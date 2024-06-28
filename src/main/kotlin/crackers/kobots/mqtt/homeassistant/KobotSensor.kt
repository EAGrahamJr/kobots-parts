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

import crackers.kobots.mqtt.homeassistant.KobotAnalogSensor.Companion.AnalogDevice
import crackers.kobots.mqtt.homeassistant.KobotBinarySensor.Companion.BinaryDevice
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Basic sensor stuff. Updates are controlled by setting the sensor's current state
 */
abstract class KobotSensor<M : DeviceClass, T>(
    uniqueId: String,
    name: String,
    deviceIdentifier: DeviceIdentifier,
    private val expires: Duration,
    val deviceClass: M
) : AbstractKobotEntity(uniqueId, name, deviceIdentifier) {

    override fun currentState() = sensorState.get() ?: ""

    protected val sensorState = AtomicReference<String>()

    abstract var currentState: T

    override fun discovery() = super.discovery().apply {
        put("entity_category", "diagnostic")
        if (expires > 1.seconds) put("expire_after", expires.inWholeSeconds)
    }
}

/**
 * On/off sensor, single state. If the [deviceClass] is set, Home Assistant will pick the appropriate "type" of
 * binary sensor.
 */
open class KobotBinarySensor(
    uniqueId: String,
    name: String,
    deviceIdentifier: DeviceIdentifier,
    deviceClass: BinaryDevice = BinaryDevice.NONE,
    expires: Duration = Duration.ZERO,
    val offDelay: Duration = Duration.ZERO
) : KobotSensor<BinaryDevice, Boolean>(uniqueId, name, deviceIdentifier, expires, deviceClass) {

    // do not allow over-rides: this is required for proper integration
    final override val component = "binary_sensor"
    override val icon = "mdi:door"

    override fun discovery() = super.discovery().apply {
        if (offDelay > 1.seconds) put("off_delay", offDelay.inWholeSeconds)
        deviceClass.addDiscovery(this)
    }

    override var currentState: Boolean
        get() = sensorState.get() == "ON"
        set(v) {
            sensorState.set(if (v) "ON" else "OFF")
            sendCurrentState()
        }

    companion object {
        enum class BinaryDevice : DeviceClass {
            NONE,
            BATTERY,
            BATTERY_CHARGING,
            CARBON_MONOXIDE,
            COLD,
            CONNECTIVITY,
            DOOR,
            GARAGE_DOOR,
            GAS,
            HEAT,
            LIGHT,
            LOCK,
            MOISTURE,
            MOTION,
            MOVING,
            OCCUPANCY,
            OPENING,
            PLUG,
            POWER,
            PRESENCE,
            PROBLEM,
            RUNNING,
            SAFETY,
            SMOKE,
            SOUND,
            TAMPER,
            UPDATE,
            VIBRATION,
            WINDOW
        }
    }
}

/**
 * Analog (value) sensor, single state. Note that any value that specifies a [deviceClass] will be expected to
 * provide the appropriate [unitOfMeasurement]: e.g. `current` requires either "mA" or "A" for units. These are
 * defined at the [device-class](https://www.home-assistant.io/integrations/sensor/#device-class) page.
 *
 * Any value to be transmitted must be pre-converted to the appropriate string.
 */
open class KobotAnalogSensor(
    uniqueId: String,
    name: String,
    deviceIdentifier: DeviceIdentifier,
    deviceClass: AnalogDevice = AnalogDevice.NONE,
    expires: Duration = Duration.ZERO,
    val stateClass: StateClass = StateClass.NONE,
    val unitOfMeasurement: String? = null,
    val suggestedPrecision: Int? = null
) : KobotSensor<AnalogDevice, String?>(uniqueId, name, deviceIdentifier, expires, deviceClass) {

    // do not allow over-rides: this is required for proper integration
    final override val component = "sensor"
    override val icon = "mdi:gauge"

    override fun discovery() = super.discovery().apply {
        if (suggestedPrecision != null) put("suggested_display_precision", suggestedPrecision)
        deviceClass.addDiscovery(this, unitOfMeasurement)
    }

    override var currentState: String?
        get() = sensorState.get()
        set(v) {
            sensorState.set(v)
            sendCurrentState()
        }

    companion object {
        enum class StateClass {
            NONE,
            MEASUREMENT,
            TOTAL,
            TOTAL_INCREASING
        }

        enum class AnalogDevice : DeviceClass {
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
            DATE,
            DISTANCE,
            DURATION,
            ENERGY,
            ENERGY_STORAGE,
            ENUM,
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
            TIMESTAMP,
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
