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

import crackers.kobots.devices.io.NeoKey
import crackers.kobots.devices.lighting.PixelBuf
import crackers.kobots.devices.lighting.WS2811.PixelColor
import org.slf4j.LoggerFactory
import java.awt.Color

/**
 * Handles getting button presses from a NeoKey1x4, along with setting pretty colors, etc. Performs a single
 * "debounce" by only reporting a button press if it's different from the last read. This works well in a fairly
 * "tight" loop, but if you're doing other things in between, you may want to do your own debouncing.
 *
 * **THIS CLASS IS NOT THREAD-SAFE!** It is not intended to be used across multiple threads.
 *
 * @param keyboard the NeoKey1x4 to use
 * @param activationColor the color to use when a button is pressed
 * @param initialColors the initial set of colors to use when a button is _not_ pressed (default blue, green, cyan, red)
 * @param initialBrightness the initial brightness of the keyboard (default 0.05)
 *
 * TODO allow for multiplexing multiple NeoKey1x4s
 */
class NeoKeyHandler(
    val keyboard: NeoKey = NeoKey(),
    val activationColor: Color = Color.YELLOW,
    initialColors: List<Color> = listOf(Color.BLUE, Color.GREEN, Color.CYAN, Color.RED),
    initialBrightness: Float = 0.05f
) : AutoCloseable {

    private var _colors: List<Color> = initialColors.toList()

    init {
        keyboard.brightness = initialBrightness
        updateButtonColors()
    }

    override fun close() {
        keyboard.close()
    }

    /**
     * TODO allow for multiplexing multiple NeoKey1x4s
     */
    private val SINGLE_KEYBOARD = 4

    /**
     * The number of buttons handled.
     */
    val numberOfButtons: Int
        get() = SINGLE_KEYBOARD

    private val NO_BUTTONS = BooleanArray(numberOfButtons) { false }.toList()
    protected var lastButtonValues = NO_BUTTONS

    /**
     * The current brightness of _all_ the buttons on the keyboard (get/set).
     */
    var brightness: Float
        get() = keyboard.brightness
        set(value) {
            keyboard.brightness = value
            updateButtonColors(true)
        }

    /**
     * Get/set the colors of the buttons. There must be exactly [numberOfButtons] colors.
     */
    var buttonColors: List<Color>
        get() = _colors
        set(c) {
            require(c.size == numberOfButtons) { "Must have exactly $numberOfButtons colors" }
            _colors = c.toList()
            updateButtonColors(true)
        }

    /**
     * Reset the colors of the buttons to [buttonColors], only if necessary.
     */
    fun updateButtonColors(force: Boolean = false) {
        val newColors: List<PixelColor> = buttonColors.map { PixelColor(it, brightness = brightness) }
        val colors: List<PixelColor> = keyboard.colors()
        if (newColors != colors || force) {
            keyboard[0, numberOfButtons - 1] = newColors
        }
    }

    private val logger by lazy { LoggerFactory.getLogger("NeoKeyHandler") }

    /**
     * The default button callback. This sets the activation color for any buttons that are pressed and returns to
     * the default color when the button is released.
     */
    val DEFAULT_BUTTON_CALLBACK = { buttons: List<Boolean>, pixels: PixelBuf ->
        buttons.forEachIndexed { index, b ->
            pixels[index] = PixelColor(if (b) activationColor else buttonColors[index], brightness = brightness)
        }
    }

    /**
     * Over-ride this to do something with the button values, otherwise the [DEFAULT_BUTTON_CALLBACK] is used.
     */
    var buttonColorCallback: (List<Boolean>, PixelBuf) -> Unit = DEFAULT_BUTTON_CALLBACK

    /**
     * Read the current button states and returns when the values change (this is a limited debounce, effectively
     * only reporting a "button down").
     */
    fun read(): List<Boolean> = try {
        keyboard.read().let { read ->
            // nothing changed
            if (read == lastButtonValues) {
                NO_BUTTONS
            } else {
                lastButtonValues = read
                buttonColorCallback(lastButtonValues, keyboard.pixels)
                read
            }
        }
    } catch (e: Exception) {
        logger.error("Error reading keyboard", e)
        NO_BUTTONS
    }
}
