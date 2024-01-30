package crackers.kobots.mqtt.homeassistant

/**
 * HA has an easy way to "send" numbers as commands: this aligns really well with things like [Rotator] and
 * [LinearActuator] devices.
 *
 * There are quite a few UI hints that can be set on these objects; defaults are set in all cases.
 */
class KobotNumber(
    val handler: NumberHandler,
    uniqueId: String,
    name: String,
    deviceIdentifier: DeviceIdentifier,
    val deviceClass: NumericDevice = NumericDevice.NONE,
    val min: Int = 1,
    val max: Int = 100,
    val mode: DisplayMode = DisplayMode.auto,
    val step: Float = 1.0f,
    val unitOfMeasurement: String? = null
) : CommandEntity(
    uniqueId,
    name,
    deviceIdentifier
) {

    override val component = "number"
    override val icon = "mdi:numeric"

    override fun discovery() = super.discovery().apply {
        deviceClass.addDiscovery(this, unitOfMeasurement)

        put("max", max)
        put("min", min)
        put("mode", mode.name)
        put("step", step)
    }

    override fun currentState() = handler.currentState?.toString() ?: "None"

    override fun handleCommand(payload: String) {
        handler.move(payload.toFloat())
    }

    companion object {
        interface NumberHandler {
            val currentState: Float?
            fun move(target: Float)
        }

        enum class DisplayMode {
            auto, box, slider
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
