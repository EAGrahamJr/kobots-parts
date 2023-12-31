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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A linear actuator that can be used to extend and retract a part of a machine. Movement is defined as a percentage
 * of the total range of motion. Note that some actuators may not be able to move partially, so the actual movement
 * may be rounded to fully extend or retract (e.g. a solenoid).
 */
interface LinearActuator : Actuator<LinearMovement> {
    override infix fun move(movement: LinearMovement): Boolean {
        return extendTo(max(0, min(100, movement.percentage)))
    }

    /**
     * Extend or retract the actuator to the given percentage of the total range of motion. Returns `true` if the
     * target has been reached.
     */
    infix fun extendTo(percentage: Int): Boolean

    /**
     * Returns the current position as a percentage of the total range of motion.
     */
    fun current(): Int

    /**
     * Extend the actuator by one step.
     *
     * Example: `+actuator`
     */
    operator fun unaryPlus() {
        extendTo(current() + 1)
    }

    /**
     * Extend the actuator by the given delta. The target may or may not move by the indicated amount if the bounds are
     * reached, depending on the implementation and current location.
     *
     * Example: `actuator += 5`
     */
    operator fun plusAssign(delta: Int) {
        extendTo(current() + delta)
    }

    /**
     * Retract the actuator by one step.
     *
     * Example: `-actuator`
     */
    operator fun unaryMinus() {
        extendTo(current() - 1)
    }

    /**
     * Retract the actuator by the given delta. The target may or may not move by the indicated amount if the bounds are
     * reached, depending on the implementation and current location.
     *
     * Example: `actuator -= 5`
     */
    operator fun minusAssign(delta: Int) {
        extendTo(current() - delta)
    }

    /**
     * Operator short-cut for [extendTo].
     *
     * Example: `actuator % 50`
     */
    operator fun rem(delta: Int): Boolean = extendTo(delta)
}

/**
 * A linear actuator that uses a servo to extend and retract. Due to rounding errors and whatever gearing is applied,
 * the "step" size is basically however far the servo moves the actuator in one degree of rotation.
 *
 * TODO add a delta to manage the rate of change
 */
class ServoLinearActuator(
    val theServo: ServoDevice,
    val homeDegrees: Float,
    val maximumDegrees: Float
) : LinearActuator {
    private val servoSwingDegrees = abs(maximumDegrees - homeDegrees)
    private val whichWay = if (maximumDegrees > homeDegrees) 1f else -1f

    override fun extendTo(percentage: Int): Boolean {
        if (percentage < 0 || percentage > 100) return true

        val delta = percentage - current()
        if (delta != 0) {
            val currentAngle = theServo.angle

            if (delta > 0) {
                theServo.angle = currentAngle + whichWay
            } else {
                theServo.angle = currentAngle - whichWay
            }
        }
        return abs(percentage - current()) <= 1
    }

    override fun current(): Int {
        val degrees = theServo.angle
        return (abs(degrees - homeDegrees) * 100 / servoSwingDegrees).roundToInt()
    }
}
