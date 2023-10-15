package crackers.kobots.parts

import org.json.JSONObject
import java.awt.Color
import java.awt.FontMetrics
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.toJavaDuration

/*
 * Just stuff.
 */

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

/**
 * Scale (or brighten/dim) a color by a percentage.
 */
fun Color.scale(percent: Int) = percent.let { pct ->
    if (pct !in (1..100)) throw IllegalArgumentException("Percentage is out of range")
    val p = pct / 100f
    Color(
        (red * p).roundToInt(),
        (green * p).roundToInt(),
        (blue * p).roundToInt()
    )
}

/**
 * Convert a temperature in Kelvin to an RGB color.
 *
 * This is based on the algorithm from https://tannerhelland.com/2012/09/18/convert-temperature-rgb-algorithm-code.html
 */
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

/**
 * Run a thing at a fixed rate extension function for better readability. Granularity is milliseconds.
 */
fun ScheduledExecutorService.scheduleAtFixedRate(intialDelay: Duration, period: Duration, command: Runnable) =
    scheduleAtFixedRate(command, intialDelay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS)

/**
 * Run a thing at a fixed delay extension function for better readability. Granularity is milliseconds.
 */
fun ScheduledExecutorService.scheduleWithFixedDelay(intialDelay: Duration, delay: Duration, command: Runnable) =
    scheduleWithFixedDelay(command, intialDelay.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS)

/**
 * Run a thing at a fixed rate extension function for better readability. Granularity is milliseconds.
 */
fun ScheduledExecutorService.scheduleAtFixedRate(
    intialDelay: kotlin.time.Duration,
    period: kotlin.time.Duration,
    command: Runnable
) =
    scheduleAtFixedRate(intialDelay.toJavaDuration(), period.toJavaDuration(), command)

/**
 * Run a thing at a fixed delay extension function for better readability. Granularity is milliseconds.
 */
fun ScheduledExecutorService.scheduleWithFixedDelay(
    intialDelay: kotlin.time.Duration,
    delay: kotlin.time.Duration,
    command: Runnable
) =
    scheduleWithFixedDelay(intialDelay.toJavaDuration(), delay.toJavaDuration(), command)
