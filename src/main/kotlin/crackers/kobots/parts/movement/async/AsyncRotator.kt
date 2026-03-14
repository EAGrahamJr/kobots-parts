package crackers.kobots.parts.movement.async

import com.diozero.api.ServoDevice
import com.diozero.devices.sandpit.motor.BasicStepperController
import com.diozero.devices.sandpit.motor.BasicStepperMotor
import crackers.kobots.parts.movement.BasicStepperRotator
import crackers.kobots.parts.movement.gearedAngleTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.Math.abs
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlin.time.Duration


/**
 * An asynchronous rotator interface for devices that can rotate to a specified angle.
 */
interface AsyncRotator : AsyncActuator {
    /**
     * The basic asynchronous rotation function. It is "device dependent", which means accuracy may be suspect.
     */
    suspend fun rotateAsync(
        angle: Int,
        time: Duration,
        easing: EasingFunction = linear,
    )

    override suspend fun softLanding(
        target: Int,
        time: Duration,
    ) = rotateAsync(angle = target, time, softLanding)

    override suspend fun softLaunch(
        target: Int,
        time: Duration,
    ) = rotateAsync(angle = target, time, softLaunch)

    override suspend fun smoothMove(
        target: Int,
        time: Duration,
    ) = rotateAsync(angle = target, time, smooth)
}

/**
 * An asynchronous servo rotator that smoothly rotates a servo to a specified angle using easing functions.
 *
 * @param theServo The servo device to be controlled.
 * @param physicalRange The physical range of motion for the servo in degrees.
 * @param servoRange The actual range of angles the servo can achieve.
 */
open class AsyncServoRotator(
    val theServo: ServoDevice,
    val physicalRange: IntRange,
    protected val servoRange: IntRange,
) : AsyncRotator {
    /**
     * Secondary constructor that assumes the servo's physical range matches its servo range.
     */
    constructor(theServo: ServoDevice, servoRange: IntRange) : this(theServo, servoRange, servoRange)

    protected val realAngles: SortedMap<Int, Int> = gearedAngleTable(physicalRange, servoRange)

    fun init() {
        theServo.angle = servoRange.first.toFloat()
        // make sure the servo range is within the trim range of the servo
        require(servoRange.first >= theServo.trim.minAngle && servoRange.last <= theServo.trim.maxAngle) {
            "Servo range $servoRange is out of trim range ${theServo.trim.minAngle}..${theServo.trim.maxAngle}"
        }
    }

    protected val _atomicCurrent = AtomicInteger(0)
    override val current: Int
        get() = _atomicCurrent.get()

    protected val mutex = Mutex()

    private val servoFirst = servoRange.first
    private val physicalFirst = physicalRange.first

    /**
     * Rotate to the specified angle asynchronously with given time and easing.
     *
     * @param angle Target angle to rotate to.
     * @param time Duration of the rotation.
     * @param easing Easing function to use for the rotation.
     */
    suspend override fun rotateAsync(
        angle: Int,
        time: Duration,
        easing: EasingFunction,
    ) = mutex.withLock {
        // Ensure the angle is within the physical range
        require(angle in physicalRange) { "Angle $angle is out of physical range $physicalRange" }

        val servoAngle = realAngles.getOrElse(angle) { servoFirst }
        val mySteps = abs(theServo.angle - servoAngle).toInt().coerceAtLeast(3)

        easeTo(
            start = theServo.angle,
            end = servoAngle.toFloat(),
            duration = time,
            updateFn = { nextAngle ->
                // set the "current" to the first realAngles keys entry that matches theServo.angle closest
                // this allows interrupted movements to report where they "are"
                val closest =
                    realAngles.entries.minByOrNull { entry -> abs(entry.value - nextAngle) }?.key ?: physicalFirst
                _atomicCurrent.set(closest)

                stopCheck(angle)

                // ok, move
                withContext(Dispatchers.IO) { theServo.angle = nextAngle }
            },
            easingFn = easing,
            steps = mySteps,
        )
        _atomicCurrent.set(angle)
    }

    companion object {
        /**
         * Create an AsyncServoRotator for this ServoDevice with the given physical and servo ranges. It will default
         * to the trim range of the servo if not specified.
         */
        fun ServoDevice.asyncRotator(
            servoRange: IntRange = trim.minAngle..trim.maxAngle,
            physicalRange: IntRange = servoRange,
        ) = AsyncServoRotator(this, physicalRange, servoRange)
    }
}

/**
 * An asynchronous stepper rotator that smoothly rotates a stepper motor to a specified angle using easing functions.
 *
 * This is built on top of [BasicStepperRotator] to provide asynchronous movement capabilities. The [stepPause]
 * parameter is provided to introduce a delay between steps to prevent overdriving the stepper motor or driver
 * circuits (e.g. "slow" response from the hardware).
 *
 * **NOTE** The stepper is _not_ automatically released after movement; it is assumed that holding torque is desired.
 *
 * @param theStepper The stepper motor to be controlled.
 * @param gearRatio The gear ratio of the stepper motor.
 * @param reversed Whether the stepper motor direction is reversed.
 * @param stepStyle The stepping style to use for the stepper motor.
 * @param stepPause The pause duration between steps (default is 0).
 */
open class AsyncStepperRotator(
    val theStepper: BasicStepperMotor,
    gearRatio: Float = 1f,
    reversed: Boolean = false,
    stepStyle: BasicStepperController.StepStyle = BasicStepperController.StepStyle.SINGLE,
    private val stepPause: Duration = Duration.ZERO,
) : AsyncRotator {

    val wrapped = BasicStepperRotator(
        theStepper,
        gearRatio,
        reversed,
        stepStyle,
        theStepper.stepsPerRotation.toInt(),
    )

    override val current: Int
        get() = wrapped.current

    protected val mutex = Mutex()

    override suspend fun rotateAsync(
        angle: Int,
        time: Duration,
        easing: EasingFunction
    ) = mutex.withLock {

        easeTo(
            start = current.toFloat(),
            end = angle.toFloat(),
            duration = time,
            updateFn = { nextAngle ->
                stopCheck(angle)
                withContext(Dispatchers.IO) {
                    while (!wrapped.rotateTo(nextAngle.roundToInt())) {
                        delay(stepPause)
                    }
                }
            },
            easingFn = easing,
            steps = abs(angle - current).coerceAtLeast(3),
        )
    }

    /**
     * Release the stepper motor, allowing it to be turned freely.
     */
    open fun release() {
        wrapped.release()
    }
}
