package crackers.kobots.graphics.widgets

import crackers.kobots.graphics.plus
import crackers.kobots.graphics.toRadians
import java.awt.*
import java.awt.geom.AffineTransform
import kotlin.math.roundToInt

/**
 * Shows a sliding pointer on a 0-100 scale. By default, it occupies the top-left of the [graphics].
 */
open class VerticalPercentageIndicator(
    final override val graphics: Graphics2D,
    override val labelFont: Font,
    private val maxHeight: Int,
    override val x: Int = 0,
    override val y: Int = 0,
    override val label: String = "Pct",
    private val showValue: Boolean = true,
    override val lineWidth: Int = 1,
    private val pointerSize: Int = 4
) : KobotsWidget<Int> {

    private val LIFT_TOP_LABEL = "100"

    private val hundredP_Y: Int
    private val zeroP_Y: Int
    private val pointerLeft: Int
    private val pointerRight: Int
    private val maxY = y + maxHeight
    private val showValueWidth: Int

    init {
        with(graphics) {
            val fm = getFontMetrics(labelFont)
            pointerLeft = fm.stringWidth(LIFT_TOP_LABEL) + lineWidth + 1 + x
            pointerRight = pointerLeft + pointerSize

            // these define the actual up/down scale
            hundredP_Y = y + fm.ascent / 2 // 1/2 way into the top label
            zeroP_Y = maxY - fm.ascent / 2 // 1/2 way into the bottom label

            showValueWidth = fm.stringWidth("00")
        }
    }

    override val bounds = Rectangle(x, y, pointerRight - x, maxHeight)

    override fun drawStatic() = withGraphics {
        val ascent = fontMetrics.ascent
        val xLine = x + fontMetrics.stringWidth(LIFT_TOP_LABEL) // where to draw the vertical line

        // draw the top
        drawString(LIFT_TOP_LABEL, x, y + ascent)

        // right-justify BOTTOM
        val bottomLabel = "0"
        val bottomX = xLine - fontMetrics.stringWidth(bottomLabel)
        drawString(bottomLabel, bottomX, maxY - 1)

        // draw the scale -- should start at the bottom of the "100" and go to the tp of the 0"
        drawLine(xLine, ascent, xLine, maxY - ascent)

        // draw the label
        val labelY = (maxHeight - fontMetrics.stringWidth(label)) / 2
        font = labelFont.deriveFont(
            AffineTransform().apply {
                rotate(90.toRadians(), 0.0, 0.0)
            }
        )
        drawString(label, x, labelY)
    }

    private var lastValue = 0

    /**
     * Update the display to the current value. **THIS IS NOT THREAD-SAFE!!!**
     */
    override fun updateValue(currentValue: Int) {
        require(currentValue in 0..100) { "Must use a percentage of 0-100: $currentValue" }
        if (currentValue == lastValue) return

        withGraphics {
            lastValue = currentValue

            // if we're showing the value, locate it in the top 1/4 of the area
            if (showValue) {
                val ascent = fontMetrics.ascent
                val h = maxHeight / 4 + ascent / 2 // base of text
                clearRect(x, y + h - ascent, showValueWidth, ascent)
                drawString(currentValue.toString(), x, y + h)
                drawLine(x, y + h + 1, x + fontMetrics.stringWidth(LIFT_TOP_LABEL), y + h + 1)
            }

            // the indicator is somewhere between the two points
            val yRange = zeroP_Y - hundredP_Y
            val whereItIs = zeroP_Y - ((lastValue / 100f) * yRange).roundToInt()

            // clear the rectangle the indicator lives in and draw it
            stroke = BasicStroke()
            clearRect(pointerLeft, y, pointerSize, maxY)
            val pointer = Polygon() + (pointerLeft to whereItIs) +
                (pointerRight to whereItIs - pointerSize) +
                (pointerRight to whereItIs + pointerSize)
            fillPolygon(pointer)
        }
    }
}
