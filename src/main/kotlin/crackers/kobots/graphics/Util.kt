package crackers.kobots.graphics

import java.awt.Font
import java.awt.FontMetrics
import java.awt.Polygon
import javax.imageio.ImageIO

/*
 * Stuff for more complex graphics beyond what's shared with LEDs
 */

/**
 * Use font metrics to center some text in an area
 */
fun FontMetrics.center(text: String, width: Int) = kotlin.math.max((width - stringWidth(text)) / 2, 0)

/**
 * Load an image.
 */
fun loadImage(name: String) = ImageIO.read(object {}::class.java.getResourceAsStream(name))

/**
 * A convenience function to load a custom font from resources.
 */
fun loadFont(name: String) = Font.createFont(Font.TRUETYPE_FONT, object {}::class.java.getResourceAsStream(name))

/**
 * Function to easily add points to a polygon in a chainable function.
 */
operator fun Polygon.plus(v: Pair<Int, Int>): Polygon = this.apply { addPoint(v.first, v.second) }

/**
 * Don't know if this will be useful anywhere else, but is used a lot in graphics.
 */
fun Int.toRadians() = Math.toRadians(this.toDouble())
