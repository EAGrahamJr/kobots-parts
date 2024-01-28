package crackers.kobots.mqtt.homeassistant

import crackers.kobots.devices.lighting.PimoroniLEDShim
import crackers.kobots.mqtt.homeassistant.LightColor.Companion.toLightColor
import crackers.kobots.parts.scale
import java.awt.Color
import java.util.concurrent.CompletableFuture
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
    private val currentColor = AtomicReference(Color.BLACK)
    private val currentBrightness = AtomicInteger(0)
    private val currentState = AtomicBoolean(false)
    private val currentEffect = AtomicReference<String>()

    override val controllerIcon = "mdi:led-strip"
    override val lightEffects: List<String>? = null

    override fun set(command: LightCommand) = with(command) {
        if (state == false) {
            device.sleep(true)
            currentState.set(false)
        } else {
            device.sleep(false)
            currentState.set(true)
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

    override fun exec(effect: String): CompletableFuture<Void> {
        TODO("Not yet implemented")
    }

    override fun current(): LightState = LightState(
        state = currentState.get(),
        brightness = currentBrightness.get(),
        color = currentColor.get().toLightColor(),
        effect = currentEffect.get()
    )
}
