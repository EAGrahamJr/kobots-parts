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

package crackers.kobots.mqtt.homeassistant

import crackers.kobots.devices.lighting.PixelBuf
import crackers.kobots.devices.lighting.WS2811
import java.awt.Color
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * Interface for light effectors that can be applied to a `PixelBuf` device.
 * The parameters are: the `PixelBuf`, the starting index, and the number of pixels.
 */
interface PixelBufEffector : LightEffector<Triple<PixelBuf, Int, Int>>

/**
 * Simple HomeAssistant "light" that controls a single pixel on a 1->n `PixelBuf` strand (e.g. a WS28xx LED). Note
 * that the effects **must** be in the context of a `PixelBuf` target.
 */
@Deprecated(replaceWith = ReplaceWith("PixelBufController"), message = "Use PixelBufController with count=1")
open class SinglePixelLightController(
    theStrand: PixelBuf,
    index: Int,
    effects: Set<PixelBufEffector> = emptySet()
) : PixelBufController(theStrand, effects, index, 1)

/**
 * Controls all or part of a "strand" of `PixelBuf` (e.g. WS28xx LEDs)
 */
open class PixelBufController(
    private val theStrand: PixelBuf,
    private val effects: Set<LightEffector<Triple<PixelBuf, Int, Int>>> = emptySet(),
    private val offset: Int = 0,
    private val count: Int = theStrand.size
) : LightController {
    override val lightEffects = effects.map { it.name }.sorted()
    override val controllerIcon = "mdi:led-strip"

    private val offColor = WS2811.PixelColor(Color.BLACK, brightness = 0f)
    private var lastColor: WS2811.PixelColor = offColor
    private val currentEffect = AtomicReference<LightEffector<Triple<PixelBuf, Int, Int>>>()

    // because the end is inclusive
    private val endPixel = count + offset - 1

    /**
     * Finds the first non-black color or the first pixel.
     */
    private fun currentColor() = theStrand.get().find { it.color != Color.BLACK } ?: theStrand[0]

    override fun set(command: LightCommand) {
        // just black it out, do not save this "color"
        if (!command.state) {
            theStrand[offset, endPixel] = Color.BLACK
            return
        }

        // need to be able to "resume" the last color set if it wasn't turned off
        val currentColor = currentColor().let { if (it.color == Color.BLACK) lastColor else it }
        val color = command.color ?: currentColor.color
        // if there's a brightness, otherwise "resume" again
        val cb = command.brightness?.let { it / 100f } ?: currentColor.brightness
        // save it, send it
        lastColor = WS2811.PixelColor(color, brightness = cb)
        theStrand[offset, endPixel] = lastColor
    }

    override fun current(): LightState {
        val effectInEffect = currentEffect.get()
        val state = (offset..endPixel).any { theStrand[it].color != Color.BLACK } || effectInEffect != null

        return LightState(
            state = state,
            brightness = if (!state) 0 else (lastColor.brightness!! * 100f).roundToInt(),
            color = lastColor.color,
            effect = effectInEffect?.name
        )
    }
}
