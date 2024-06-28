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

import org.json.JSONObject

/**
 * A "select" entity allows for a device to react to explicit "commands". This conforms to the
 * [MQTT Select](https://www.home-assistant.io/integrations/select.mqtt)
 */
open class KobotSelectEntity(
    val selectHandler: SelectHandler,
    uniqueId: String,
    name: String,
    deviceIdentifier: DeviceIdentifier
) : CommandEntity(
    uniqueId,
    name,
    deviceIdentifier
) {

    override val component = "select"
    override val icon = "mdi:list-status"

    override fun discovery(): JSONObject = super.discovery().put("options", selectHandler.options)

    private lateinit var lastOption: String

    override fun currentState() = if (::lastOption.isInitialized) lastOption else "None"

    override fun handleCommand(payload: String) {
        selectHandler.executeOption(payload)
        lastOption = payload
    }

    companion object {
        interface SelectHandler {
            val options: List<String>
            fun executeOption(select: String)
        }
    }
}
