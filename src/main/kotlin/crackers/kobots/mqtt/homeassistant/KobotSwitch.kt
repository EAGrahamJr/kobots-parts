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
