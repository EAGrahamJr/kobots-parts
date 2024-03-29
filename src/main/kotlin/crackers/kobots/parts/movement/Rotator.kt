/*
 * Copyright 2022-2023 by E. A. Graham, Jr.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package crackers.kobots.parts.movement

import com.diozero.api.ServoDevice
import com.diozero.devices.sandpit.motor.BasicStepperController.StepStyle
import com.diozero.devices.sandpit.motor.BasicStepperMotor
import com.diozero.devices.sandpit.motor.StepperMotorInterface.Direction.BACKWARD
import com.diozero.devices.sandpit.motor.StepperMotorInterface.Direction.FORWARD
import crackers.kobots.devices.at
import crackers.kobots.parts.movement.LimitedRotator.Companion.MAX_ANGLE
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Thing that turns. The basic notion is to allow for a "servo" or a "stepper" motor to be used incrementally or
 * absolutely, somewhat interchangeably. Rotation is roughly based on angular _motor_ movement, not necessarily
 * corresponding to real world coordinates.
 */
interface Rotator : Actuator<RotationMovement> {
    /**
     * Take a "step" towards this destination. Returns `true` if the target has been reached.
     */
    override infix fun move(movement: RotationMovement): Boolean {
        return rotateTo(movement.angle)
    }

    /**
     * Take a "step" towards this destination. Returns `true` if the target has been reached.
     */
    infix fun rotateTo(angle: Int): Boolean

    /**
     * TODO this does not work because it doesn't set a "real" reachable target
     *
     * Rotate by the given delta in a positive direction. The target may or may not move by the indicated amount,
     * depending on the implementation and current location.
     *
     * Example: rotator += 5
     */
//    operator fun plusAssign(delta: Int) {
//        rotateTo(current() + delta)
//    }

    /**
     * TODO this does not work because it doesn't set a "real" reachable target
     *
     * Rotate by the given delta in a negative direction. The target may or may not move by the indicated amount,
     * depending on the implementation and current location.
     *
     * Example: rotator -= 5
     */
//    operator fun minusAssign(delta: Int) {
//        rotateTo(current() - delta)
//    }

    /**
     * Move one degree negatively, if possible.
     *
     * Example: -rotator
     */
    operator fun unaryMinus() {
        rotateTo(current() - 1)
    }

    /**
     * Move one degree positively, if possible.
     *
     * Example: +rotator
     */
    operator fun unaryPlus() {
        rotateTo(current() + 1)
    }

    /**
     * Rotate to the given angle. Returns `true` if the target has been reached.
     */
    operator fun div(angle: Int): Boolean = rotateTo(angle)

    /**
     * Current location.
     */
    fun current(): Int

    /**
     * Determine if this float is  "almost" equal to [another], within the given [wibble].
     */
    fun Float.almostEquals(another: Float, wibble: Float): Boolean = abs(this - another) < wibble
}

/**
 * Stepper motor with an optional gear ratio. If [reversed] is `true`, the motor is mounted "backwards" relative to
 * desired rotation. Each movement is executed as a single step of the motor.
 *
 * **NOTE** The accuracy of the movement is dependent on rounding errors in the calculation of the number of steps
 * required to reach the destination. The _timing_ of each step may also affect if the motor receives the pulse or not.
 *
 * [theStepper] _should_ be "released" after use to avoid motor burnout and to allow for "re-calibration" if necessary.
 */
