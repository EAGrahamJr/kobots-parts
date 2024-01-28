package crackers.kobots.mqtt.homeassistant

import com.diozero.api.DigitalOutputDevice
import crackers.kobots.app.AppCommon
import org.json.JSONObject

/**
 * On/off.
 */
open class KobotSwitch(
    val device: OnOffDevice,
    uniqueId: String,
    name: String,
    deviceIdentifier: DeviceIdentifier = defaultDeviceIdentifier(device)
) : CommandEntity(uniqueId, name, deviceIdentifier) {
    override val component = "switch"

    override fun discovery() = super.discovery().put("optimistic", false)

    override fun currentState() = JSONObject()

    override fun sendCurrentState(state: JSONObject) {
        if (homeassistantAvailable) AppCommon.mqttClient[statusTopic] = if (device.isOn) "ON" else "OFF"
    }

    /**
     * Only understands "ON" and "not ON"
     */
    override fun handleCommand(payload: String) {
        device.isOn = payload == ON_CMD
        sendCurrentState()
    }

    companion object {
        val ON_CMD = "ON"

        /**
         * Defines the simple on/off thingie.
         */
        interface OnOffDevice {
            var isOn: Boolean
            val name: String
        }

        /**
         * Create the identifier from the device.
         */
        fun defaultDeviceIdentifier(device: OnOffDevice) =
            DeviceIdentifier("Kobots", device.name, "mdi:switch")

        /**
         * Ibid
         */
        val NOOP_DEVICE = object : OnOffDevice {
            private var on = false
            override var isOn: Boolean
                get() = on
                set(v) {
                    on = v
                }
            override val name: String
                get() = "No Op Switch"
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
