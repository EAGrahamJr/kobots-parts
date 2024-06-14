package crackers.kobots.parts

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
 * Convert a temperature in Kelvin to an RGB color. **THIS IS NOT ACCURATE!!!**
 *
 * This is based on the algorithm from https://tannerhelland.com/2012/09/18/convert-temperature-rgb-algorithm-code.html
 * Note that round-tripping between this and the inverse function does not work.
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
 * Note that round-tripping between this and the inverse function does not work.
 */
fun Color.toKelvin(): Int {
    // Convert RGB to XYZ using the sRGB color space
    val r = red / 255.0
    val g = green / 255.0
    val b = blue / 255.0

    val rLinear = if (r > 0.04045) ((r + 0.055) / (1.055)).pow(2.4) else r / 12.92
    val gLinear = if (g > 0.04045) ((g + 0.055) / (1.055)).pow(2.4) else g / 12.92
    val bLinear = if (b > 0.04045) ((b + 0.055) / (1.055)).pow(2.4) else b / 12.92

    val x = rLinear * 0.4124 + gLinear * 0.3576 + bLinear * 0.1805
    val y = rLinear * 0.2126 + gLinear * 0.7152 + bLinear * 0.0722
    val z = rLinear * 0.0193 + gLinear * 0.1192 + bLinear * 0.9505

    // Convert XYZ to CCT
    val n = (x - 0.3320) / (0.1858 - y)
    val cct = 449.0 * n.pow(3) + 3525.0 * n.pow(2) + 6823.3 * n + 5520.33

    return cct.roundToInt()
}

fun Int.squared() = toDouble().pow(2).toInt()

/**
 * Additional math to convert to _mired_ values.
 */
fun Color.toMireds(): Int = 1_000_000 / toKelvin()
fun Int.miredsToColor(): Color = (1_000_000 / this).kelvinToRGB()

fun Color.toLuminance() = (0.2126 * red + 0.7152 * green + 0.0722 * blue)
fun Color.toLuminancePerceived() = (0.299 * red + 0.587 * green + 0.114 * blue)
fun Color.toLuminancePerceived2() = sqrt(0.299 * red.squared() + 0.587 * green.squared() + 0.114 * blue.squared())

val PURPLE = Color(0xB4, 0, 0xFF)
val GOLDENROD = Color(255, 150, 0)
val ORANGISH = Color(255, 75, 0)

/**
 * Fits a font to a specified number of pixels.
 */
fun Graphics2D.fitFont(f: Font, h: Int): Font {
    val nextFont = f.deriveFont(f.size + .5f)
    val fm = getFontMetrics(nextFont)
    return if (fm.height > h) f else fitFont(nextFont, h)
}
