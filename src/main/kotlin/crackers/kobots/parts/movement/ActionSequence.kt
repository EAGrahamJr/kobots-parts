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
 * Base parts for all movements.
 */
abstract class MovementBuilder<out M : Movement, out A : Actuator<out M>>(private val actuator: A) {
    var stopCheck: () -> Boolean = { false }

    @Suppress("UNCHECKED_CAST")
    fun build(): Pair<Actuator<Movement>, Movement> = Pair(actuator as Actuator<Movement>, makeMovement())

    protected abstract fun makeMovement(): M
}

class RotationMovementBuilder(rotator: Rotator) : MovementBuilder<RotationMovement, Rotator>(rotator) {
    var angle: Int = 0

    override fun makeMovement(): RotationMovement {
        return RotationMovement(angle, stopCheck)
    }
}

class LinearMovementBuilder(linear: LinearActuator) : MovementBuilder<LinearMovement, LinearActuator>(linear) {
    var distance: Int = 0

    override fun makeMovement(): LinearMovement {
        return LinearMovement(distance, stopCheck)
    }
}

/*
 * Movement, actuator, and builder for executing a code block as a movement.
 */
// only need one of these
private val SIMPLE = object : Actuator<Movement> {
    // stop check has the code`
    override fun move(movement: Movement) = false
    override fun current() = 0
}

private class ExecutableMovementBuilder(private val function: () -> Boolean) :
    MovementBuilder<Movement, Actuator<Movement>>(SIMPLE) {
    override fun makeMovement(): Movement {
        return object : Movement {
            override val stopCheck = function
        }
    }
}

/**
 * How long each step should take to execute **at a minimum**.
 */
interface ActionSpeed {
    val millis: Long
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
 * Builds up movements for an action.
 */
class ActionBuilder {
    private val steps = mutableListOf<MovementBuilder<*, *>>()
    var requestedSpeed: ActionSpeed = DefaultActionSpeed.NORMAL

    /**
     * DSL to rotate a [Rotator]: requires stop check and angle
     */
    infix fun Rotator.rotate(init: RotationMovementBuilder.() -> Unit) {
        steps += RotationMovementBuilder(this).apply(init)
    }

    /**
     * DSL to rotate a [Rotator] to a specific angle without a stop check.
     */
    infix fun Rotator.rotate(angle: Int) {
        steps += RotationMovementBuilder(this).apply {
            this.angle = angle
        }
    }

    /**
     * DSL to rotate a [Rotator] in a "positive" direction with the given check used as a stop-check.
     */
    infix fun Rotator.forwardUntil(forwardCheck: () -> Boolean) {
        steps += RotationMovementBuilder(this).apply {
            angle = Int.MAX_VALUE
            stopCheck = forwardCheck
        }
    }

    /**
     * DSL to rotate a [Rotator] in a "negative" direction until a stop check is true.
     */
    infix fun Rotator.backwardUntil(backwardCheck: () -> Boolean) {
        steps += RotationMovementBuilder(this).apply {
            angle = -Int.MAX_VALUE
            stopCheck = backwardCheck
        }
    }

    /**
     * DSL to move a [LinearActuator] to a specific position with a stop check.
     */
    infix fun LinearActuator.extend(init: LinearMovementBuilder.() -> Unit) {
        steps += LinearMovementBuilder(this).apply(init)
    }

    /**
     * DSL to move a [LinearActuator] to a specific position without a stop check.
     */
    infix fun LinearActuator.goTo(position: Int) {
        steps += LinearMovementBuilder(this).apply {
            distance = position
        }
    }

    /**
     * DSL to execute a code block as a movement. The [function] must return `true` if the code was "completed" (e.g.
     * this is a "naked" stop check).
     */
    fun execute(function: () -> Boolean) {
        steps += ExecutableMovementBuilder(function)
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
    fun build(): Action {
        return Action(steps.associate { it.build() })
    }
}

/**
 * Associates an [Action] with a [speed]. Note that the `Action` is rebuilt each time this is called.
 */
class ExecutableAction internal constructor(private val builder: ActionBuilder) {
    val action: Action
        get() = builder.build()
    val speed: ActionSpeed = builder.requestedSpeed
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
    operator fun plus(actionBuilder: ActionBuilder) {
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
    fun build(): List<ExecutableAction> = steps.map { ExecutableAction(it) }
}

/**
 * Typesafe "builder" (DSL) for creating a sequence of actions.
 */
fun sequence(init: ActionSequence.() -> Unit): ActionSequence = ActionSequence().apply(init)

/**
 * Typesafe "builder" (DSL) for just the actions.
 */
fun action(init: ActionBuilder.() -> Unit): ActionBuilder = ActionBuilder().apply(init)
