package crackers.kobots.mqtt

import crackers.kobots.app.AppCommon.mqttClient
import crackers.kobots.mqtt.KobotDevice.Companion.KOBOTS_MQTT
import crackers.kobots.parts.app.KobotSleep
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What is this thing?
 */
data class DeviceIdentifier @JvmOverloads constructor(
    val manufacturer: String,
    val model: String,
    val icon: String = "mdi:devices",
    val identifer: String = InetAddress.getLocalHost().hostName
)

/**
 * Defines the "device" for MQTT discovery and state messages.
 */
interface KobotDevice : Comparable<KobotDevice> {
    /**
     * The unique ID of the device. **NOTE** If this is _not_ unique across all devices, then
     * HomeAssistant will throw an error on discovery, but it won't be seen here.
     */
    val uniqueId: String

    /**
     * The friendly name of the device. This may be renamed in the HomeAssistant UI.
     */
    val name: String

    /**
     * The Home Assistant classification of the device (e.g. light, switch, etc.)
     */
    val component: String

    /**
     * More detailed information about the device.
     */
    val deviceIdentifier: DeviceIdentifier

    /**
     * Generate the MQTT discovery message for this device.
     */
    fun discovery(): JSONObject

    /**
     * Generate the MQTT state message for this device.
     */
    fun currentState(): JSONObject

    /**
     * Handle the MQTT command message for this device.
     */
    fun handleCommand(payload: JSONObject)

    override fun compareTo(other: KobotDevice): Int = uniqueId.compareTo(other.uniqueId)

    companion object {
        const val KOBOTS_MQTT = "kobots_ha"
    }
}

/**
 * Abstraction for common attributes and methods for all Kobot devices for integration with Home Assistant via MQTT.
 * Because it's part of this package, it assumes usage of the [AppCommon.mqttClient] singleton.
 *
 * Devices may be removed from HomeAssistant at any time, so the discovery message is sent on every connection.
 */
abstract class AbstractKobotDevice(override val uniqueId: String, override val name: String) : KobotDevice {
    private val logger = LoggerFactory.getLogger(javaClass.simpleName)
    private val conntected = AtomicBoolean(false)
    protected val homeassistantAvailable: Boolean
        get() = conntected.get()

    /**
     * The MQTT topic for the device's state (send).
     */
    val statusTopic by lazy { "$KOBOTS_MQTT/$uniqueId/state" }

    /**
     * The MQTT topic for the device's command (receive).
     */
    val commandTopic by lazy { "$KOBOTS_MQTT/$uniqueId/set" }

    /**
     * A generic configuration for the device, which can be used to generate the discovery message
     * because there are too many "base" configuration parameters to be able to handle them cleanly,
     * so just dump it on the child class to figure it out.
     */
    val baseConfiguration by lazy {
        val deviceId = JSONObject().apply {
            put("identifiers", listOf(uniqueId, deviceIdentifier.identifer))
            put("name", if (name.isNotBlank()) name else uniqueId)
            put("model", deviceIdentifier.model)
            put("manufacturer", deviceIdentifier.manufacturer)
        }

        JSONObject().apply {
            put("command_topic", commandTopic)
            put("device", deviceId)
            put("entity_category", "config")
            put("icon", deviceIdentifier.icon)
            if (name.isNotBlank()) put("name", name)
            put("schema", "json")
            put("state_topic", statusTopic)
            put("unique_id", uniqueId)
        }
    }

    fun start() = with(mqttClient) {
        // add a (re-)connect listener to the MQTT client to send state`when the connection is re-established
        addConnectListener(object : KobotsMQTT.ConnectListener {
            override fun onConnect(reconnect: Boolean) {
                redoConnection()
            }

            override fun onDisconnect() {
                conntected.set(false)
            }
        })
        // subscribe to the command topic (expecting JSON payload)
        subscribeJSON(commandTopic, ::handleCommand)
        // subscribe to the HA LWT topic and send discovery on "online" status
        subscribe("homeassistant/status") { message ->
            if (message == "online") redoConnection() else conntected.set(false)
        }
    }

    /**
     * Re-do the connection to HomeAssistant, which means sending the discovery message and the current state.
     */
    protected open fun redoConnection() {
        conntected.set(true)
        sendDiscovery()
        logger.info("Waiting 2 seconds for discovery to be processed")
        KobotSleep.seconds(2)
        sendCurrentState()
    }

    /**
     * Send the discovery message for this device.
     */
    protected open fun sendDiscovery(discoveryPayload: JSONObject = discovery()) {
        if (homeassistantAvailable) {
            logger.info("Sending discovery for $uniqueId")
            mqttClient["homeassistant/$component/$uniqueId/config"] = discoveryPayload
        }
    }

    /**
     * Send the current state message for this device.
     */
    protected open fun sendCurrentState(state: JSONObject = currentState()) {
        if (homeassistantAvailable) mqttClient[statusTopic] = state
    }
}
