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

import com.diozero.api.PwmOutputDevice
import kotlin.math.roundToInt

/**
 * Interface for controlling a light. This is used by [KobotLight] to abstract the actual hardware implementation.
 *
 * The [exec], [flash], and [transition] functions are specifically called out since they will _usually_ involve some
 * background tasking to work with this system.
 */
interface LightController {
    /**
     * Set the state of the light.
     */
    infix fun set(command: LightCommand)

    /**
     * Execute an effect in some sort of completable manner. Note this **must** be cancellable by any subsequent
     * commands.
     */
    infix fun exec(effect: String) {
        // does nothing
    }

    /**
     * Flash the light (on/off) using this period (seconds). Continues flashing until another command is received.
     *
     * **NOTE** HA is only currently sending either 10 or 2 to signal fast/slow.
     */
    infix fun flash(flash: Int) {
        // does nothing
    }

    /**
     * Transition from the current state to a new state. Because this _can_ include color and brightness changes, the
     * whole parsed command is necessary to complete this function.
     */
    infix fun transition(command: LightCommand) {
        // does nothing
    }

    /**
     * Get the state of the light.
     */
    fun current(): LightState

    val controllerIcon: String

    val lightEffects: List<String>?
}

/**
 * Turns it on and off, adjust brightness.
 *
 * TODO support a minimal set of effects? should support fade-in/out as HA settings?
 */
class BasicLightController(val device: PwmOutputDevice) : LightController {
    private var currentEffect: String? = null
    override val lightEffects: List<String>? = null

    override fun set(command: LightCommand) = with(command) {
        // off over-rides everything
        device.value = when {
            !state -> 0f
            brightness != null -> brightness / 100f
            else -> 1f
        }
    }

    override fun current() = LightState(
        device.isOn,
        brightness = (device.value * 100f).roundToInt(),
        effect = currentEffect
    )

    override val controllerIcon = "mdi:lamp"
}
