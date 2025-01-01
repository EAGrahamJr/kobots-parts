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

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * How long each step should take to execute **at a minimum**.
 */
interface ActionSpeed {
    val millis: Long
    val duration: Duration
        get() = millis.toDuration(DurationUnit.MILLISECONDS)

    @Deprecated(message = "Use 'val' instead", replaceWith = ReplaceWith("duration"))
    fun duration(): Duration = millis.toDuration(DurationUnit.MILLISECONDS)
}

/**
 * Makes an [ActionSpeed] in millis from one.
 */
fun Long.toSpeed(): ActionSpeed {
    val v = this
    return object : ActionSpeed {
        override val millis = v
    }
}

/**
 * "Default" speeds based on observation.
 */
enum class DefaultActionSpeed(override val millis: Long) : ActionSpeed {
    VERY_SLOW(100), SLOW(50), NORMAL(10), FAST(5), VERY_FAST(2)
}

/**
 * Just runs the stop-check. Library only.
 */
internal class ExecutionMovement(override val stopCheck: () -> Boolean) : Movement

/**
 * Builds up movements for an [Action].
 */
class ActionBuilder {
    private val steps = mutableMapOf<Actuator<*>, Movement>()

    /**
     * How "fast" this action runs: e.g. each "step" takes this amount of time _at a minimum_.
     */
    var requestedSpeed: ActionSpeed = DefaultActionSpeed.NORMAL

    /**
     * DSL to define a [RotationMovement] in an action.
     */
    class Rotation {
        var angle: Int = 0
        var stopCheck = { false }
    }

    /**
     * DSL to rotate a [Rotator] to an angle with a stop-check
     */
    infix fun Rotator.rotate(r: Rotation.() -> Unit) {
        val r2 = Rotation().apply(r)
        steps[this] = RotationMovement(r2.angle, r2.stopCheck)
    }

    /**
     * DSL to rotate a [Rotator] to a specific angle.
     */
    infix fun Rotator.rotate(angle: Int) {
        steps[this] = RotationMovement(angle)
    }

    /**
     * DSL to rotate a [Rotator] in a "positive" direction with the given check used as a stop-check.
     */
    infix fun Rotator.forwardUntil(forwardCheck: () -> Boolean) {
        steps[this] = RotationMovement(Int.MAX_VALUE, forwardCheck)
    }

    /**
     * DSL to rotate a [Rotator] in a "negative" direction until a stop check is true.
     */
    infix fun Rotator.backwardUntil(backwardCheck: () -> Boolean) {
        steps[this] = RotationMovement(-Int.MAX_VALUE, backwardCheck)
    }

    /**
     * DSL to define a [LinearMovement] in an action.
     */
    class Linear {
        var percentage = 0
        var stopCheck = { false }
    }

    /**
     * DSL to move a [LinearActuator] to a specific position with a stop check.
     */
    infix fun LinearActuator.extend(m: Linear.() -> Unit) {
        val m2 = Linear().apply(m)
        steps[this] = LinearMovement(m2.percentage, m2.stopCheck)
    }

    /**
     * DSL to move a [LinearActuator] to a specific position without a stop check.
     */
    infix fun LinearActuator.goTo(position: Int) {
        steps[this] = LinearMovement(position)
    }

    /**
     * DSL to execute a code block as a movement. The [function] must return `true` if the code was "completed" (e.g.
     * this is a "naked" stop check).
     */
    fun execute(function: () -> Boolean) {
        steps[NO_OP] = ExecutionMovement(function)
    }

    /**
     * Create a new [ActionBuilder] with the movements from this one plus the other.
     */
    operator fun plus(otherBuilder: ActionBuilder) = ActionBuilder().also {
        it.steps += this.steps
        it.steps += otherBuilder.steps
    }

    /**
     * Add another builder's movements to this.
     */
    operator fun plusAssign(otherBuilder: ActionBuilder) {
        steps += otherBuilder.steps
    }

    /**
     * Build the action's steps.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun build() = Pair(Action(steps as Map<Actuator<Movement>, Movement>), requestedSpeed.duration)

    companion object {
        /**
         * No move actuator. Library only.
         */
        private val NO_OP = object : Actuator<ExecutionMovement> {
            // everything is done in the stop-check
            override fun move(movement: ExecutionMovement) = false
            override fun current() = 0
            override val current = 0
        }
    }
}

/**
 * Defines a sequence of actions to be performed.
 */
class ActionSequence {
    private val steps = mutableListOf<ActionBuilder>()

    var name: String = "default"

    /**
     * Add an action to the sequence.
     */
    fun action(init: ActionBuilder.() -> Unit): ActionBuilder = ActionBuilder().apply(init).also {
        steps += it
    }

    infix fun append(actionBuilder: ActionBuilder) {
        steps += actionBuilder
    }

    /**
     * Create a new sequence with the steps from here with the other appended.
     */
    operator fun plus(otherSequence: ActionSequence) = ActionSequence().also {
        it.steps += this.steps
        it.steps += otherSequence.steps
    }

    /**
     * Append (add) another action builder to the list of steps. Does not return anything but does modify the
     * internal list. (Yes, this is contrary to most operators, but it's nicer for DSL.)
     */
    @Deprecated(message = "Invalid semantics applied", replaceWith = ReplaceWith("plusAssign(actionBuilder)"))
    operator fun plus(actionBuilder: ActionBuilder) {
        this.steps += actionBuilder
    }

    /**
     * Append (add) another action builder to the list of steps.
     */
    operator fun plusAssign(actionBuilder: ActionBuilder) {
        this.steps += actionBuilder
    }

    /**
     * Add the other sequence's steps to this one.
     */
    operator fun plusAssign(otherSequence: ActionSequence) {
        steps += otherSequence.steps
    }

    /**
     * Build the sequence of actions.
     */
    internal fun build(): List<ActionBuilder> = steps
}

/**
 * Typesafe "builder" (DSL) for creating a sequence of actions.
 */
fun sequence(init: ActionSequence.() -> Unit): ActionSequence = ActionSequence().apply(init)

/**
 * Typesafe "builder" (DSL) for just the actions.
 */
fun action(init: ActionBuilder.() -> Unit): ActionBuilder = ActionBuilder().apply(init)
