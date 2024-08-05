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
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * Simple HomeAssistant "light" that controls a single pixel on a 1->n `PixelBuf` strand (e.g. a WS28xx LED). Note
 * that the effects **must** be in the context of a `PixelBuf` target.
 * TODO this should be just a PixelBufController with the start==end index
 */
class SinglePixelLightController(
    private val theStrand: PixelBuf,
    private val index: Int,
    private val effects: Map<String, PixelBuf.(index: Int) -> Any>? = null
) : LightController {

    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

    private var lastColor: WS2811.PixelColor = WS2811.PixelColor(Color.WHITE, brightness = 0.5f)
    override val lightEffects = effects?.keys?.sorted()
    override val controllerIcon = "mdi:lightbulb"
    private val currentEffect = AtomicReference<String>()

    // note: brightness for the kobot lights is 0-100
    override fun current(): LightState {
        val state = theStrand[index].color != Color.BLACK
        return LightState(
            state = state,
            brightness = if (!state) 0 else (lastColor.brightness!! * 100f).roundToInt(),
            color = lastColor.color,
            effect = currentEffect.get()
        )
    }

    override fun set(command: LightCommand) {
        if (!command.state) {
            theStrand[index] = Color.BLACK
            return
        }
        // if the last color was "off", use the stored color
        val currentColor = theStrand[index].let {
            if (it.color == Color.BLACK) lastColor else it
        }

        val cb = command.brightness?.let { it / 100f } ?: currentColor.brightness
        val color = command.color ?: currentColor.color
        lastColor = WS2811.PixelColor(color, brightness = cb)
        theStrand[index] = lastColor
    }

    override fun exec(effect: String) = CompletableFuture.runAsync {
        try {
            effects?.get(effect)?.invoke(theStrand, index)?.also { currentEffect.set(effect) }
        } catch (t: Throwable) {
            logger.error("Error executing effect $effect", t)
        }
    }.whenComplete { _, _ ->
        currentEffect.set(null)
    }
}

/**
 * Controls a full "strand" of `PixelBuf` (e.g. WS28xx LEDs)
 * TODO needs a start and end index
 */
class PixelBufController(
    private val theStrand: PixelBuf,
    private val effects: Map<String, PixelBuf.() -> Any>? = null
) : LightController {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    override val lightEffects = effects?.keys?.sorted()
    override val controllerIcon = "mdi:led-strip"

    private var lastColor: WS2811.PixelColor = WS2811.PixelColor(Color.WHITE, brightness = 0.5f)
    private val currentEffect = AtomicReference<String>()

    /**
     * Finds the first non-black color or the first pixel.
     */
    private fun currentColor() = theStrand.get().find { it.color != Color.BLACK } ?: theStrand[0]

    override fun set(command: LightCommand) {
        // just black it out, do not save this "color"
        if (!command.state) {
            theStrand fill Color.BLACK
            return
        }

        // need to be able to "resume" the last color set if it wasn't turned off
        val currentColor = currentColor().let { if (it.color == Color.BLACK) lastColor else it }
        val color = command.color ?: currentColor.color
        // if there's a brightness, otherwise "resume" again
        val cb = command.brightness?.let { it / 100f } ?: currentColor.brightness
        // save it, send it
        lastColor = WS2811.PixelColor(color, brightness = cb)
        theStrand fill lastColor
    }

    override fun current(): LightState {
        val state = theStrand.get().any { it.color != Color.BLACK }
        return LightState(
            state = state,
            brightness = if (!state) 0 else (lastColor.brightness!! * 100f).roundToInt(),
            color = lastColor.color,
            effect = currentEffect.get()
        )
    }

    override fun exec(effect: String) = CompletableFuture.runAsync {
        try {
            effects?.get(effect)?.invoke(theStrand)?.also { currentEffect.set(effect) }
        } catch (t: Throwable) {
            logger.error("Error executing effect $effect", t)
        }
    }.whenComplete { _, _ ->
        currentEffect.set(null)
    }
}
