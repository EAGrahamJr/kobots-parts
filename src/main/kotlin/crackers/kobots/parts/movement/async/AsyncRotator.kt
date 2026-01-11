package crackers.kobots.parts.movement.async

import com.diozero.api.ServoDevice
import crackers.kobots.app.AppCommon
import crackers.kobots.parts.movement.gearedAngleTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Math.abs
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


interface AsyncRotator {
    /**
     * Set a kill switch function that can interrupt the rotation. This will cause an exception to be thrown if the
     * function returns true during rotation.
     */
    var myLittleKillSwitch: suspend () -> Boolean

    val current: Int

    /**
     * The basic asynchronous rotation function. It is "device dependent", which means accuracy may be suspect.
     */
    suspend fun rotateAsync(
        angle: Int,
        time: Duration = 2.seconds,
        easing: EasingFunction = linear,
    )

    suspend fun softLanding(
        angle: Int,
        time: Duration = 2.seconds,
    ) = rotateAsync(angle, time, softLanding)

    suspend fun softTakeoff(
        angle: Int,
        time: Duration = 2.seconds,
    ) = rotateAsync(angle, time, softLaunch)

    suspend fun smoothMove(
        angle: Int,
        time: Duration = 2.seconds,
    ) = rotateAsync(angle, time, smooth)

    companion object {
        /**
         * Create an AsyncServoRotator for this ServoDevice with the given physical range.
         */
        fun ServoDevice.asyncServoRotator(physicalRange: IntRange = this.trim.minAngle..this.trim.minAngle) =
            AsyncServoRotator(this, physicalRange)

        /**
         * Create an AsyncServoRotator for this ServoDevice with the given physical and servo ranges.
         */
        fun ServoDevice.asyncServoRotator(
            physicalRange: IntRange,
            servoRange: IntRange,
        ) = AsyncServoRotator(this, physicalRange, servoRange)
    }
}

/**
 * An asynchronous servo rotator that smoothly rotates a servo to a specified angle using easing functions.
 * Do **not** use this class if you need to interrupt the rotation once started.
 *
 * @param theServo The servo device to be controlled.
 * @param physicalRange The physical range of motion for the servo in degrees.
 * @param servoRange The actual range of angles the servo can achieve.
 */
open class AsyncServoRotator(
    protected val theServo: ServoDevice,
    val physicalRange: IntRange,
    protected val servoRange: IntRange,
) : AsyncRotator {
    /**
     * Secondary constructor that assumes the servo's physical range matches its servo range and uses a delta of 1.
     */
    constructor(theServo: ServoDevice, servoRange: IntRange) : this(theServo, servoRange, servoRange)

    protected val realAngles: SortedMap<Int, Int> = gearedAngleTable(physicalRange, servoRange)
    override var myLittleKillSwitch: suspend () -> Boolean = { false }

    fun init() {
        theServo.angle = servoRange.first.toFloat()
    }

    protected val _atomicCurrent = AtomicInteger(0)
    override val current: Int
        get() = _atomicCurrent.get()

    protected val busy = AtomicBoolean(false)

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
    ) {
        // Ensure the angle is within the physical range
        require(angle in physicalRange) { "Angle $angle is out of physical range $physicalRange" }
        if (busy.getAndSet(true))
            return

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

                // check for interrupts
                if (myLittleKillSwitch())
                    throw InterruptedException("Rotation to $angle° interrupted by kill switch")
                if (!AppCommon.applicationRunning)
                    throw InterruptedException("Rotation to $angle° interrupted by application shutdown")

                // ok, move
                withContext(Dispatchers.IO) { theServo.angle = nextAngle }
            },
            easingFn = easing,
            steps = mySteps,
        )
        _atomicCurrent.set(angle)
        busy.set(false)
    }
}
