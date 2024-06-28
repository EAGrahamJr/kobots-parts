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

package crackers.kobots.graphics.widgets

import crackers.kobots.graphics.center
import crackers.kobots.graphics.plus
import java.awt.*
import java.awt.geom.AffineTransform
import kotlin.math.min

/**
 * Draws a "pointer" within a square box defined by 2 x the [size]. If the label is set, the bounding box will be
 * **higher** than [size] by the _font height_ of the [labelFont].
 */
class DirectionPointer(
    override val graphics: Graphics2D,
    override val labelFont: Font,
    private val size: Int,
    val clockWise: Boolean = true,
    override val label: String = "",
    override val lineWidth: Int = 1,
    override val x: Int = 0,
    override val y: Int = 0,
    val startAngle: Int = 0,
    val endAngle: Int = 360
) : KobotsWidget<Int> {

    // offset all the things if we have a label
    private val labelOffset = if (label.isEmpty()) 0 else graphics.getFontMetrics(labelFont).height
    private val startY = y + labelOffset

    private val centerX = x + size.toDouble()
    private val centerY = startY + size.toDouble()

    private val pointer: Shape
    private val circleStroke: Stroke

    init {
        val bump = 3 * lineWidth
        val headStart = size - bump

        // create the pointer and move it to the center, pointing at 0 degrees (3 o'clock)
        val p = Polygon() + (0 to 0) + (headStart to 0) + (headStart to bump) + (size - 1 to 0) + (headStart to -bump) +
            (headStart to 0)
        pointer = AffineTransform.getTranslateInstance(centerX, centerY)
            .createTransformedShape(p)

        // create the dotted line for the "scope"
        val w = min(lineWidth / 2, 1).toFloat()
        circleStroke = BasicStroke(
            w,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            10.0f,
            floatArrayOf(lineWidth * 2f),
            0.0f
        )
    }

    override val bounds = Rectangle(x, y, 2 * size, size + labelOffset)

    override fun drawStatic() {
        withGraphics {
            if (label.isNotEmpty()) {
                val x = fontMetrics.center(label, size)
                drawString(label, this@DirectionPointer.x + x, y + fontMetrics.ascent)
            }

            updateValue(0)
        }
    }

    private var lastValue = -1
    override fun updateValue(currentValue: Int) {
        if (currentValue == lastValue) return
        withGraphics {
            lastValue = currentValue

            val fullSize = size * 2
            clearRect(x, startY, fullSize, fullSize)

            // rotate the pointer
            val clockAdjust = if (clockWise) 1 else -1
            val rotation = (lastValue % 360).toDouble().let { v ->
                Math.toRadians(v * clockAdjust)
            }
            val p = AffineTransform.getRotateInstance(rotation, centerX, centerY).createTransformedShape(pointer)
            draw(p)

            stroke = circleStroke
            drawArc(x, startY, fullSize, fullSize, startAngle, endAngle * -clockAdjust)

            // draws the value in a cleared box
            currentValue.toString().let { s ->
                clearRect(x, startY, fontMetrics.stringWidth(s) + lineWidth, fontMetrics.ascent + lineWidth)
                drawString(s, x, startY + fontMetrics.ascent)
            }
        }
    }
}
