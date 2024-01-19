package crackers.kobots.mqtt.homeassistant

import crackers.kobots.app.AppCommon.mqttClient
import crackers.kobots.mqtt.KobotsMQTT
import crackers.kobots.mqtt.homeassistant.KobotDevice.Companion.KOBOTS_MQTT
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
     *
     * **NOTE** This should be modified by each entity for it's special cases.
     */
    fun discovery(): JSONObject

    /**
     * Generate the MQTT state message for this device.
     */
    fun currentState(): JSONObject

    override fun compareTo(other: KobotDevice): Int = uniqueId.compareTo(other.uniqueId)

    companion object {
        const val KOBOTS_MQTT = "kobots_ha"
    }
}

/**
 * Abstraction for common attributes and methods for all Kobot devices for integration with Home Assistant via MQTT.
 * Because it's part of this package, it assumes usage of the [AppCommon.mqttClient] singleton.
 *
 * Devices may be removed from HomeAssistant at any time, so the discovery message is sent on every connection. Birth
 * and last-will messages are **not** used because the client can be used for other things besides HA.
 *
 * **Note** the [uniqueId] and [name] properties are the name of the _entity_, not the device (see
 * [DeviceIdentifier] -- a device may have several entities). The _entityId_ in Home Assistant will be constructed from
 * the category, uniqueId, and name -- e.g. `light.sparkle_night_light`.
 */
abstract class AbstractKobotDevice(final override val uniqueId: String, final override val name: String) : KobotDevice {
    init {
        require(uniqueId.isNotBlank()) { "'uniqeId' must not be blank." }
        require(name.isNotBlank()) { "'name' must not be blank." }
    }

    private val logger = LoggerFactory.getLogger(javaClass.simpleName)
    private val connected = AtomicBoolean(false)
    protected val homeassistantAvailable: Boolean
        get() = connected.get()

    /**
     * The MQTT topic for the device's state (send).
     */
    val statusTopic = "$KOBOTS_MQTT/$uniqueId/state"

    /**
     * A generic configuration for the device, which can be used to generate the discovery message
     * because there are too many "base" configuration parameters to be able to handle them cleanly,
     * so just dump it on the child class to figure it out.
     */
    override fun discovery() = JSONObject().apply {
        val deviceId = JSONObject()
            .put("identifiers", listOf(uniqueId, deviceIdentifier.identifer))
            .put("name", deviceIdentifier.identifer)
            .put("model", deviceIdentifier.model)
            .put("manufacturer", deviceIdentifier.manufacturer)

        put("device", deviceId)
        put("entity_category", "config")
        put("icon", deviceIdentifier.icon)
        put("name", name)
        put("schema", "json")
        put("state_topic", statusTopic)
        put("unique_id", uniqueId)
    }

    open fun start() = with(mqttClient) {
        // add a (re-)connect listener to the MQTT client to send state`when the connection is re-established
        addConnectListener(object : KobotsMQTT.ConnectListener {
            override fun onConnect(reconnect: Boolean) {
                redoConnection()
            }

            override fun onDisconnect() {
                connected.set(false)
            }
        })
        // subscribe to the HA LWT topic and send discovery on "online" status
        subscribe("homeassistant/status") { message ->
            if (message == "online") redoConnection() else connected.set(false)
        }
    }

    /**
     * Re-do the connection to HomeAssistant, which means sending the discovery message and the current state.
     */
    protected open fun redoConnection() {
        connected.set(true)
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

abstract class CommandDevice(uniqueId: String, name: String) : AbstractKobotDevice(uniqueId, name) {
    /**
     * The MQTT topic for the device's command (receive).
     */
    val commandTopic = "$KOBOTS_MQTT/$uniqueId/set"

    /**
     * Handle the MQTT command message for this device.
     */
    abstract fun handleCommand(payload: JSONObject)

    /**
     * Over-ride the base class to add a subscription to handle commands.
     */
    override fun start() {
        super.start()
        // subscribe to the command topic (expecting JSON payload)
        mqttClient.subscribeJSON(commandTopic, ::handleCommand)
    }

    override fun discovery() = super.discovery().put("command_topic", commandTopic)
}
