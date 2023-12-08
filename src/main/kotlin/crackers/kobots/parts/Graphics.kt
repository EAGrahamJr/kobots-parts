package crackers.kobots.parts

import java.awt.Color
import java.awt.FontMetrics
import javax.imageio.ImageIO
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

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
 * Scale (or brighten/dim) a color by a percentage (1-100).
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

fun Double.colorLimit() = this.roundToInt().colorLimit()
fun Int.colorLimit() = this.coerceIn(0, 255)

/**
 * Convert a temperature in Kelvin to an RGB color.
 *
 * This is based on the algorithm from https://tannerhelland.com/2012/09/18/convert-temperature-rgb-algorithm-code.html
 */
fun Int.kelvinToRGB(): Color {
    var red: Double
    var green: Double
    var blue: Double

    val temp = this.coerceIn(1000, 40000) / 100.0

    if (temp <= 66.0) {
        red = 255.0
        green = temp
        green = 99.4708025861 * ln(green) - 161.1195681661
        if (temp <= 19.0) {
            blue = 0.0
        } else {
            blue = temp - 10.0
            blue = 138.5177312231 * ln(blue) - 305.0447927307
        }
    } else {
        red = temp - 60.0
        red = 329.698727446 * red.pow(-0.1332047592)
        green = temp - 60.0
        green = 288.1221695283 * green.pow(-0.0755148492)
        blue = 255.0
    }

    return Color(red.colorLimit(), green.colorLimit(), blue.colorLimit())
}

/**
 * Convert a color to a Kelvin temperature. **THIS IS NOT ACCURATE!!!**
 *
 * This is based on the algorithm from https://tannerhelland.com/2012/09/18/convert-temperature-rgb-algorithm-code.html
 */
fun Color.toKelvin(): Int {
    val r = red / 255.0
    val g = green / 255.0
    val b = blue / 255.0

    val x = 0.4124 * r + 0.3576 * g + 0.1805 * b
    val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    val z = 0.0193 * r + 0.1192 * g + 0.9505 * b

    val n = (x - 0.3320) / (0.1858 - y + 0.3320)
    val cct = 449.0 * n.pow(3.0) + 3525.0 * n.pow(2.0) + 6823.3 * n + 5520.33
    return cct.roundToInt()
}

val PURPLE = Color(0xB4, 0, 0xFF)
val GOLDENROD = Color(255, 150, 0)
val ORANGISH = Color(255, 130, 0)

/**
 * Use font metrics to center some text in an area
 */
fun FontMetrics.center(text: String, width: Int) = kotlin.math.max((width - stringWidth(text)) / 2, 0)

/**
 * Load an image.
 */
fun loadImage(name: String) = ImageIO.read(object {}::class.java.getResourceAsStream(name))
