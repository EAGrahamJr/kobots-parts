package crackers.kobots.mqtt

import crackers.kobots.devices.lighting.PimoroniLEDShim
import crackers.kobots.mqtt.LightColor.Companion.toLightColor
import crackers.kobots.parts.scale
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Controller for a light device backed by a
 * [Pimoroni LED Shim](https://shop.pimoroni.com/products/led-shim?variant=3136952467466) on a Raspberry Pi.
 *
 * **NOTE** This product is no longer available.
 */
class PimoroniShimController(private val device: PimoroniLEDShim) : LightController {
    val currentColor = AtomicReference(Color.BLACK)
    val currentBrightness = AtomicInteger(0)
    val currentState = AtomicBoolean(false)
    val currentEffect = AtomicReference<String>()

    override fun set(command: LightCommand) {
        if (!command.state) {
            device.sleep(true)
            currentState.set(false)
        } else {
            device.sleep(false)
            currentState.set(true)
            if (command.effect != null) {
                manageEffect(command.effect)
            } else {
                currentEffect.set(null)
                // if the command color is not null, use it, otherwise use the current color
                var newColor = command.color ?: currentColor.get()
                // if the color is BLACK, use WHITE because that's the only way to turn it on
                if (newColor == Color.BLACK) newColor = Color.WHITE

                // if the brightness is not null, use it, otherwise use the current brightness
                // if less than or equal to 0, use 100
                var newBrightness = command.brightness ?: currentBrightness.get()
                if (newBrightness <= 0) newBrightness = 100

                currentColor.set(newColor)
                currentBrightness.set(newBrightness)
                val scaledColor = newColor.scale(newBrightness)
                device.setAll(scaledColor)
            }
        }
    }

    private fun manageEffect(effect: String) {
        currentEffect.set(effect)
        // TODO?
    }

    override fun current(): LightState = LightState(
        state = currentState.get(),
        brightness = currentBrightness.get(),
        color = currentColor.get().toLightColor(),
        effect = currentEffect.get()
    )
}
