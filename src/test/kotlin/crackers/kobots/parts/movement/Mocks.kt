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

import kotlin.random.Random

/**
 * A generic rotoator that can be used for testing.
 */
open class MockRotator : Rotator() {
    var angle: Int = 0
    var stopCheckResult = false

    override fun rotateTo(angle: Int): Boolean {
        var doneYet = stopCheckResult || this.angle == angle
        if (doneYet) return true

        if (angle > this.angle) {
            this.angle += 1
        } else if (angle < this.angle) this.angle -= 1
        doneYet = this.angle == angle
        return doneYet
    }

    override val current: Number
        get() = angle
    override fun current(): Int = angle
}

open class MockLinear : LinearActuator() {
    var percentage: Int = 0
    var stopCheckResult = false

    override fun extendTo(percentage: Int): Boolean {
        var doneYet = stopCheckResult || this.percentage == percentage
        if (doneYet) return true

        if (percentage > this.percentage) {
            this.percentage += 1
        } else if (percentage < this.percentage) this.percentage -= 1
        doneYet = this.percentage == percentage
        return doneYet
    }

    override val current: Number
        get() = percentage

    override fun current(): Int = percentage
}

fun runAndGetCount(block: () -> Boolean): Int {
    var count = 0
    while (!block()) count++
    return count
}

fun randomServoAngle() = Random.nextInt(180)
