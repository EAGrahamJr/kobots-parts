/*
 * Copyright 2022-2024 by E. A. Graham, Jr.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package crackers.kobots.parts.movement

import crackers.kobots.mqtt.KobotsMQTT
import crackers.kobots.mqtt.KobotsMQTT.Companion.KOBOTS_EVENTS
import crackers.kobots.parts.app.*
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Request to execute a sequence of actions.
 */
class SequenceRequest(val sequence: ActionSequence) : KobotsAction

/**
 * Handles running a sequence for a thing. Every sequence is executed on a background thread that runs until
 * completion or until the [stop] method is called or if an [EmergencyStop] is received. Only one sequence can be
 * running at a time (see the [moveInProgress] flag).
 *
 * Default execution speeds are:
 * - VERY_SLOW = 100ms
 * - SLOW = 50ms
 * - NORMAL = 10ms
 * - FAST = 5ms
 * - VERY_FAST = 2ms
 *
 * @param executorName the name of the executor
 * @param mqttClient the MQTT client for publishing events
 */
abstract class SequenceExecutor(
    val executorName: String,
    private val mqttClient: KobotsMQTT
) {
    protected val logger: Logger = LoggerFactory.getLogger(executorName)

    /**
     * Each executor gets a single thread pool for itself.
     */
    private val seqExecutor by lazy { Executors.newSingleThreadExecutor() }

    /**
     * Execution control "lock".
     */
    private val _moving = AtomicBoolean(false)
    var moveInProgress: Boolean
        get() = _moving.get()
        private set(value) = _moving.set(value)

    private val _stop = AtomicBoolean(false)
    var stopImmediately: Boolean
        get() = _stop.get()
        private set(value) = _stop.set(value)

    data class SequenceEvent(val source: String, val sequence: String, val started: Boolean) : KobotsEvent

    /**
     * Sets the stop flag and blocks until the flag is cleared.
     */
    open fun stop() {
        stopImmediately = moveInProgress
        while (stopImmediately) KobotSleep.millis(5)
    }

    protected val currentSequence = AtomicReference<String>()
    protected abstract fun canRun(): Boolean

    /**
     * Handles a request. If the request is a sequence, it is executed on a background thread. If the request is an
     * [EmergencyStop], the [stopImmediately] flag is set.
     *
     * This function is non-blocking.
     */
    open fun handleRequest(request: KobotsAction) {
        when (request) {
            is EmergencyStop -> stopImmediately = true
            is SequenceRequest -> executeSequence(request)
            else -> {}
        }
    }

    infix fun does(actionSequence: ActionSequence) = handleRequest(SequenceRequest(actionSequence))

    /**
     * Executes the sequence in a separate thread. N.B. the sequence should only contain the devices/actions that
     * this handler can control.
     */
    protected fun executeSequence(request: SequenceRequest) {
        // claim it for ourselves and then use that for loop control
        val sequenceName = request.sequence.name
        if (!_moving.compareAndSet(false, true)) {
            logger.warn("Sequence already running - rejected $sequenceName")
            return
        }
        seqExecutor.submit {
            currentSequence.set(sequenceName)

            // publish start event to the masses
            val startMessage = SequenceEvent(executorName, sequenceName, true)
            mqttClient.publish(KOBOTS_EVENTS, JSONObject(startMessage))
            publishToTopic(INTERNAL_TOPIC, startMessage)

            preExecution()
            try {
                request.sequence.build().forEach { action ->
                    val maxPause = Duration.ofMillis(action.speed.millis)

                    // while can run, not stopping, and the action is not done...
                    while (canRun() && !stopImmediately && !action.action.step(maxPause)) {
                        updateCurrentState()
                    }
                }
            } catch (e: Exception) {
                logger.error("Error executing sequence $sequenceName", e)
            }
            postExecution()
            currentSequence.set(null)

            // done
            _moving.set(false)
            updateCurrentState()
            _stop.set(false) // clear emergency stop flag

            // publish completion event to the masses
            val completedMessage = SequenceEvent(executorName, sequenceName, false)
            mqttClient.publish(KOBOTS_EVENTS, JSONObject(completedMessage))
            publishToTopic(INTERNAL_TOPIC, completedMessage)
        }
    }

    /**
     * Optional callback for pre-execution.
     */
    open fun preExecution() {}

    /**
     * Optional callback for post-execution.
     */
    open fun postExecution() {}

    /**
     * Optionally updates the state of the executor.
     */
    open fun updateCurrentState() {}

    companion object {
        const val INTERNAL_TOPIC = "Executor.Sequences"

        @Deprecated("Use KOBOTS_EVENTS instead", ReplaceWith("KOBOTS_EVENTS"))
        const val MQTT_TOPIC = KOBOTS_EVENTS
    }
}
