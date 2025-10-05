package crackers.kobots.parts.movement.async

import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


class SceneBuilder {
    private val actions = mutableListOf<suspend () -> Unit>()
    private val startSwitch = CountDownLatch(1)

    interface SceneMovement {
        var duration: Duration
        var ease: EasingFunction
    }

    /**
     * Parameter holder class for moveTo DSL
     */
    class Rotate : SceneMovement {
        override var duration: Duration = 2.seconds
        override var ease: EasingFunction = linear
        var angle: Int = 0
    }

    /**
     * DSL construct for moveTo with named parameter assignment syntax.
     * Usage: rotator.moveTo { angle = 90; duration = 2.seconds; ease = linear }
     */
    infix fun AsyncRotator.moveTo(rotate: Rotate.() -> Unit) {
        val params = Rotate().apply(rotate)
        actions.add {
            this.rotateTo(params.angle, params.duration, params.ease)
        }
    }


    fun play() =
        runBlocking {
            val tasks = mutableListOf<kotlinx.coroutines.Job>()
            actions.forEach { action ->
                val task =
                    launch {
                        startSwitch.await()
                        action()
                    }
                tasks.add(task)
            }
            startSwitch.countDown()
            tasks.joinAll()
        }
}

fun sceneBuilder(block: SceneBuilder.() -> Unit) = SceneBuilder().apply(block)
