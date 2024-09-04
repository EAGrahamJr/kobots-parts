/*
 * Copyright 2022-2024 by E. A. Graham, Jr.
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

/**
 * Basic interface for a generic movement. A [stopCheck] function may be supplied to terminate movement, which should
 * be checked _prior_ to any physical action.
 */
interface Movement {
    /**
     * Returns `true` if the movement should be stopped.
     */
    val stopCheck: () -> Boolean
}

/**
 * A thing to handle a [Movement].
 */
interface Actuator<M : Movement> {
    /**
     * Perform the [Movement] and return `true` if the movement was successful/completed.
     */
    infix fun move(movement: M): Boolean
}

/**
 * Additional actions for an actuator that uses steppers.
 */
interface StepperActuator {
    /**
     * Release the stepper: this is to prevent over-heating
     */
    fun release()

    /**
     * Allows for "re-calibration" of the stepper's position.
     */
    fun reset()
}

/**
 * Describes where to go as a rotational angle. A [stopCheck] function may also be supplied to terminate movement
 * **prior** to reaching the desired [angle]. An `Actuator` may or may not be limited in its range of motion, so the
 * [angle] should be tailored to fit.
 */
open class RotationMovement(
    val angle: Int,
    override val stopCheck: () -> Boolean = { false }
) : Movement

/**
 * Describes where to go as a [percentage] of "in/out", where "in" is 0 and 100 is "out". A [stopCheck] function may
 * also be supplied to terminate movement **prior** to reaching the desired setting.
 */
open class LinearMovement(
    val percentage: Int,
    override val stopCheck: () -> Boolean = { false }
) : Movement {

    init {
        require(percentage in 0..100) { "percentage must be between 0 and 100" }
    }
}
