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

import crackers.kobots.devices.lighting.PimoroniLEDShim
import crackers.kobots.parts.scale
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Controller for a light device backed by a
 * [Pimoroni LED Shim](https://shop.pimoroni.com/products/led-shim?variant=3136952467466) on a Raspberry Pi.
 */
class PimoroniShimController(
    private val device: PimoroniLEDShim,
    private val effects: Set<LightEffector<PimoroniLEDShim>> = emptySet()
) : LightController {
    private val currentColor = AtomicReference(Color.BLACK)
    private val currentBrightness = AtomicInteger(0)
    private val currentState = AtomicBoolean(false)
    private val currentEffect = AtomicReference<LightEffector<PimoroniLEDShim>>()

    override val controllerIcon = "mdi:led-strip"
    override val lightEffects = effects.map { it.name }.sorted()

    override fun set(command: LightCommand) = with(command) {
        val runningEffect = currentEffect.getAndSet(null)?.let {
            it.stop()
            true
        } ?: false

        if (state == false || runningEffect) {
            device.sleep(true)
            currentState.set(false)
        } else {
            device.sleep(false)
            currentState.set(true)
            // if the command color is not null, use it, otherwise use the current color
            var newColor = command.color ?: currentColor.get()
            // if the color is BLACK, use WHITE because that's the only way to turn it on
            if (newColor == Color.BLACK) newColor = Color.WHITE

            // if the brightness is not null, use it, otherwise use the current brightness
            // if less than or equal to 0, use 100
            var newBrightness = command.brightness ?: currentBrightness.get()
            if (newBrightness <= 0) newBrightness = 100

            currentColor.set(newColor)
            currentBrightness.set(newBrightness)
            val scaledColor = newColor.scale(newBrightness)
            device.setAll(scaledColor)
        }
    }

    override fun exec(effect: String) {
        effects.firstOrNull { it.name == effect }?.let {
            it start device
            currentEffect.set(it)
        }
    }

    override fun current(): LightState {
        val runningEffect = currentEffect.get()
        return LightState(
            state = currentState.get() || runningEffect != null,
            brightness = currentBrightness.get(),
            color = currentColor.get(),
            effect = runningEffect?.name
        )
    }
}
