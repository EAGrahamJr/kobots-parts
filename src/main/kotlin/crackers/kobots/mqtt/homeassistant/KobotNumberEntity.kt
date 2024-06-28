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

import org.json.JSONObject

/**
 * HA has an easy way to "send" numbers as commands: this aligns really well with things like `Rotator` and
 * `LinearActuator` devices.
 *
 * There are quite a few UI hints that can be set on these objects; defaults are set in all cases.
 */
open class KobotNumberEntity(
    private val handler: NumberHandler,
    uniqueId: String,
    name: String,
    deviceIdentifier: DeviceIdentifier,
    private val deviceClass: NumericDevice = NumericDevice.NONE,
    private val min: Int = 1,
    private val max: Int = 100,
    private val mode: DisplayMode = DisplayMode.AUTO,
    private val step: Float = 1.0f,
    private val unitOfMeasurement: String? = null
) : CommandEntity(
    uniqueId,
    name,
    deviceIdentifier
) {

    override val component = "number"
    override val icon = "mdi:numeric"

    override fun discovery(): JSONObject = super.discovery().apply {
        deviceClass.addDiscovery(this, unitOfMeasurement)

        put("max", max)
        put("min", min)
        put("mode", mode.name.lowercase())
        put("step", step)
    }

    override fun currentState() = handler.currentState()?.toString() ?: "None"

    override fun handleCommand(payload: String) {
        handler.set(payload.toFloat())
    }

    companion object {
        interface NumberHandler {
            fun currentState(): Float?
            fun set(target: Float)
        }

        enum class DisplayMode {
            AUTO, BOX, SLIDER
        }

        enum class NumericDevice : DeviceClass {
            NONE,
            APPARENT_POWER,
            AQI,
            ATMOSPHERIC_PRESSURE,
            BATTERY,
            CARBON_MONOXIDE,
            CARBON_DIOXIDE,
            CURRENT,
            DATA_RATE,
            DATA_SIZE,
            DISTANCE,
            DURATION,
            ENERGY,
            ENERGY_STORAGE,
            FREQUENCY,
            GAS,
            HUMIDITY,
            ILLUMINANCE,
            IRRADIANCE,
            MOISTURE,
            MONETARY,
            NITROGEN_DIOXIDE,
            NITROGEN_MONOXIDE,
            NITROUS_OXIDE,
            OZONE,
            PH,
            PM1,
            PM10,
            PM25,
            POWER_FACTOR,
            POWER,
            PRECIPITATION,
            PRECIPITATION_INTENSITY,
            PRESSURE,
            REACTIVE_POWER,
            SIGNAL_STRENGTH,
            SOUND_PRESSURE,
            SPEED,
            SULPHUR_DIOXIDE,
            TEMPERATURE,
            VOLATILE_ORGANIC_COMPOUNDS,
            VOLATILE_ORGANIC_COMPOUNDS_PARTS,
            VOLTAGE,
            VOLUME,
            VOLUME_STORAGE,
            WATER,
            WEIGHT,
            WIND_SPEED
        }
    }
}
