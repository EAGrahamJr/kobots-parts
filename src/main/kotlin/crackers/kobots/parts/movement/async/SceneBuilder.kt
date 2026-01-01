package crackers.kobots.parts.movement.async

import kotlinx.coroutines.*
import kotlin.time.Duration

/**
 * Scene builder for orchestrating concurrent asynchronous movements.
 */
class SceneBuilder {
    private val actions = mutableListOf<suspend () -> Unit>()

    /**
     * DSL construct for moveTo with named parameter assignment syntax.
     * Usage: `rotator moveTo { angle = 90; duration = 2.seconds; ease = linear }`
     */
    infix fun <T : AsyncRotator> T.moveTo(rotate: Rotate.() -> Unit) {
        val params = Rotate().apply(rotate)
        actions.add {
            if (params.startDelay > Duration.ZERO) {
                delay(params.startDelay)
            }
            this.rotateAsync(params.angle, params.duration, params.ease)
            if (params.endDelay > Duration.ZERO) {
                delay(params.endDelay)
            }
        }
    }

    /**
     * DSL construct for smooth movements with named parameter assignment syntax.
     * Usage: `rotator smoothly { angle = 90; duration = 2.seconds }`
     */
    infix fun <T : AsyncRotator> T.smoothly(rotate: SmoothRotate.() -> Unit) {
        val params = SmoothRotate().apply(rotate)
        this moveTo {
            angle = params.angle
            duration = params.duration
            startDelay = params.startDelay
            endDelay = params.endDelay
            ease = params.ease
        }
    }

    /**
     * DSL construct for soft launch movements with named parameter assignment syntax.
     * Usage: `rotator withSoftLaunch { angle = 90; duration = 2.seconds }`
     */
    infix fun <T : AsyncRotator> T.withSoftLaunch(rotate: SoftLaunchRotate.() -> Unit) {
        val params = SoftLaunchRotate().apply(rotate)
        this moveTo {
            angle = params.angle
            duration = params.duration
            startDelay = params.startDelay
            endDelay = params.endDelay
            ease = params.ease
        }
    }

    /**
     * DSL construct for soft landing movements with named parameter assignment syntax.
     * Usage: `rotator withSoftLanding { angle = 90; duration = 2.seconds }`
     */
    infix fun <T : AsyncRotator> T.withSoftLanding(rotate: SoftLandingRotate.() -> Unit) {
        val params = SoftLandingRotate().apply(rotate)
        this moveTo {
            angle = params.angle
            duration = params.duration
            startDelay = params.startDelay
            endDelay = params.endDelay
            ease = params.ease
        }
    }


    /**
     * Starts all actions concurrently and waits for their completion.
     */
    suspend fun invoke() = coroutineScope {
        val startSwitch = CompletableDeferred<Unit>()

        val tasks = mutableListOf<Job>()
        actions.forEach { action ->
            val task = launch {
                startSwitch.await()
                action()
            }
            tasks.add(task)
        }
        startSwitch.complete(Unit)
        tasks.joinAll()
    }

    /**
     * Runnit (blocking).
     */
    fun play() = runBlocking {
        invoke()
    }

    /**
     * Runnit (non-blocking).
     */
    fun start(): Job = AppScope.appScope.launch {
        invoke()
    }
}

/**
 * DSL entry point for building and executing a scene.
 */
fun sceneBuilder(block: SceneBuilder.() -> Unit) = SceneBuilder().apply(block)
