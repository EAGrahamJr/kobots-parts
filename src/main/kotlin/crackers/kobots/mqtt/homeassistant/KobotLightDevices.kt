package crackers.kobots.mqtt.homeassistant

import crackers.kobots.mqtt.homeassistant.LightColor.Companion.toLightColor
import crackers.kobots.mqtt.homeassistant.LightCommand.Companion.commandFrom
import crackers.kobots.parts.kelvinToRGB
import org.json.JSONObject
import java.awt.Color
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/*
 * Classes and definitions to handle RGB(W) lights via Kobots and Diozero via MQTT and Home Assistant.
 */

enum class LightColorMode {
    COLOR_TEMP,
    RGB
    // TODO RGBW
}

/**
 * Full description of the HA light color.
 */
data class LightColor(
    val r: Int = 0,
    val g: Int = 0,
    val b: Int = 0,
    val c: Int = 0,
    val w: Int = 0,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val h: Double = 0.0,
    val s: Double = 0.0
) {
    fun toColor(): Color = Color(r, g, b)

    companion object {
        fun Color.toLightColor(): LightColor = LightColor(r = red, g = green, b = blue)
    }
}

/**
 * The current "state" of a light to be sent back to Home Assistant.
 */
data class LightState(
    val state: Boolean = false,
    val brightness: Int = 0,
    val color: LightColor = Color.BLACK.toLightColor(),
    val effect: String? = null
) {

    fun json(): JSONObject {
        return JSONObject(this).apply {
            put("state", if (state) "ON" else "OFF")
            put("brightness", (brightness * 255f / 100f).roundToInt())
            put("color_mode", LightColorMode.RGB.name.lowercase())
        }
    }
}

/**
 * The command payload received from Home Assistant.
 *
 * Examples of commands:
 * ```
 * {"state":"ON","color_temp":230}
 * {"state":"ON","color":{"r":255,"g":41,"b":38}}
 * {"state":"ON","effect":"effect2"}
 * ```
 */
data class LightCommand(
    val state: Boolean?,
    val brightness: Int?,
    val color: Color?,
    val effect: String?
) {
    companion object {
        fun JSONObject.commandFrom(): LightCommand = with(this) {
            val state = optString("state", null)?.let { it == "ON" }
            val effect = optString("effect", null)

            // brightness is 0-255, so translate to 0-100
            val brightness = optInteger("brightness")?.let { it * 100f / 255f }?.roundToInt()

            // this is in mireds, so we need to convert to Kelvin
            val colorTemp = optInteger("color_temp")?.let { 1000000f / it }?.roundToInt()

            val color = optJSONObject("color")?.let {
                Color(it.optInt("r"), it.optInt("g"), it.optInt("b"))
            } ?: colorTemp?.kelvinToRGB()
            LightCommand(state, brightness, color, effect)
        }

        private fun JSONObject.optInteger(key: String) = optIntegerObject(key, null)
    }
}

/**
 * Describes a "simple" light. This conforms to the Home Assistant MQTT
 * [light spec](https://www.home-assistant.io/integrations/light.mqtt) and allows for a list of "effects" that can be
 * selected via MQTT.
 */
open class KobotLight(
    uniqueId: String,
    private val controller: LightController,
    name: String,
    deviceIdentifier: DeviceIdentifier = defaultDeviceIdentifier(controller)
) :
    CommandEntity(uniqueId, name, deviceIdentifier) {

    final override val component: String = "light"
    override fun discovery() = super.discovery().apply {
        put("brightness", true)
        controller.lightEffects?.let {
            put("effect", true)
            put("effect_list", it.sorted())
        }
    }

    override fun currentState() = controller.current().json()

    private val effectFuture = AtomicReference<CompletableFuture<Void>>()
    private var theFuture: CompletableFuture<Void>? // for pretty
        get() = effectFuture.get()
        set(v) {
            effectFuture.set(v)
        }

    override fun handleCommand(payload: String) = with(JSONObject(payload).commandFrom()) {
        // stop any running effect
        theFuture?.cancel(true)

        if (effect != null) {
            theFuture = controller.lightEffects?.takeIf { effect in it }?.let { controller exec effect }
        } else {
            controller set this
        }
        sendCurrentState()
    }

    companion object {
        /**
         * Creates a default identifier based on the controller.
         */
        fun defaultDeviceIdentifier(controller: LightController) =
            DeviceIdentifier("Kobots", controller.javaClass.simpleName, controller.controllerIcon)
    }
}

/**
 * Describes an RGB light. This conforms to the Home Assistant MQTT
 * [light spec](https://www.home-assistant.io/integrations/light.mqtt) and allows for a list of "effects" that can be
 * selected via MQTT.
 */
open class KobotRGBLight(
    uniqueId: String,
    controller: LightController,
    name: String,
    deviceIdentifier: DeviceIdentifier = defaultDeviceIdentifier(controller)
) : KobotLight(uniqueId, controller, name, deviceIdentifier) {

    override fun discovery() = super.discovery().apply {
        put("color_mode", true)
        put("supported_color_modes", LightColorMode.entries.map { it.name.lowercase() })
    }
}
