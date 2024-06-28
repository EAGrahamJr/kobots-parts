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

package crackers.kobots.parts

import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/*
 * Just stuff.
 */

// variants on a theme
const val on = true
const val ON = true
const val off = false
const val OFF = false

/**
 * Extension function on a JSON object to get a on/off status as a boolean.
 */
fun JSONObject.onOff(key: String): Boolean = this.optString(key, "off") == "on"

/**
 * Elapsed time.
 */
fun Instant.elapsed(): Duration = Duration.between(this, Instant.now())

/**
 * Captures data in a simple FIFO buffer for averaging. Probably not suitable for large sizes.
 */
class SimpleAverageMeasurement(val bucketSize: Int, val initialValue: Float = Float.MAX_VALUE) {
    private val dumbBuffer = CopyOnWriteArrayList<Float>()

    val value: Float
        get() = if (dumbBuffer.isEmpty()) initialValue else dumbBuffer.average().toFloat()

    operator fun plusAssign(v: Float) {
        dumbBuffer += v
        while (dumbBuffer.size > bucketSize) dumbBuffer.removeAt(0)
    }
}

/**
 * Microseconds to `Duration`.
 */
fun microDuration(micros: Long): Duration = Duration.ofNanos(micros * 1000)

/**
 * Enum value or null.
 */
inline fun <reified T : Enum<T>> enumValue(s: String): T? = try {
    enumValueOf<T>(s.uppercase())
} catch (_: IllegalArgumentException) {
    null
}

fun Float.toFahrenheit(): Float = (this * 9f / 5f) + 32
