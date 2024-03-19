package crackers.kobots.graphics.widgets

import crackers.kobots.graphics.center
import crackers.kobots.graphics.plus
import java.awt.*
import kotlin.math.roundToInt

/**
 * Shows a sliding pointer between 0 and 100 horizontally.
 */
class HorizontalPercentageIndicator(
    override val graphics: Graphics2D,
    override val labelFont: Font,
    val maxWidth: Int,
    override val label: String = "Pct",
    override val lineWidth: Int = 1,
    val pointerSize: Int = 4,
    override val x: Int = 0,
    override val y: Int = 0

) : KobotsWidget<Int> {

    private val pointerTop: Int
    private val pointerBottom: Int
    private val zeroP_X: Int
    private val hundredP_X: Int

    init {
        graphics.run {
            val fm = getFontMetrics(labelFont)
            pointerTop = fm.height + lineWidth + y + 1
            pointerBottom = pointerTop + pointerSize + y
            // just one of those things that you don't need to re-calculate all the time
            val zeroWidth = fm.stringWidth("0") / 2
            zeroP_X = zeroWidth + x
            hundredP_X = x + maxWidth - zeroWidth
        }
    }

    override val bounds = Rectangle(x, y, maxWidth, pointerBottom - y)

    override fun drawStatic() = withGraphics {
        val x = fontMetrics.center(label, maxWidth)
        val fontY = y + fontMetrics.ascent
        drawString(label, this@HorizontalPercentageIndicator.x + x, fontY)

        // draw the 0 and 100, which may over-ride some the label
        drawString("0", this@HorizontalPercentageIndicator.x, fontY)
        val hx = this@HorizontalPercentageIndicator.x + maxWidth - fontMetrics.stringWidth("100")
        drawString("100", hx, fontY)

        // if the line is really wide, try to move it so that the topmost row of pixels clips the ascent
        val lineY = (lineWidth / 2).coerceAtLeast(1) + fontMetrics.ascent + y
        drawLine(zeroP_X, lineY, hundredP_X, lineY)
    }

    private var lastValue = 0

    /**
     * Update the display to the current value. **THIS IS NOT THREAD-SAFE!!!**
     */
    override fun updateValue(currentValue: Int) {
        require(currentValue in 0..100) { "Must use a percentage of 0-100" }
        if (currentValue == lastValue) return

        withGraphics {
            lastValue = currentValue
            clearRect(bounds.x, pointerTop, maxWidth, pointerBottom - pointerTop)

            val xRange = hundredP_X - zeroP_X
            val whereItIs = zeroP_X + ((lastValue / 100f) * xRange).roundToInt()
            stroke = BasicStroke()
            val p = Polygon() +
                (whereItIs to pointerTop) +
                (whereItIs + pointerSize to pointerBottom) +
                (whereItIs - pointerSize to pointerBottom)
            fillPolygon(p)
        }
    }
}
