package crackers.kobots.parts.movement.async

import com.diozero.api.ServoDevice
import crackers.kobots.parts.movement.LimitedRotator
import crackers.kobots.parts.movement.RotationMovement
import crackers.kobots.parts.movement.gearedAngleTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A rotation movement that includes duration and easing function.
 *
 * @param targetAngle The target angle for the rotation.
 * @param duration The duration of the rotation.
 * @param easing The easing function to be used for the rotation.
 */
open class AsyncRotationMovement(
    val targetAngle: Int,
    val duration: Duration,
    val easing: EasingFunction,
) : RotationMovement(targetAngle)

fun ServoDevice.asyncServoRotator(physicalRange: IntRange) = AsyncServoRotator(this, physicalRange)

fun ServoDevice.asyncServoRotator(
    physicalRange: IntRange,
    servoRange: IntRange,
    delta: Int = 1,
) = AsyncServoRotator(this, physicalRange, servoRange, delta)

interface AsyncRotator {
    suspend fun rotateTo(
        angle: Int,
        time: Duration = 2.seconds,
        easing: EasingFunction = linear,
    )
}

/**
 * An asynchronous servo rotator that smoothly rotates a servo to a specified angle using easing functions.
 * Do **not** use this class if you need to interrupt the rotation once started.
 *
 * @param theServo The servo device to be controlled.
 * @param physicalRange The physical range of motion for the servo in degrees.
 * @param servoRange The actual range of angles the servo can achieve.
 * @param delta The increment step for rotation. Default is 1 degree.
 */
open class AsyncServoRotator(
    private val theServo: ServoDevice,
    override val physicalRange: IntRange,
    private val servoRange: IntRange,
    private val delta: Int = 1,
) : LimitedRotator() {
    /**
     * Secondary constructor that assumes the servo's physical range matches its servo range and uses a delta of 1.
     */
    constructor(theServo: ServoDevice, servoRange: IntRange) : this(
        theServo,
        servoRange,
        servoRange,
        1,
    )

    private val mySteps = (physicalRange.last - physicalRange.first).coerceAtLeast(10) / delta
    private val realAngles: SortedMap<Int, Int> = gearedAngleTable(physicalRange, servoRange)

    fun init() {
        theServo.angle = servoRange.first.toFloat()
    }

    protected val _atomicCurrent = AtomicInteger(0)
    override val current: Int
        get() = _atomicCurrent.get()

    /**
     * Rotate to the specified angle synchronously. This function is disabled for this class of rotator.
     */
    override fun rotateTo(angle: Int): Boolean {
        throw UnsupportedOperationException("Use rotateTo(angle, time, easing) instead for AsyncServoRotator")
    }

    /**
     * Rotate to the specified angle asynchronously with given time and easing.
     *
     * @param angle Target angle to rotate to.
     * @param time Duration of the rotation.
     * @param easing Easing function to use for the rotation.
     */
    suspend fun rotateAsync(
        angle: Int,
        time: Duration = 2.seconds,
        easing: EasingFunction = linear,
    ) {
        // Ensure the angle is within the physical range
        require(angle in physicalRange) { "Angle $angle is out of physical range $physicalRange" }
        val servoAngle = realAngles.getOrElse(angle) { servoRange.first.toFloat() }
        easeTo(
            start = theServo.angle,
            end = servoAngle.toFloat(),
            duration = time,
            updateFn = { withContext(Dispatchers.IO) { theServo.angle = it } },
            easingFn = easing,
            steps = mySteps,
        )
        _atomicCurrent.set(angle)
    }
}
