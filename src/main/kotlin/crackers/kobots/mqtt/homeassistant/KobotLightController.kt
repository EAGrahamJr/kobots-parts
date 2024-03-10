package crackers.kobots.mqtt.homeassistant

import com.diozero.api.PwmOutputDevice
import crackers.kobots.devices.set
import java.util.concurrent.CompletableFuture
import kotlin.math.roundToInt

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
     * Execute an effect in some sort of completable manner. Note this **must** be cancellable since commands can be
     * compounded.
     */
    infix fun exec(effect: String): CompletableFuture<Void>

    /**
     * Get the state of the light. Node "0" represents either a single LED or a full LED strand. Non-zero indices are
     * to address each LED individually, offset by 1.
     */
    fun current(): LightState

    val controllerIcon: String

    val lightEffects: List<String>?
}

/**
 * Turns it on and off, adjust brightness. Supports a minimal set of effects.
 *
 * TODO the effect should include (`n` is a duration)
 *
 * * `blink n` on/off; duration is how long the light stays in that state
 * * `pulse n` gently fade in/out; duration is for a full cycle7
 */
class BasicLightController(val device: PwmOutputDevice) : LightController {
    private var currentEffect: String? = null
    override val lightEffects: List<String>? = null

    override fun set(command: LightCommand) = with(command) {
        if (brightness != null) {
            device.setValue(brightness / 100f)
        } else {
            device.set(state)
        }
    }

    override fun exec(effect: String): CompletableFuture<Void> {
        currentEffect = effect
        return CompletableFuture.runAsync {
            TODO("No effects yet")
        }
    }

    override fun current() = LightState(
        device.isOn,
        brightness = (device.value * 100f).roundToInt(),
        effect = currentEffect
    )
    override val controllerIcon = "mdi:lamp"
}
