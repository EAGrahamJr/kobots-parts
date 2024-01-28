package crackers.kobots.mqtt.homeassistant

import crackers.kobots.devices.lighting.PixelBuf
import crackers.kobots.devices.lighting.WS2811
import crackers.kobots.mqtt.homeassistant.LightColor.Companion.toLightColor
import java.awt.Color
import java.util.concurrent.CompletableFuture
import kotlin.math.roundToInt

/**
 * Simple HomeAssistant "light" that controls a single pixel on a 1->n PixelBuf strand.
 */
class SinglePixelLightController(private val theStrand: PixelBuf, private val index: Int) : LightController {
    private var lastColor: WS2811.PixelColor = WS2811.PixelColor(Color.WHITE, brightness = 0.5f)
    override val lightEffects: List<String>? = null
    override val controllerIcon = "mdi:led-strip"

    // note: brightness for the kobot lights is 0-100
    override fun current(): LightState {
        val state = theStrand[index].color != Color.BLACK
        return LightState(
            state = state,
            brightness = if (!state) 0 else (lastColor.brightness!! * 100f).roundToInt(),
            color = lastColor.color.toLightColor()
        )
    }

    override fun set(command: LightCommand) {
        if (command.state == false) {
            theStrand[index] = Color.BLACK
            return
        }
        // if the last color was "off", use the stored color
        val currentColor = theStrand[index].let {
            if (it.color == Color.BLACK) lastColor else it
        }

        val cb = command.brightness?.let { it / 100f } ?: currentColor.brightness
        val color = command.color ?: currentColor.color
        lastColor = WS2811.PixelColor(color, brightness = cb)
        theStrand[index] = lastColor
    }

    override fun exec(effect: String): CompletableFuture<Void> {
        TODO("Not yet implemented")
    }
}

class PixelBufController(private val theStrand: PixelBuf) : LightController {
    override val lightEffects: List<String>? = null
    override val controllerIcon = "mdi:led-strip"
    override fun set(command: LightCommand) {
        TODO("Not yet implemented")
    }

    override fun current(): LightState {
        TODO("Not yet implemented")
    }

    override fun exec(effect: String): CompletableFuture<Void> {
        TODO("Not yet implemented")
    }
}
