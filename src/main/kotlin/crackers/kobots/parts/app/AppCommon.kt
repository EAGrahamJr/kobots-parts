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

package crackers.kobots.app

import com.typesafe.config.ConfigFactory
import crackers.hassk.HAssKClient
import crackers.kobots.parts.app.KobotSleep
import crackers.kobots.parts.app.KobotsEvent
import crackers.kobots.parts.app.publishToTopic
import org.json.JSONObject
import java.awt.Color
import java.awt.FontMetrics
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Common application control and configuration. This is _highly_ opinionated and is not intended to be a general
 * purpose library.
 */
object AppCommon {
    // threads and execution control
    val executor by lazy { Executors.newScheduledThreadPool(4) }
    val runFlag = AtomicBoolean(true)

    /**
     * Run a [block] and ensure it takes up **at least** [maxPause] time. This is basically to keep various parts from
     * overloading the various buses.
     *
     * The purported granularity of the pause is _ostensibly_ nanoseconds.
     */
    fun <R> executeWithMinTime(maxPause: Duration, block: () -> R): R {
        val pauseForNanos = maxPause.toNanos()
        val startAt = System.nanoTime()

        val response = block()

        val runtime = System.nanoTime() - startAt
        if (runtime < pauseForNanos) KobotSleep.nanos(pauseForNanos - runtime)
        return response
    }

    /**
     * Run an execution loop until the run-flag says stop
     */
    fun checkRun(maxPause: Duration, block: () -> Unit): Future<*> = executor.submit {
        while (runFlag.get()) executeWithMinTime(maxPause) { block() }
    }

    /**
     * HomeAssistant client
     */
    val hasskClient by lazy {
        with(ConfigFactory.load()) {
            HAssKClient(getString("ha.token"), getString("ha.server"), getInt("ha.port"))
        }
    }

    /**
     * Generic topic and event for sleep/wake events.
     */
    const val SLEEP_TOPIC = "System.Sleep"

    class SleepEvent(val sleep: Boolean) : KobotsEvent

    fun goToSleep() = publishToTopic(SLEEP_TOPIC, SleepEvent(true))
    fun wakey() = publishToTopic(SLEEP_TOPIC, SleepEvent(false))

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

    fun microDuration(micros: Long): Duration = Duration.ofNanos(micros * 1000)

    /**
     * Note this only extracts the HSB _hue_ component of the color.
     */
    fun colorInterval(from: Color, to: Color, steps: Int): List<Color> =
        colorIntervalFromHSB(
            Color.RGBtoHSB(from.red, from.green, from.blue, null)[0] * 360f,
            Color.RGBtoHSB(to.red, to.green, to.blue, null)[0] * 360f,
            steps
        )

    /**
     * Generate a range of [n] colors based on the HSB hue (0==red) [angleFrom] to [angleTo]
     */
    fun colorIntervalFromHSB(angleFrom: Float, angleTo: Float, n: Int): List<Color> {
        val angleRange = angleTo - angleFrom
        val stepAngle = angleRange / n
        val colors = mutableListOf<Color>()
        for (i in 0 until n) {
            val angle = angleFrom + i * stepAngle
            colors += Color.getHSBColor(angle / 360f, 1f, 1f)
        }
        return colors
    }

    fun Color.scale(percent: Int) = percent.let { pct ->
        if (pct !in (1..100)) throw IllegalArgumentException("Percentage is out of range")
        val p = pct / 100f
        Color(
            (red * p).roundToInt(),
            (green * p).roundToInt(),
            (blue * p).roundToInt()
        )
    }

    fun Int.kelvinToRGB(): Color {
        val tempK = (Integer.min(40000, Integer.max(1000, this)) / 100.0).roundToInt()

        fun Double.limit(): Int = (
            if (this < 0) {
                0.0
            } else if (this > 255) {
                255.0
            } else {
                this
            }
            ).roundToInt()

        val r: Int = if (tempK <= 66) {
            255
        } else {
            var temp = tempK - 60.0
            temp = 329.698727446 * temp.pow(-0.1332047592)
            temp.limit()
        }

        val g: Int = if (tempK <= 66) {
            val temp = 99.4708025861 * ln(tempK.toDouble()) - 161.1195681661
            temp.limit()
        } else {
            var temp = tempK - 60.0
            temp = 288.1221695283 * temp.pow(-0.0755148492)
            temp.limit()
        }

        val b: Int = if (tempK >= 66) {
            255
        } else if (tempK <= 19) {
            0
        } else {
            var temp = tempK - 10.0
            temp = 138.5177312231 * ln(temp) - 305.0447927307
            temp.limit()
        }

        return Color(r, g, b)
    }

    val PURPLE = Color(0xB4, 0, 0xFF)
    val GOLDENROD = Color(255, 150, 0)
    val ORANGISH = Color(1f, .55f, 0f)

    /**
     * Use font metrics to center some text in an area
     */
    fun FontMetrics.center(text: String, width: Int) = kotlin.math.max((width - stringWidth(text)) / 2, 0)

    /**
     * Load an image.
     */
    fun loadImage(name: String) = ImageIO.read(object {}::class.java.getResourceAsStream(name))
}
