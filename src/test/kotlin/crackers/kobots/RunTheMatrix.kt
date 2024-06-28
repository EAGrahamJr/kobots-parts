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

package crackers.kobots

import crackers.kobots.graphics.animation.MatrixRain
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel

/**
 * Runs the matrix rain in a simple window.
 */
fun main() {
    val frame = JFrame("Matrix Rain").apply {
        isResizable = true
        size = Dimension(800, 400)
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        layout = BorderLayout()
    }

    val image = BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB)
    val label = JLabel(ImageIcon(image))
    frame.add(label, BorderLayout.CENTER)
    frame.isVisible = true
    MatrixRain(
        image.graphics as Graphics2D,
        0,
        0,
        800,
        400,
        displayFont = Font(Font.SANS_SERIF, Font.PLAIN, 8),
        useBold = false
    ).apply {
        start(frame::repaint)
    }
}
