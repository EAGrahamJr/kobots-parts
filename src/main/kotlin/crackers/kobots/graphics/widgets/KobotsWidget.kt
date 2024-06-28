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

import java.awt.BasicStroke
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle

/**
 * Shows some graphics things in a defined region.
 *
 * TODO add color
 */
interface KobotsWidget<T> {
    val graphics: Graphics2D
    val x: Int
    val y: Int
    val labelFont: Font
    val label: String?
    val lineWidth: Int

    /**
     * How much space this occupies.
     */
    val bounds: Rectangle

    /**
     * Draw any "static" parts that don't get updated.
     */
    fun drawStatic()

    /**
     * Update the display to the current value. **THIS IS NOT GUARANTEED TO BE THREAD-SAFE!!!**
     */
    fun updateValue(currentValue: T)

    /**
     * Makes drawing fun.
     */
    fun withGraphics(block: Graphics2D.() -> Unit) = with(graphics) {
        val ogClip = clip
        try {
            clip = bounds
            font = labelFont
            stroke = BasicStroke(lineWidth.toFloat())
            block()
        } catch (t: Throwable) {
            t.printStackTrace()
        } finally {
            clip = ogClip
        }
    }
}