open class BasicStepperRotator(
    private val theStepper: BasicStepperMotor,
    gearRatio: Float = 1f,
    reversed: Boolean = false,
    val stepStyle: StepStyle = StepStyle.SINGLE
) : Rotator {

    private val maxSteps: Float

    private val degreesToSteps: Map<Int, Int>
    init {
        require(gearRatio > 0f) { "gearRatio '$gearRatio' must be greater than zero." }
        maxSteps = theStepper.stepsPerRotation / gearRatio

        // calculate how many steps off of "zero" each degree is
        degreesToSteps = (0..359).map {
            it to (maxSteps * it / 360).roundToInt()
        }.toMap()
    }

    private val forwardDirection = if (reversed) BACKWARD else FORWARD
    private val backwardDirection = if (reversed) FORWARD else BACKWARD

    open fun release() = theStepper.release()

    private var stepsLocation: Int = 0
    private var angleLocation: Int = 0

    override fun current(): Int = (360 * stepsLocation / maxSteps).roundToInt()

    override fun rotateTo(angle: Int): Boolean {
        // first check to see if the angles already match
        if (angleLocation == angle) return true

        // find out where we're supposed to be for steps
        val destinationSteps = degreesToSteps[abs(angle % 360)]!! * (if (angle < 0) -1 else 1)
        // and if steps match, angles match and everything is good
        if (destinationSteps == stepsLocation) {
            angleLocation = angle
            return true
        }

        // move towards the destination
        if (destinationSteps < stepsLocation) {
            stepsLocation--
            theStepper.step(backwardDirection, stepStyle)
        } else {
            stepsLocation++
            theStepper.step(forwardDirection, stepStyle)
        }
        // are we there yet?
        return (destinationSteps == stepsLocation).also {
            if (it) angleLocation = angle
        }
    }

    /**
     * TODO is this really necessary?
     */
    fun reset() {
        stepsLocation = 0
    }
}

/**
 * A [Rotator] that is constrained to a physical angular range, in degrees. This is useful for servos that are not
 * "continuous rotation" types.
 *
 * @param physicalRange the physical range of the rotator, in degrees
 */
interface LimitedRotator : Rotator {
    val physicalRange: IntRange

    /**
     * Operator to rotate to a percentage of the physical range. For example, if the physical range is `0..180`, then
     * `rotator % 50` would rotate to 90 degrees.
     */
    operator fun rem(percentage: Int): Boolean {
        val range = physicalRange.last - physicalRange.first
        return rotateTo(physicalRange.first + (range * percentage / 100f).roundToInt())
    }

    companion object {
        /**
         * Create a [LimitedRotator] based on a [SimpleServoRotator]
         */
        fun ServoDevice.create(
            physicalRange: IntRange,
            deltaDegrees: Int? = 1
        ): LimitedRotator = SimpleServoRotator(this, physicalRange, deltaDegrees)

        /**
         * Create a [LimitedRotator] based on a [ServoRotator]
         */
        fun ServoDevice.create(
            physicalRange: IntRange,
            servoRange: IntRange,
            deltaDegrees: Int? = 1
        ): LimitedRotator = ServoRotator(this, physicalRange, servoRange, deltaDegrees)

        const val MAX_ANGLE = Int.MAX_VALUE
        const val MIN_ANGLE = -MAX_ANGLE
    }
}

/**
 * Servo, with software limits to prevent over-rotation. Each "step" is controlled by the `deltaDegrees` parameter
 * (`null` means absolute movement, default 1 degree per "step"). Servo movement is done in "whole degrees" (int), so
 * there may be some small rounding errors.
 *
 * The servo motion is modeled as a "simple" servo, with no gear ratio or other mechanical limitations.
 */
open class SimpleServoRotator(
    private val theServo: ServoDevice,
    private val servoRange: IntRange,
    private val deltaDegrees: Int? = 1
) : LimitedRotator {
    override val physicalRange: IntRange
        get() = servoRange

    override fun rotateTo(angle: Int): Boolean {
        // special case -- if the target is MAXINT, use the physical range as the target
        // NOTE -- this may cause the servo to jitter or not move at all
        if (abs(angle) == MAX_ANGLE) {
            return rotateTo(if (angle < 0) physicalRange.first else physicalRange.last)
        }

        require(angle in physicalRange) { "Angle '$angle' is not in physical range '$physicalRange'." }
        val diff = abs(angle - theServo.angle)
        when {
            diff < .1 -> return true
            angle < theServo.angle -> if (deltaDegrees == null) theServo at angle else theServo at theServo.angle - deltaDegrees
            angle > theServo.angle -> if (deltaDegrees == null) theServo at angle else theServo at theServo.angle + deltaDegrees
            else -> return true
        }
        return false
    }

    override fun current(): Int = theServo.angle.roundToInt()
}

