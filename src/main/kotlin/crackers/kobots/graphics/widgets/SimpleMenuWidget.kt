package crackers.kobots.graphics.widgets

import crackers.kobots.graphics.center
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import kotlin.math.max
import kotlin.math.min

/**
 * A simple "selectable" vertical menu of selections. If the number of items exceeds the number of lines to display,
 * the menu _will_ scroll so that the next
 */
class SimpleMenuWidget(
    override val graphics: Graphics2D,
    val menuItems: List<MenuItem>,
    val width: Int,
    val height: Int,
    val itemFont: Font = graphics.font,
    override val x: Int = 0,
    override val y: Int = 0,
    override val label: String? = null,
    override val labelFont: Font = graphics.font,
    override val lineWidth: Int = 1
) : KobotsWidget<SimpleMenuWidget.MenuAction> {

    enum class MenuAction {
        NONE, PREVIOUS, NEXT, SELECT, PAGEUP, PAGEDOWN
    }

    interface MenuItem {
        val name: String
        fun action() {
            // default action does nothing: items can set this for execution on selection instead of querying for
            // current item
        }
    }

    private val numberOfLines: Int
    private val startItemsAtY: Int
    private var currentTopLine = 0
    private var highlightItem = -1
    private var selectedItem = -1
    private val lineHeight: Int
    private val lineAscent: Int

    override val bounds = Rectangle(x, y, width, height)

    val currentItem: MenuItem?
        get() = if (selectedItem < 0) null else menuItems[selectedItem]

    init {
        with(graphics) {
            val labelFm = getFontMetrics(labelFont)
            startItemsAtY = label?.let { labelFm.height } ?: 0
            val itemFm = getFontMetrics(itemFont)
            lineHeight = itemFm.height
            lineAscent = itemFm.ascent
            numberOfLines = (height - startItemsAtY) / lineHeight
        }
    }

    override fun drawStatic() = withGraphics {
        clearRect(x, y, width, height)
        label?.let { l ->
            font = labelFont
            val x = fontMetrics.center(l, width)
            drawString(l, x, fontMetrics.ascent)
        }
        clearRect(x, startItemsAtY, width, height - startItemsAtY)
        updateValue(MenuAction.NONE)
    }

    override fun updateValue(currentValue: MenuAction) = withGraphics {
        val maxLines = numberOfLines - 1
        val lastItem = menuItems.size - 1

        when (currentValue) {
            MenuAction.NONE -> {}
            MenuAction.PREVIOUS -> highlightItem--
            MenuAction.NEXT -> highlightItem++
            MenuAction.SELECT -> {
                selectedItem = highlightItem
                menuItems[selectedItem].action()
            }

            MenuAction.PAGEUP -> highlightItem -= numberOfLines
            MenuAction.PAGEDOWN -> highlightItem += numberOfLines
        }
        highlightItem = highlightItem.coerceIn(0, lastItem)

        // scroll the top to make sure it's visible
        // TODO this is too brute force: don't know why i can't algorythm this
        val maximumLines = min(currentTopLine + maxLines, lastItem)
        while (highlightItem !in currentTopLine..maximumLines) {
            if (highlightItem < currentTopLine) currentTopLine-- else currentTopLine++
        }
        currentTopLine = currentTopLine.coerceIn(0, max(lastItem, lastItem - maxLines))

        // draw what we have, highlighting the current selection
        // TODO arrow or inverse?
        (currentTopLine..maximumLines).forEachIndexed { lineIndex, itemIndex ->
            val currentColors = color to background

            // flip the colors and make a rectangle
            if (itemIndex == highlightItem) {
                color = currentColors.second
                background = currentColors.first
            }

            // clear this line, draw it and reset the colors
            val lineTop = lineIndex * lineHeight
            clearRect(x, lineTop, width, lineHeight)
            drawString(menuItems[itemIndex].name, x, lineTop + lineAscent)
            color = currentColors.first
            background = currentColors.second
        }
    }
}
