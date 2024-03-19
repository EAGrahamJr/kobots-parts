package crackers.kobots.graphics.widgets

import crackers.kobots.graphics.center
import crackers.kobots.graphics.plus
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.Rectangle

/**
 * Draws up/down arrows indicating which way something is "moving". The display is divided into 4 sections:
 * * label (drawn at the top)
 * * top arrow
 * * value label
 * * bottom arrow
 * The sizes of the arrows are based on some hacking up the available space into equals enough parts to make it look
 * at least not entirely sucky.
 *
 * Decreasing values will light up the "top" arrow and increasing values will have the opposite effect.
 */
class UpDownIndicator<T : Comparable<T>>(
    override val graphics: Graphics2D,
    override val labelFont: Font,
    val maxWidth: Int,
    private val maxHeight: Int,
    override val label: String = "Up/Down",
    override val lineWidth: Int = 1,
    override val x: Int = 0,
    override val y: Int = 0

) : KobotsWidget<T> {

    // define an arrow shape
    private val upArrow: Polygon
    private val downArrow: Polygon
    private val topOfMidLabel: Int
    private val fontAscent: Int
    private val fontHeight: Int

    init {
        graphics.getFontMetrics(labelFont).run {
            fontAscent = ascent
            fontHeight = height
        }

        val midX = maxWidth / 2
        val bumpIn = midX / 2
        val rightMostBumpX = x + maxWidth - bumpIn
        val leftMostBumpX = x + bumpIn

        topOfMidLabel = y + maxHeight / 2 - graphics.getFontMetrics(labelFont).ascent / 2

        // split the height left over from the labels into quarters
        val arrowHeight = (maxHeight - (fontHeight * 2)) / 2
        val midY = arrowHeight / 2

        upArrow = Polygon() + (x + midX to fontHeight) + (x + maxWidth to fontHeight + midY) +
            (rightMostBumpX to fontHeight + midY) + (rightMostBumpX to fontHeight + arrowHeight) +
            (leftMostBumpX to fontHeight + arrowHeight) + (leftMostBumpX to fontHeight + midY) +
            (x to fontHeight + midY)

        val maxY = y + maxHeight
        downArrow = Polygon() + (x + midX to maxY) + (x to maxY - midY) +
            (leftMostBumpX to maxY - midY) + (leftMostBumpX to maxY - arrowHeight) +
            (rightMostBumpX to maxY - arrowHeight) + (rightMostBumpX to maxY - midY) +
            (x + maxWidth to maxY - midY)
    }

    override val bounds = Rectangle(x, y, maxWidth, maxHeight)

    override fun drawStatic() = withGraphics {
        clearRect(x, y, maxWidth, maxHeight)
        val x = fontMetrics.center(label, maxWidth)
        drawString(label, this@UpDownIndicator.x + x, fontMetrics.ascent)
        draw(upArrow)
        draw(downArrow)
    }

    private var lastValue: T? = null

    override fun updateValue(currentValue: T) {
        if (currentValue == lastValue) return

        withGraphics {
            // clear the entire area and redraw
            clearRect(x, fontHeight, maxWidth, maxHeight - fontHeight)

            val text = currentValue.toString()
            val x = fontMetrics.center(text, maxWidth)
            drawString(text, this@UpDownIndicator.x + x, y + (maxHeight / 2) + fontHeight - lineWidth)

            if (lastValue == null || currentValue > lastValue!!) {
                drawPolygon(upArrow)
                fillPolygon(downArrow)
            } else {
                fillPolygon(upArrow)
                drawPolygon(downArrow)
            }

            lastValue = currentValue
        }
    }
}
