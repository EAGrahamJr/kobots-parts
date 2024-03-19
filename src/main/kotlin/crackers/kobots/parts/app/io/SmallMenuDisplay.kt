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

package crackers.kobots.parts.app.io

import crackers.kobots.graphics.center
import crackers.kobots.parts.app.io.SmallMenuDisplay.DisplayMode
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage

/**
 * An abstract class that creates screen graphics for the NeoKeyMenu display. This is primarily intended for use with a
 * small OLED display (e.g. 128x32). This class only creates the _image_: the [displayFun] receiver function is
 * responsible for displaying the image. It also
 *
 * If the [mode] is [DisplayMode.TEXT], the image is constructed with two items on two rows.
 *
 * The assumption for sizing is that the number of items given to display (4) _should_ fit (128/32).
 *
 * Note: text is **NOT** truncated, so if the text is "too long", it may overwrite other text.
 *
 * @param mode the display mode (defaults to [DisplayMode.TEXT])
 *
 *
 * TODO "vertical" menu display
 * TODO make it fit other things (multi-line?)
 */
abstract class SmallMenuDisplay(
    private val mode: DisplayMode = DisplayMode.TEXT,
    private val displayWidth: Int = DEFAULT_WIDTH,
    private val displayHeight: Int = DEFAULT_HEIGHT
) : NeoKeyMenu.MenuDisplay {

    enum class DisplayMode {
        ICONS, TEXT
    }

    private val menuGraphics: Graphics2D

    private val withImagesTextHeight: Int
    private val withImagesTextBaseline: Int
    private val iconImageSize: Int
    private val withImagesFont: Font = Font(Font.SANS_SERIF, Font.PLAIN, 9)

    private val withTextFont = Font(Font.SANS_SERIF, Font.PLAIN, 12)
    private var withTextFirstLine: Int
    private val withTextSecondLine: Int

    private var fgColor = Color.WHITE
    private var bgColor = Color.BLACK

    fun setForeground(color: Color) {
        fgColor = color
    }

    fun setBackground(color: Color) {
        bgColor = color
    }

    /**
     * Draw to a separate image. This allows the image to be scaled to the actual display size, or placed within another
     * image.
     */
    private val menuImage = BufferedImage(displayWidth, displayHeight, BufferedImage.TYPE_BYTE_GRAY).also { img ->
        menuGraphics = (img.graphics as Graphics2D).apply {
            val fm = getFontMetrics(withImagesFont)
            withImagesTextHeight = fm.height
            withImagesTextBaseline = displayHeight - fm.descent
            iconImageSize = displayHeight - withImagesTextHeight

            withTextFirstLine = getFontMetrics(withTextFont).ascent + 1
            withTextSecondLine = displayHeight - getFontMetrics(withTextFont).descent
        }
    }

    /**
     * Draw icons + text for each menu item.
     */
    private fun showIcons(items: List<NeoKeyMenu.MenuItem>) = with(menuGraphics) {
        font = withImagesFont
        clearImage()

        // draw and scale the icons
        color = fgColor
        items.forEachIndexed { index, item ->
            val offset = index * displayHeight
            val imageX = offset + withImagesTextHeight / 2
            if (item.icon != null) {
                drawImage(item.icon, imageX, 0, iconImageSize, iconImageSize, null)
            }
            val text = item.toString()
            val textX = offset + fontMetrics.center(text, displayHeight)
            drawString(text, textX, withImagesTextBaseline)
        }
    }

    /**
     * Draw text for each menu item -- this is done in two columns with two rows.
     */
    private fun showText(items: List<NeoKeyMenu.MenuItem>) = with(menuGraphics) {
        val wd = displayWidth - 1
        val secondColumnOffset = (wd / 2) - 3

        font = withTextFont
        clearImage()

        // draw the text
        color = Color.WHITE
        drawString(items[0].toString(), 0, withTextFirstLine)
        drawString(items[1].toString(), secondColumnOffset, withTextFirstLine)
        drawString(items[2].toString(), 0, withTextSecondLine)
        drawString(items[3].toString(), secondColumnOffset, withTextSecondLine)
    }

    private fun Graphics2D.clearImage() {
        // clear the image
        color = bgColor
        fillRect(0, 0, displayWidth, displayHeight)
    }

    /**
     * Display the menu items.
     */
    override fun displayItems(items: List<NeoKeyMenu.MenuItem>) {
        when (mode) {
            DisplayMode.ICONS -> showIcons(items)
            DisplayMode.TEXT -> showText(items)
        }
        displayFun(menuImage)
    }

    protected abstract fun displayFun(menuImage: BufferedImage)

    companion object {
        const val DEFAULT_WIDTH = 128
        const val DEFAULT_HEIGHT = 32
    }
}
