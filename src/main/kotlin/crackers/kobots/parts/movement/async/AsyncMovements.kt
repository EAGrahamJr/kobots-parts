/*
 * Copyright 2022-2025 by E. A. Graham, Jr.
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

package crackers.kobots.parts.movement.async

import crackers.kobots.app.AppCommon
import org.slf4j.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val _defaultDuration = 2.seconds

interface SceneMovement {
    /**
     * How long the movement should take.
     */
    var duration: Duration

    /**
     * Easing function to use for the movement.
     */
    var ease: EasingFunction
    var startDelay: Duration
    var endDelay: Duration
}

/**
 * Parameter holder class for moveTo DSL
 */
open class Rotate : SceneMovement {
    override var duration: Duration = _defaultDuration
    override var ease: EasingFunction = linear
    override var startDelay: Duration = Duration.ZERO
    override var endDelay: Duration = Duration.ZERO
    open var angle: Int = 0
}

/**
 * Parameter holder class for smooth moveTo DSL
 */
open class SmoothRotate : Rotate() {
    final override var ease: EasingFunction = smooth
}

/**
 * Parameter holder class for soft launch moveTo DSL
 */
open class SoftLaunchRotate : Rotate() {
    final override var ease: EasingFunction = softLaunch
}

/**
 * Parameter holder class for soft landing moveTo DSL
 */
open class SoftLandingRotate : Rotate() {
    final override var ease: EasingFunction = softLanding
}

interface AsyncActuator {
    /**
     * Allows an implementation to log stop-checks or not.
     */
    fun stopLogger(): Logger? = null

    /**
     * Set a kill switch function that can interrupt the rotation. This will cause an exception to be thrown if the
     * function returns true during rotation.
     */
    suspend fun myLittleKillSwitch(): Boolean = false

    /**
     * Return some notion of the "current" position/value of the actuator.
     */
    val current: Int

    /**
     * Check whether the movement should be stopped, throwing a [MovementStoppedException] if so.
     */
    suspend fun stopCheck(location: Int) {
        // check for interrupts
        if (myLittleKillSwitch()) {
            stopLogger()?.warn("Translation to $location interrupted by kill switch")
            throw MovementStoppedException()
        }
        if (!AppCommon.applicationRunning) {
            stopLogger()?.warn("Translation to $location interrupted by application shutdown")
            throw MovementStoppedException()
        }
    }

    suspend fun softLanding(target: Int, time: Duration)

    suspend fun softLaunch(target: Int, time: Duration)

    suspend fun smoothMove(target: Int, time: Duration)
}
