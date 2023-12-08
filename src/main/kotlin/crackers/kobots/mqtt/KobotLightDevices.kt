package crackers.kobots.mqtt

import crackers.kobots.mqtt.LightColor.Companion.toLightColor
import crackers.kobots.mqtt.LightCommand.Companion.commandFrom
import crackers.kobots.parts.kelvinToRGB
import org.json.JSONObject
import java.awt.Color
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
    val state: Boolean,
    val brightness: Int?,
    val color: Color?,
    val effect: String?
) {
    companion object {
        fun JSONObject.commandFrom(): LightCommand = with(this) {
            val state = optString("state", "OFF") == "ON"
            val effect = optString("effect").takeIf { it.isNotBlank() }

            // brightness is 0-255, so translate to 0-100
            val brightness = optInt("brightness", -1).takeIf { it >= 0 }?.let { it * 100f / 255f }?.roundToInt()

            // this is in mireds, so we need to convert to Kelvin
            val colorTemp = optInt("color_temp", -1).takeIf { it >= 0 }?.let { 1000000 / it }

            val color = optJSONObject("color")?.let {
                Color(it.optInt("r", 0), it.optInt("g", 0), it.optInt("b", 0))
            } ?: colorTemp?.kelvinToRGB()
            LightCommand(state, brightness, color, effect)
        }
    }
}

/**
 * Interface for controlling a light. This is used by [KobotLight] and [KobotLightStrip] to abstract the actual
 * hardware implementation.
 */
interface LightController {
    /**
     * Set the state of the light. Node "0" represents either a single LED or a full LED strand. Non-zero indices are
     * to address each LED individually, offset by 1.
     */
    infix fun set(command: LightCommand)

    /**
     * Get the state of the light. Node "0" represents either a single LED or a full LED strand. Non-zero indices are
     * to address each LED individually, offset by 1.
     */
    fun current(): LightState
}

/**
 * Stubbed out implementation of [LightController] that does nothing. This is useful for testing, but not much else.
 */
val NOOP_CONTROLLER = object : LightController {
    private var current: LightState = LightState()

    override fun set(command: LightCommand) {
        println("LightState: $command")
        current = LightState(
            state = command.state,
            brightness = command.brightness ?: current.brightness,
            color = command.color?.toLightColor() ?: current.color,
            effect = command.effect ?: current.effect
        )
    }

    override fun current(): LightState = current
}

/**
 * Describes an RGB light. This conforms to the Home Assistant MQTT
 * [light spec](https://www.home-assistant.io/integrations/light.mqtt) and allows for a list of "effects" that can be
 * selected via MQTT.
 */
open class KobotLight(
    uniqueId: String,
    private val controller: LightController,
    name: String = "",
    val lightEffects: List<String>? = null
) :
    AbstractKobotDevice(uniqueId, name) {

    override val component = "light"
    override val deviceIdentifier = DeviceIdentifier("Kobots", "KobotLight", "mdi:lightbulb")

    override fun discovery(): JSONObject {
        return baseConfiguration.apply {
            put("brightness", true)
            put("color_mode", true)
            put("supported_color_modes", LightColorMode.entries.map { it.name.lowercase() })
            if (lightEffects != null) {
                put("effect", true)
                put("effect_list", lightEffects)
            }
        }
    }

    override fun currentState() = controller.current().json()

    override fun handleCommand(payload: JSONObject) {
        val command = payload.commandFrom()
        controller set command
        sendCurrentState()
    }
}
