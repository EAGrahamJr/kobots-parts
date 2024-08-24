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

import crackers.kobots.mqtt.homeassistant.LightColor.Companion.toLightColor
import crackers.kobots.mqtt.homeassistant.LightCommand.Companion.commandFrom
import crackers.kobots.parts.miredsToColor
import crackers.kobots.parts.toMireds
import org.json.JSONObject
import java.awt.Color
import java.awt.Color.BLACK
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
    val color: Color = BLACK,
    val effect: String? = null
) {

    fun json(): JSONObject {
        return JSONObject().apply {
            put("color", JSONObject(color.toLightColor()))
            put("state", if (state) "ON" else "OFF")
            put("brightness", (brightness * 255f / 100f).roundToInt())
            put("color_mode", LightColorMode.RGB.name.lowercase())
            put("color_temp", color.toMireds())
            effect?.let { e -> put("effect", e) }
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
    val effect: String?,
    val flash: Int,
    val transition: Float
) {
    companion object {
        fun JSONObject.commandFrom(): LightCommand = with(this) {
            var state = optString("state", null)?.let { it == "ON" } ?: false
            // extract the effect: if no effect, see if there's transition or flash
            val effect = optString("effect", null)
            val flash = if (effect == null) optInt("flash", 0) else 0
            val trans = if (effect == null && flash != 0) optFloat("transition", 0f) else 0f

            // brightness is 0-255, so translate to 0-100
            val brightness = takeIf { has("brightness") }?.let { getInt("brightness") * 100f / 255f }?.roundToInt()

            // this is in mireds, so we need to convert to Kelvin
            val colorTemp = takeIf { has("color_temp") }?.let { getInt("color_temp").miredsToColor() }

            val color = optJSONObject("color")?.let {
                Color(it.optInt("r"), it.optInt("g"), it.optInt("b"))
            } ?: colorTemp

            // set state regardless
            if (brightness != null || color != null) state = true

            LightCommand(state, brightness, color, effect, flash, trans)
        }
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
    deviceIdentifier: DeviceIdentifier
) :
    CommandEntity(uniqueId, name, deviceIdentifier) {

    final override val component: String = "light"
    override val icon = controller.controllerIcon

    override fun discovery() = super.discovery().apply {
        put("brightness", true)
        controller.lightEffects?.let {
            put("effect", true)
            put("effect_list", it.sorted())
        }
    }

    override fun currentState() = controller.current().json().toString()

    override fun handleCommand(payload: String) {
        val cmd = JSONObject(payload).commandFrom()
        // split out specialized support mechanisms for background managements
        when {
            !cmd.state -> controller set cmd    // off over-rides anything
            cmd.effect != null -> controller exec cmd.effect
            cmd.flash > 0 -> controller flash cmd.flash
            cmd.transition > 0f -> controller transition cmd
            else -> controller set cmd
        }
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
    deviceIdentifier: DeviceIdentifier
) : KobotLight(uniqueId, controller, name, deviceIdentifier) {

    override fun discovery() = super.discovery().apply {
        put("supported_color_modes", LightColorMode.entries.map { it.name.lowercase() })
    }
}
