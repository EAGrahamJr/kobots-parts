/*
 * Copyright 2022-2024 by E. A. Graham, Jr.
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

package crackers.kobots.graphics.animation

import crackers.kobots.app.AppCommon
import crackers.kobots.parts.scale
import crackers.kobots.parts.scheduleAtRate
import crackers.kobots.parts.sleep
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random.Default.nextInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Draws "Matrix" movie code rain on a specified region.
 */
class MatrixRain(
    private val graphics: Graphics2D,
    private val x: Int,
    private val y: Int,
    private val width: Int,
    private val height: Int,
    private val displayFont: Font = Font(Font.SANS_SERIF, Font.PLAIN, 10),
    private val useBold: Boolean = true,
    private val leadColor: Color = Color.WHITE,
    private val boldColor: Color = Color.GREEN,
    private val normalColor: Color = boldColor.scale(50),
    private val backgroundColor: Color = Color.BLACK,
    private val updateSpeed: Duration = 50.milliseconds
) {
    private val logger: Logger = LoggerFactory.getLogger("MatrixRain")

    private val maxColumns: Int
    private val maxRows: Int
    private val cellWidth: Int

    // available columns to use
    private val availableColumns = mutableListOf<Int>()

    init {
        // figure out how many rows and columns we have: use the widest character
        with(graphics) {
            val oldFont = font
            font = displayFont
            cellWidth = CHAR_LIST.maxOf { fontMetrics.stringWidth(it) }
            maxColumns = width / cellWidth
            maxRows = height / fontMetrics.height
            // should have more than 3 columns and 5 rows
            require(maxColumns > 3 && maxRows > 5) { "Not useful with less than 3 columns and/or 5 rows: $maxColumns/$maxRows" }
            font = oldFont
        }
        availableColumns += (0 until maxColumns).toList().shuffled()
    }

    private inner class LineOfStuff(val column: Int) {
        private val length = nextInt(3, maxRows)
        private val leadRow = AtomicInteger(0)
        private val row = AtomicInteger(-1)
        private val lastRow = AtomicInteger(-length)

        fun getLead() = if (leadRow.get() > maxRows) null else leadRow.getAndIncrement()
        fun getNext() = when {
            row.get() < 0 -> {
                row.incrementAndGet()
                null
            }

            row.get() > maxRows -> null
            else -> row.getAndIncrement()
        }

        fun deleteLast() = when {
            okToDelete() -> null
            lastRow.get() < 0 -> {
                lastRow.incrementAndGet()
                null
            }

            else -> lastRow.getAndIncrement()
        }

        fun okToDelete() = lastRow.get() > maxRows
    }

    private fun context(block: () -> Unit) {
        with(graphics) {
            val oldFont = font
            val oldColor = color
            val oldBackground = background
            val oldClip = clipBounds
            setClip(x, y, width, height)

            try {
                font = displayFont
                color = normalColor
                background = backgroundColor
                block()
            } finally {
                font = oldFont
                color = oldColor
                background = oldBackground
                clip = oldClip
            }
        }
    }

    private val lineList = mutableListOf<LineOfStuff>()
    private fun aLoop() = context {
        // add any new lines if we don't have any and there are more than 3 available
        if (lineList.size < maxColumns && availableColumns.size > 3) {
            repeat(2) {
                availableColumns.shuffle()
                val x = availableColumns.removeAt(0)
                lineList += LineOfStuff(x)
            }
        }
        val removeLines = mutableListOf<LineOfStuff>()
        for (line in lineList) with(graphics) {
            // where characters in this line will start
            val lineX = line.column * cellWidth

            line.deleteLast()?.let { lineRow ->
                lineX.clearCell(lineRow)
                if (line.column !in availableColumns) {
                    availableColumns.add(line.column)
                }
            }

            line.getNext()?.let { lineRow ->
                lineX.clearCell(lineRow)

                // if it's bold, check the bold flag, otherwise use the color
                if (nextInt(1, 5) == 1) {
                    if (useBold) {
                        font = font.deriveFont(Font.BOLD)
                        color = normalColor
                    } else {
                        font = displayFont
                        color = boldColor
                    }
                } else {
                    font = displayFont
                    color = normalColor
                }
                val y = lineRow * fontMetrics.height + fontMetrics.ascent
                // draw random char at new location
                drawString(CHAR_LIST.random(), lineX, y)
            }

            line.getLead()?.let { lineRow ->
                lineX.clearCell(lineRow)

                // draw the lead character using the lead color, normal font
                color = leadColor
                font = displayFont.deriveFont(Font.BOLD)
                val y = lineRow * fontMetrics.height + fontMetrics.ascent
                drawString(CHAR_LIST.random(), lineX, y)
            }

            if (line.okToDelete()) {
                availableColumns += line.column
                removeLines += line
            }
        }
        lineList.removeAll(removeLines)
    }

    private fun Int.clearCell(row: Int) {
        val topOfLine = row * graphics.fontMetrics.height
        graphics.clearRect(this, topOfLine, cellWidth, graphics.fontMetrics.height)
    }

    private var future: Future<*>? = null

    /**
     * Start the matrix loop
     */
    fun start(refresh: () -> Unit) {
        future?.let {
            logger.warn("Rain loop already started")
            return
        }

        graphics.clearRect(x, y, width, height)
        future = AppCommon.executor.scheduleAtRate(updateSpeed) {
            aLoop()
            refresh()
        }
    }

    /**
     * Stop it -- and then make sure it's stopped
     */
    fun stop() {
        future?.let { f ->
            f.cancel(true)
            while (!f.isDone) 5.milliseconds.sleep()
            future = null
        }
    }

    companion object {
        // @formatter:off
        internal val CHAR_LIST = listOf(
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v",
            "w", "x", "y", "z", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R",
            "S", "T", "U", "V", "W", "X", "Y", "Z", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "!", "#", "$", "%",
            "^", "&", "(", ")", "-", "+", "=", "[", "]", "{", "}", "|", ";", ":", "<", ">", ",", ".", "?", "~", "`", "@",
            "*", "_", "'", "\\", "/", "\"", "Ç", "È", "Ì", "Í", "Ð", "Ñ", "Ò", "×", "Ø", "Ù", "Ú", "Ý", "Þ", "ß", "à", "£",
            "¤", "¥", "§", "ª", "¶", "º", "»", "¿", "Ä", "Å", "é", "ê", "í", "ï", "å", "æ", "ç", "è", "ð", "ñ", "ò", "ö",
            "ø", "ù", "ý", "þ", "ā", "ć", "ĉ", "ė", "ě", "ĝ", "ģ", "ħ", "ī", "ı", "ķ", "Ľ", "Ł", "ł", "ń", "ň", "ō", "Œ",
            "œ", "ŕ", "ŗ", "ś", "ŝ", "š", "ť", "ū", "ų", "Ÿ", "ź", "ż", "Ž", "ž", "ș", "ț", "ë", "Ĉ", "Ď", "ď", "Ġ", "Ř",
            "°", "«", "±", "Δ", "Ξ", "Λ", "ｦ", "ｱ", "ｳ", "ｴ", "ｵ", "ｶ", "ｷ", "ｹ", "ｺ", "ｻ", "ｼ", "ｽ", "ｾ", "ｿ", "ﾀ", "ﾂ",
            "ﾃ", "ﾅ", "ﾆ", "ﾇ", "ﾈ", "ﾊ", "ﾋ", "ﾎ", "ﾏ", "ﾐ", "ﾑ", "ﾒ", "ﾓ", "ﾔ", "ﾕ", "ﾗ", "ﾘ", "ﾜ", "ﾍ", "ｲ", "ｸ", "ﾁ",
            "ﾄ", "ﾉ", "ﾌ", "ﾖ", "ﾙ", "ﾚ", "ﾛ", "ﾝ", "0", "1", "2", "3", "4", "5", "7", "8", "9", "Z", ":", ".", "=", "*",
            "+", "-", "<", ">"
        )
        // @formatter:on
    }
}
