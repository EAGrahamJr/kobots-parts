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

package crackers.kobots.parts.app

import com.diozero.util.SleepUtil
import java.time.Duration

/**
 * Shortcuts for sleeping, wrapping the `diozero` utils. Sleeps should be used
 * **very** judiciously.
 */
object KobotSleep {
    fun nanos(nanos: Long) {
        SleepUtil.busySleep(nanos)
    }

    fun micros(micros: Long) {
        duration(Duration.ofNanos(micros * 100))
    }

    fun millis(millis: Long) {
        duration(Duration.ofMillis(millis))
    }

    fun seconds(seconds: Long) {
        duration(Duration.ofSeconds(seconds))
    }

    fun duration(d: Duration) {
        nanos(d.toNanos())
    }

    infix fun of(d: kotlin.time.Duration) {
        nanos(d.inWholeNanoseconds)
    }
}
