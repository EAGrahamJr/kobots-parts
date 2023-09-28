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

import crackers.kobots.parts.app.io.SmallMenuDisplay.DisplayMode
import crackers.kobots.parts.center
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage

/**
 * An abstract class that creates screen graphics for the NeoKeyMenu display. This is primarily intended for use with a
 * small OLED display (e.g. 128x32). This class only creates image: the [displayFun] receiver function is responsible for
 * displaying the image.
 *
 * The assumption for sizing is that the number of items given to display (4) _should_ fit (128/32).
 *
 * Note: text is **NOT** truncated, so if the text is "too long", it may overwrite other text.
 *
 * @param mode the display mode (defaults to [DisplayMode.TEXT])
 *
 *
 * TODO "vertical" menu display
 */
abstract class SmallMenuDisplay(private val mode: DisplayMode = DisplayMode.TEXT) : NeoKeyMenu.MenuDisplay {

    enum class DisplayMode {
        ICONS, TEXT
    }

    private val menuGraphics: Graphics2D

    private val withImagestextHeight: Int
    private val withImagestextBaseline: Int
    private val iconImageSize: Int
    private val withImagesFont: Font = Font(Font.SANS_SERIF, Font.PLAIN, 9)

    private val withTextFont = Font(Font.SANS_SERIF, Font.PLAIN, 12)
    private var withTextFirstLine: Int
    private val withTextSecondLine: Int

    /**
     * Draw to a separate image. This allows the image to be scaled to the actual display size, or placed within another
     * image.
     */
    protected val menuImage = BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_BYTE_GRAY).also { img ->
        menuGraphics = (img.graphics as Graphics2D).apply {
            val fm = getFontMetrics(withImagesFont)
            withImagestextHeight = fm.height
            withImagestextBaseline = IMG_HEIGHT - fm.descent
            iconImageSize = IMG_HEIGHT - withImagestextHeight

            withTextFirstLine = getFontMetrics(withTextFont).ascent + 1
            withTextSecondLine = IMG_HEIGHT - getFontMetrics(withTextFont).descent
        }
    }

    /**
     * Draw icons + text for each menu item.
     */
    protected fun showIcons(items: List<NeoKeyMenu.MenuItem>) {
        with(menuGraphics) {
            font = withImagesFont
            // clear the image
            color = Color.BLACK
            fillRect(0, 0, IMG_WIDTH, IMG_HEIGHT)
            // draw and scale the icons
            color = Color.WHITE
            items.forEachIndexed { index, item ->
                val offset = index * IMG_HEIGHT
                val imageX = offset + withImagestextHeight / 2
                if (item.icon != null) {
                    drawImage(item.icon, imageX, 0, iconImageSize, iconImageSize, null)
                }
                val text = item.toString()
                val textX = offset + fontMetrics.center(text, IMG_HEIGHT)
                drawString(text, textX, withImagestextBaseline)
            }
        }
    }

    /**
     * Draw text for each menu item -- this is done in two columns with two rows.
     */
    protected fun showText(items: List<NeoKeyMenu.MenuItem>) {
        val secondColumnOffset = (MAX_WD / 2) - 3
        with(menuGraphics) {
            font = withTextFont
            // clear the image
            color = Color.BLACK
            fillRect(0, 0, IMG_WIDTH, IMG_HEIGHT)
            // draw the text
            color = Color.WHITE
            drawString(items[0].toString(), 0, withTextFirstLine)
            drawString(items[1].toString(), secondColumnOffset, withTextFirstLine)
            drawString(items[2].toString(), 0, withTextSecondLine)
            drawString(items[3].toString(), secondColumnOffset, withTextSecondLine)
        }
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
        const val IMG_WIDTH = 128
        const val IMG_HEIGHT = 32
        private const val HALF_HT = IMG_HEIGHT / 2
        private const val MAX_WD = IMG_WIDTH - 1
    }
}