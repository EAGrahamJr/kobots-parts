package crackers.kobots.mqtt.homeassistant

import java.util.concurrent.atomic.AtomicReference

/**
 * Handles simple text messages.
 */
class KobotTextEntity(
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
