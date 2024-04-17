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

import crackers.kobots.parts.app.KobotSleep
import java.time.Duration

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
 * An [Action] is a sequence of [Movement]s to perform. Each [Movement] is associated with a [Actuator] and is to be
 * performed in sequence. The [Action] is considered complete when all [Movement]s have been performed.
 *
 * `Actions` are not re-runnable.
 */
class Action(movements: Map<Actuator<Movement>, Movement>) {

    // make a copy of the [movements] list to avoid any external modification
    private val executionList = movements.toList()

    // don't run movements if they're done
    private val movementResults = BooleanArray(executionList.size) { false }

    /**
     * Executes each movement in the [Action] and returns `true` if all movements were successful. If `false`, this
     * indicates that the action is not complete and should execute [step] again.
     *
     * If a [stepExecutionTime] is provided, then each movement will pause until the next is executed (the value is
     * roughly distributed between steps). This is useful for ensuring that the [Action] is not executed too quickly.
     *
     * This is a **blocking** call: the assumption is that it is only used by a single, controlling thread.
     * There is currently no means to interrupt a single step, aside from each movement's `stopCheck`.
     */
    fun step(stepExecutionTime: Duration = Duration.ZERO): Boolean {
        // get nano sleeps
        val nanoSleeps = Duration.ofNanos(stepExecutionTime.toNanos() / executionList.size)

        return movementResults
            .mapIndexed { index, previous ->
                executeAndPause(nanoSleeps) { previous || executeMovement(index) }
            }
            .all { it }
    }

    private fun <R> executeAndPause(maxPause: Duration, block: () -> R): R {
        val pauseForNanos = maxPause.toNanos()
        val startAt = System.nanoTime()

        return try {
            block()
        } finally {
            // if the execution takes too little time, snooze for the remaining allocation
            val runtime = System.nanoTime() - startAt
            if (runtime < pauseForNanos) KobotSleep.nanos(pauseForNanos - runtime)
        }
    }

    private fun executeMovement(index: Int): Boolean {
        val (actuator, movement) = executionList[index]

        // result == stop check or movement done
        return (movement.stopCheck() || actuator move movement)
            // save the result
            .also { movementResults[index] = it }
    }
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

/**
 * Simple "open/close" movement. A [stopCheck] function may also be supplied to terminate movement **prior**
 * to attempting movement.
 */
open class OpenCloseMovement(
    val open: Boolean,
    override val stopCheck: () -> Boolean = { false }
) : Movement