/**
 * Servo, with software limits to prevent over-rotation. Each "step" is controlled by the `deltaDegrees` parameter
 * (`null` means absolute movement, default 1 degree per "step"). Servo movement is done in "whole degrees" (int), so
 * there may be some small rounding errors.
 *
 * The [physicalRange] is the range of the servo _target_ in degrees, and the [servoRange] is the range of the servo
 * itself, in degrees. For example, a servo that rotates its target 180 degrees, but only requires a 90 degree range,
 * would have a [physicalRange] of `0..180` and a [servoRange] of `45..135`. This allows for gear ratios and other
 * mechanical limitations to be accounted for. **NOTE** that the [physicalRange] can be _inverted_ to the [servoRange]
 * if the servo is mounted "backwards" relative to the desired rotation.
 *
 * Note that the target **may** not be exact, due to rounding errors and if the [delta] is large. Example:
 * ```
 * delta = 5
 * current = 47
 * target = 50
 * ```
 * This would indicate that the servo _might not_ move, as the target is within the delta given.
 *
 */
open class ServoRotator(
    private val theServo: ServoDevice,
    override val physicalRange: IntRange,
    private val servoRange: IntRange,
    deltaDegrees: Int? = 1
) : LimitedRotator {
    private val delta: Float? = if (deltaDegrees == null) null else abs(deltaDegrees).toFloat()

    private val PRECISION = 0.1f

    private val servoLowerLimit: Float
    private val servoUpperLimit: Float
    private val gearRatio: Float

    init {
        require(physicalRange.first < physicalRange.last) { "physicalRange '$physicalRange' must be increasing" }
        with(servoRange) {
            servoLowerLimit = (if (first < last) first else last).toFloat()
            servoUpperLimit = (if (first < last) last else first).toFloat()
        }
        gearRatio = (physicalRange.last - physicalRange.first).toFloat() /
            (servoRange.last - servoRange.first).toFloat()
    }

    // translate an angle in the physical range to a servo angle
    private fun translate(angle: Int): Float {
        val physicalOffset = angle - physicalRange.first
        val servoOffset = physicalOffset / gearRatio
        return servoOffset + servoRange.first
    }

    // report the current angle, translated to the physical range
    override fun current(): Int = theServo.angle.let {
        val servoOffset = it - servoRange.first
        val physicalOffset = servoOffset * gearRatio
        physicalOffset + physicalRange.first
    }.roundToInt()

    /**
     * Figure out if we need to move or not (and how much)
     */
    override fun rotateTo(angle: Int): Boolean {
        // special case -- if the target is MAXINT, use the physical range as the target
        // NOTE -- this may cause the servo to jitter or not move at all
        if (abs(angle) == Int.MAX_VALUE) {
            return rotateTo(if (angle < 0) physicalRange.first else physicalRange.last)
        }

        // angle must be in the physical range
        require(angle in physicalRange) { "Angle '$angle' is not in physical range '$physicalRange'." }

        val currentAngle = theServo.angle
        val targetAngle = translate(angle)

        // this is an absolute move without any steps, so set it up and fire it
        if (delta == null) {
            if (reachedTarget(currentAngle, targetAngle, PRECISION)) return true

            // move to the target (trimmed) and we're done
            theServo at trimTargetAngle(targetAngle)
            return true
        }

        // check to see if within the target
        if (targetAngle.almostEquals(currentAngle, delta)) return true

        // apply the delta and make sure it doesn't go out of range (technically it shouldn't)
        val nextAngle = trimTargetAngle(
            if (currentAngle > targetAngle) currentAngle - delta else currentAngle + delta
        )

        // move it and re-check
        theServo at nextAngle
        return reachedTarget(theServo.angle, targetAngle, delta)
    }

    // determine if the servo is at the target angle or at either of the limits
    private fun reachedTarget(servoCurrent: Float, servoTarget: Float, delta: Float): Boolean {
        return servoCurrent.almostEquals(servoTarget, delta) ||
            servoCurrent.almostEquals(servoLowerLimit, PRECISION) ||
            servoCurrent.almostEquals(servoUpperLimit, PRECISION)
    }

    // limit the target angle to the servo limits
    private fun trimTargetAngle(targetAngle: Float): Float {
        return when {
            targetAngle < servoLowerLimit -> servoLowerLimit
            targetAngle > servoUpperLimit -> servoUpperLimit
            else -> targetAngle
        }
    }
}
