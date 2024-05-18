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
import java.util.*
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
     * Rotate to the given angle. Returns `true` if the target has been reached.
     */
    operator fun div(angle: Int): Boolean = rotateTo(angle)

    /**
     * Current location.
     */
    fun current(): Int
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
) : Rotator, StepperActuator {

    private val maxSteps: Float

    private val degreesToSteps: Map<Int, Int>
    private val stepsToDegrees = mutableMapOf<Int, MutableList<Int>>()

    init {
        require(gearRatio > 0f) { "gearRatio '$gearRatio' must be greater than zero." }
        maxSteps = theStepper.stepsPerRotation / gearRatio

        // calculate how many steps off of "zero" each degree is
        degreesToSteps = (0..359).map { deg: Int ->
            val steps = (maxSteps * deg / 360).roundToInt()
            stepsToDegrees.compute(steps) { _, v ->
                val theList = v ?: mutableListOf()
                theList += deg
                theList
            }
            deg to steps
        }.toMap()
    }

    private val forwardDirection = if (reversed) BACKWARD else FORWARD
    private val backwardDirection = if (reversed) FORWARD else BACKWARD

    /**
     * Pass through to release the stepper when it's not directly available.
     */
    override fun release() = theStepper.release()

    protected var stepsLocation: Int = 0
    protected var angleLocation: Int = 0

    override fun current(): Int = angleLocation

    override fun rotateTo(angle: Int): Boolean {
        // checks things
        if (limitCheck(angle)) return true

        // first check to see if the angles already match
        if (angleLocation == angle) return true

        // find out where we're supposed to be for steps
        val realAngle = abs(angle % 360)
        val destinationSteps = degreesToSteps[realAngle]!!
        // and if steps match, angles match and everything is good
        if (destinationSteps == stepsLocation) {
            angleLocation = angle
            return true
        }

        // move towards the destination
        if (destinationSteps < stepsLocation) {
            stepsLocation--
            theStepper.step(backwardDirection, stepStyle)
            stepsToDegrees[stepsLocation]?.also { anglesForStep ->
                angleLocation = if (stepsLocation !in anglesForStep) anglesForStep.max() else anglesForStep.min()
            }
        } else {
            stepsLocation++
            theStepper.step(forwardDirection, stepStyle)
            stepsToDegrees[stepsLocation]?.also { anglesForStep ->
                angleLocation = if (stepsLocation !in anglesForStep) anglesForStep.min() else anglesForStep.max()
            }
        }
        // are we there yet?
        return (destinationSteps == stepsLocation).also {
            if (it) angleLocation = angle
        }
    }

    /**
     * Allows for "re-calibration" of the stepper to the "0" position.
     */
    override fun reset() {
        stepsLocation = 0
        angleLocation = 0
    }
}

/**
 * A [Rotator] that is constrained to a physical angular range, in degrees. This is useful for servos that are not
 * "continuous rotation" types.
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
         * Create a [LimitedRotator] with a 1:1 ratio
         */
        fun ServoDevice.rotator(
            physicalRange: IntRange
        ): LimitedRotator = ServoRotator(this, physicalRange)

        /**
         * Create a [LimitedRotator] based on a [ServoRotator]
         */
        fun ServoDevice.rotator(
            physicalRange: IntRange,
            servoRange: IntRange,
            deltaDegrees: Int = 1
        ): LimitedRotator = ServoRotator(this, physicalRange, servoRange, deltaDegrees)

        const val MAX_ANGLE = Int.MAX_VALUE
        const val MIN_ANGLE = -MAX_ANGLE
    }
}

/**
 * Servo, with software limits to prevent over-rotation.
 *
 * The [physicalRange] is the range of the servo _target_ in degrees, and the [servoRange] is the range of the servo
 * itself, in degrees. For example, a servo that rotates its target 180 degrees, but only requires a 90-degree
 * rotation of the servo, would have a [physicalRange] of `0..180` and a [servoRange] of `45..135`. This allows for gear
 * ratios and other mechanical linkages. Note that if the [servoRange] is _inverted_, the servo will rotate backwards
 * relative to a positive angle.
 *
 * Each "step" is controlled by the [delta] parameter (degrees to move the servo, default `1`). Since this movement is
 * done in "whole degrees" (int), there will be rounding errors, especially if the gear ratios are not 1:1. Note that
 * using a `[delta] != 1` **may** cause the servo to "seek" when the ranges do not align well and _that_ may
 * also cause the servo to never actually reach the target.
 */
open class ServoRotator(
    private val theServo: ServoDevice,
    final override val physicalRange: IntRange,
    private val servoRange: IntRange,
    private val delta: Int = 1
) : LimitedRotator {

    /**
     * Alternate constructor where the "gear ratio" is 1:1 (servo move == physical move). The default movement is a
     * single degree at a time.
     */
    constructor(theServo: ServoDevice, servoRange: IntRange) : this(
        theServo,
        servoRange,
        servoRange,
        1
    )

    // map physical degrees to where the servo should be
    private val degreesToServo: SortedMap<Int, Int>

    init {
        require(physicalRange.first < physicalRange.last) { "physicalRange '$physicalRange' must be increasing" }

        val physicalScope = physicalRange.last - physicalRange.first
        val servoScope = servoRange.last - servoRange.first

        degreesToServo = physicalRange.associateWith { angle ->
            val normalizedPosition = (angle - physicalRange.first).toDouble() / physicalScope

            val servo = (servoRange.first + normalizedPosition * servoScope).roundToInt()
            servo
        }.toSortedMap()
    }

    private val availableDegrees = degreesToServo.keys.toList()

    internal var where = physicalRange.first
    override fun current(): Int = where

    /**
     * Figure out if we need to move or not (and how much)
     */
    override fun rotateTo(angle: Int): Boolean {
        val now = current()
        if (angle == now) return true

        // special case -- if the target is MAXINT, use the physical range as the target
        // NOTE -- this may cause the servo to jitter or not move at all
        if (abs(angle) == MAX_ANGLE) {
            return rotateTo(if (angle < 0) physicalRange.first else physicalRange.last)
        }

        // angle must be in the physical range
        require(angle in physicalRange) { "Angle '$angle' is not in physical range '$physicalRange'." }

        // find the "next" angle from now based on where the target is
        val nowKeyIndex = availableDegrees.indexOf(now)
        val nextAngleIndex = (if (angle < now) nowKeyIndex - 1 else nowKeyIndex + 1)
            .coerceIn(0, availableDegrees.size - 1)

        val nextAngle = availableDegrees[nextAngleIndex]
        // do not move beyond the requested angle
        if (nextAngle < angle && angle < now) {
            return true
        } else if (nextAngle > angle && angle > now) return true

        // so where the hell are we going?
        val nextServoAngle = degreesToServo[nextAngle]!!
        val currentServoAngle = theServo.angle.roundToInt()

        val nextServoTarget: Int
        if (nextServoAngle < currentServoAngle) {
            val t = currentServoAngle - delta
            if (t < nextServoAngle) return true // can't move
            nextServoTarget = t
        } else if (nextServoAngle > currentServoAngle) {
            val t = currentServoAngle + delta
            if (t > nextServoAngle) return true // can't move
            nextServoTarget = t
        } else {
            nextServoTarget = currentServoAngle
        }

        theServo at nextServoTarget

        // if we moved to a mapped target, update the position
        if (nextServoTarget == nextServoAngle) where = nextAngle

        return false
    }
}
