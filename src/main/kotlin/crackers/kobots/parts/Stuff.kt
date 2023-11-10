package crackers.kobots.parts

import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/*
 * Just stuff.
 */

/**
 * Extension function on a JSON object to get a on/off status as a boolean.
 */
fun JSONObject.onOff(key: String): Boolean = this.optString(key, "off") == "on"

/**
 * Elapsed time.
 */
fun Instant.elapsed(): Duration = Duration.between(this, Instant.now())

/**
 * Captures data in a simple FIFO buffer for averaging. Probably not suitable for large sizes.
 */
class SimpleAverageMeasurement(val bucketSize: Int, val initialValue: Float = Float.MAX_VALUE) {
    private val dumbBuffer = CopyOnWriteArrayList<Float>()

    val value: Float
        get() = if (dumbBuffer.isEmpty()) initialValue else dumbBuffer.average().toFloat()

    operator fun plusAssign(v: Float) {
        dumbBuffer += v
        while (dumbBuffer.size > bucketSize) dumbBuffer.removeAt(0)
    }
}

/**
 * Microseconds to `Duration`.
 */
fun microDuration(micros: Long): Duration = Duration.ofNanos(micros * 1000)

/**
 * Enum value or null.
 */
inline fun <reified T : Enum<T>> enumValue(s: String): T? = try {
    enumValueOf<T>(s.uppercase())
} catch (_: IllegalArgumentException) {
    null
}
