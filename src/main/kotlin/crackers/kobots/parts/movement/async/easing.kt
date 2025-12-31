package crackers.kobots.parts.movement.async

import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.time.Duration

typealias EasingFunction = (Float) -> Float
typealias UpdateFunction = suspend (Float) -> Unit

val linear: EasingFunction = { t -> t }

val softLaunch: EasingFunction = { t -> t * t }

val softLanding: EasingFunction = { t -> t * (2 - t) }

val smooth: EasingFunction = { t -> -(cos(PI.toFloat() * t) - 1) / 2 }

val bounce: EasingFunction = { t ->
    when {
        t < 1f / 2.75f -> 7.5625f * t * t
        t < 2f / 2.75f -> {
            val t2 = t - 1.5f / 2.75f
            7.5625f * t2 * t2 + 0.75f
        }

        t < 2.5f / 2.75f -> {
            val t2 = t - 2.25f / 2.75f
            7.5625f * t2 * t2 + 0.9375f
        }

        else -> {
            val t2 = t - 2.625f / 2.75f
            7.5625f * t2 * t2 + 0.984375f
        }
    }
}

suspend fun easeTo(
    start: Float,
    end: Float,
    duration: Duration,
    updateFn: UpdateFunction,
    easingFn: EasingFunction = linear,
    steps: Int = 50,
) {
    val stepDuration = (duration.inWholeMilliseconds / steps).coerceAtLeast(10)
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val easedT = easingFn(t)
        val value = start + (end - start) * easedT
        updateFn(value)
        delay(stepDuration)
    }
}
