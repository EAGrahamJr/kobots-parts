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

import java.util.concurrent.atomic.AtomicReference

/**
 * Handles simple text messages.
 */
open class KobotTextEntity(
    val textHandler: (String) -> Unit,
    uniqueId: String,
    name: String,
    deviceIdentifier: DeviceIdentifier
) : CommandEntity(uniqueId, name, deviceIdentifier) {

    override val component = "text"
    override val icon = "mdi:text"

    private val currentText = AtomicReference("")

    override fun currentState() = currentText.get()

    override fun handleCommand(payload: String) {
        textHandler(payload)
        currentText.set(payload)
    }
}
