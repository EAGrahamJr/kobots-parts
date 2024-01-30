package crackers.kobots.mqtt.homeassistant

import org.json.JSONObject

/**
 * A "select" entity allows for a device to react to explicit "commands". This conforms to the
 * [MQTT Select](https://www.home-assistant.io/integrations/select.mqtt)
 */
class KobotSelect(
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
