package crackers.kobots.mqtt.homeassistant

import crackers.kobots.app.AppCommon.mqttClient
import crackers.kobots.mqtt.homeassistant.KobotAnalogSensor.Companion.AnalogDevice
import crackers.kobots.mqtt.homeassistant.KobotBinarySensor.Companion.BinaryDevice
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class KobotSensor<M : Enum<M>>(
    uniqueId: String,
    name: String,
    val expires: Duration,
    val deviceClass: M,
    deviceIdentifier: DeviceIdentifier
) : AbstractKobotEntity(uniqueId, name, deviceIdentifier) {

    override fun currentState() = JSONObject()

    protected val sensorState = AtomicReference<String>()

    fun sendSensorState() {
        if (homeassistantAvailable) mqttClient[statusTopic] = sensorState.get() ?: ""
    }

    override fun discovery() = with(super.discovery()) {
        put("entity_category", "diagnostic")
        if (expires > 1.seconds) put("expire_after", expires.inWholeSeconds)
        if (deviceClass.name != "NONE") {
            remove("icon") // let the device class decide the icon
            put("device_class", deviceClass.name.lowercase())
        }
        this
    }
}

/**
 * On/off sensor, single state. If the [deviceClass] is set, Home Assistant will pick the appropraite "type" of
 * binary sensor.
 */
open class KobotBinarySensor(
    uniqueId: String,
    name: String,
    deviceClass: BinaryDevice = BinaryDevice.NONE,
    expires: Duration = Duration.ZERO,
    val offDelay: Duration = Duration.ZERO,
    deviceIdentifier: DeviceIdentifier = DEFAULT_IDENTIFIER
) : KobotSensor<BinaryDevice>(uniqueId, name, expires, deviceClass, deviceIdentifier) {

    // do not allow over-rides: this is required for proper integration
    final override val component = "binary_sensor"

    override fun discovery() = super.discovery().apply {
        if (offDelay > 1.seconds) put("off_delay", offDelay.inWholeSeconds)
    }

    var currentState: Boolean
        get() = sensorState.get() == "ON"
        set(v) {
            sensorState.set(if (v == true) "ON" else "OFF")
            sendSensorState()
        }

    companion object {
        val DEFAULT_IDENTIFIER = DeviceIdentifier("Kobots", "KobotBinarySensor", "mdi:sensors")

        enum class BinaryDevice {
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
 * provide the appropriate [unit_of_measurement]: e.g. `current` requires either "mA" or "A" for units. These are
 * defined at the [device-class](https://www.home-assistant.io/integrations/sensor/#device-class) page.
 *
 * Any value to be transmitted must be pre-converted to the appropriate string.
 */
open class KobotAnalogSensor(
    uniqueId: String,
    name: String,
    deviceClass: AnalogDevice = AnalogDevice.NONE,
    expires: Duration = Duration.ZERO,
    val stateClass: StateClass = StateClass.NONE,
    val unitOfMeasurement: String? = null,
    val suggestedPrecision: Int? = null,
    deviceIdentifier: DeviceIdentifier = DEFAULT_IDENTIFIER
) : KobotSensor<AnalogDevice>(uniqueId, name, expires, deviceClass, deviceIdentifier) {

    // do not allow over-rides: this is required for proper integration
    final override val component = "sensor"

    override fun discovery() = super.discovery().apply {
        if (unitOfMeasurement != null) put("unit_of_measurement", unitOfMeasurement)
        if (suggestedPrecision != null) put("suggested_display_precision", suggestedPrecision)
    }

    var currentState: String?
        get() = sensorState.get()
        set(v) {
            sensorState.set(v)
            sendSensorState()
        }

    companion object {
        val DEFAULT_IDENTIFIER = DeviceIdentifier("Kobots", "KobotAmalogSensor", "mdi:sensors")

        enum class StateClass {
            NONE,
            MEASUREMENT,
            TOTAL,
            TOTAL_INCREASING
        }

        enum class AnalogDevice {
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
