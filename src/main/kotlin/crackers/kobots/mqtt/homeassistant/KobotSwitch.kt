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

import com.diozero.api.DigitalOutputDevice

/**
 * On/off.
 */
open class KobotSwitch(
    val device: OnOffDevice,
    uniqueId: String,
    name: String,
    deviceIdentifier: DeviceIdentifier
) : CommandEntity(uniqueId, name, deviceIdentifier) {
    override val component = "switch"
    override val icon = "mdi:light-switch"

    override fun discovery() = super.discovery().apply {
        put("optimistic", false)
    }

    override fun currentState() = if (device.isOn) "ON" else "OFF"

    /**
     * Only understands "ON" and "not ON"
     */
    override fun handleCommand(payload: String) {
        device.isOn = payload == ON_CMD
    }

    companion object {
        const val ON_CMD = "ON"

        /**
         * Defines the simple on/off thingie.
         */
        interface OnOffDevice {
            var isOn: Boolean
            val name: String
        }

        /**
         * Create an on/off switch using a Diozero digital thingie.
         */
        fun digitalDevice(d: DigitalOutputDevice) = object : OnOffDevice {
            override var isOn: Boolean
                get() = d.isOn
                set(v) {
                    d.isOn = v
                }
            override val name = "Diozero DigitalOutput"
        }
    }
}
