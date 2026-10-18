/*
 * Copyright 2022-2026 by E. A. Graham, Jr.
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

package crackers.kobots.coroutine

import com.diozero.devices.Button
import crackers.kobots.app.AppCommon
import crackers.kobots.parts.app.io.NeoKeyHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

interface KobotsEvent {
    val name: String
}

/**
 * Simple event bus for publishing and subscribing to Kobots events. Flows are buffered to 1K events to avoid
 * suspending publishers.
 */
object EventBus {
    val logger = LoggerFactory.getLogger("EventBus")

    // create separate flows per event type to reduce filtering overhead and blocking on non-related events
    private val _events = ConcurrentHashMap<Class<out KobotsEvent>, MutableSharedFlow<KobotsEvent>>()

    private fun getFlowForEvent(eventClass: Class<out KobotsEvent>): MutableSharedFlow<KobotsEvent> {
        return _events.computeIfAbsent(eventClass) {
            MutableSharedFlow(extraBufferCapacity = 1000)
        }
    }

    /**
     * Get the flow for the given event class. Note that the flow is created if it does not
     * already exist, but this still only returns a non-modifiable flow.
     *
     * @param eventClass the class of the event
     * @return the flow for the event class
     */
    @PublishedApi
    internal
    fun getFlow(eventClass: Class<out KobotsEvent>): SharedFlow<KobotsEvent> {
        return getFlowForEvent(eventClass).asSharedFlow()
    }

    /**
     * Publish an event to the event bus.
     *
     * @param event the event to publish
     */
    fun <T : KobotsEvent> publish(event: T) {
        getFlowForEvent(event::class.java).tryEmit(event)
    }

    /**
     * Subscribe to events of type [T] with the given name. Messages are delivered sequentially to each listener
     * within the limits of the buffer size of the underlying flow.
     *
     * @param name the name of the event to subscribe to
     * @param onEvent the function to call when an event is received
     */
    inline fun <reified T : KobotsEvent> subscribe(
        name: String,
        crossinline onEvent: suspend (T) -> Unit,
    ) {
        getFlow(T::class.java).let { flow ->
            AppScope.appScope.launch(start = CoroutineStart.UNDISPATCHED) {
                // job dead, go away
                if (!isActive) return@launch

                flow
                    .filter { msg -> msg.name == name }
                    .collect { event ->
                        try {
                            onEvent(event as T)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            logger.error("Unhandled by subscriber", t)
                        }
                    }
            }
        }
    }
}

/**
 * Event published when a Kobots button is pressed.
 *
 * @param name the name of the button that was pressed
 */
data class KobotsButtonEvent(override val name: String) : KobotsEvent

/**
 * Start monitoring the button for press events and publish KobotsButtonEvent on press. Device polling occurs on the
 *  * `Dispatchers.IO` thread pool, and events are published on the `Dispatchers.Default` thread pool.
 *
 * This is used in lieu of the built-in button event handling because it is not thread safe and uses "regular" threads.
 *
 * @param checkInterval the interval at which to check the button state
 * @param runCheck function determines whether the app is running or not - must be thread safe;
 *        defaults to `AppCommon.applicationRunning`
 */
fun Button.startAsync(
    checkInterval: Duration,
    runCheck: () -> Boolean = { AppCommon.applicationRunning }
) {
    val me = this.name
    var lastButton = false
    AppScope.scheduleWithFixedDelay(checkInterval, checkInterval, runCheck = runCheck, block = {
        val currentState = isPressed
        // detect button UP event (e.g. true->false)
        if (lastButton && !currentState) {
            EventBus.publish(KobotsButtonEvent(me))
        }
        lastButton = currentState
    })
}

data class NeoKeyEvent(override val name: String, val buttons: List<Boolean>) : KobotsEvent

/**
 * Start monitoring the NeoKey buttons for press events and publish NeoKeyEvent on press. Device polling occurs on the
 * `Dispatchers.IO` thread pool, and events are published on the `Dispatchers.Default` thread pool.
 *
 * @param checkInterval the interval at which to check the button state
 * @param runCheck function determines whether the app is running or not - must be thread safe;
 *        defaults to `AppCommon.applicationRunning`
 */
fun NeoKeyHandler.startAsync(
    checkInterval: Duration,
    runCheck: () -> Boolean = { AppCommon.applicationRunning }
) {
    val me = this.name
    val lastButtons = MutableList(numberOfButtons) { false }
    AppScope.scheduleWithFixedDelay(checkInterval, checkInterval, runCheck = runCheck, block = {
        val buttonStates = read()
        // detect button UP events (e.g. true->false)
        val fired = buttonStates.zip(lastButtons) { current, last -> last && !current }
        if (fired.any { it }) {
            EventBus.logger.info("$me Firing buttons: $fired")
            EventBus.publish(NeoKeyEvent(me, fired))
        }
        for (i in 0 until numberOfButtons) {
            lastButtons[i] = buttonStates[i]
        }
    })
}

data class KobotSensorEvent<T>(override val name: String, val value: T) : KobotsEvent

/**
 * Start monitoring a sensor by periodically calling [readFunction] and publishing the result as a [KobotSensorEvent].
 * Device polling occurs on the * `Dispatchers.IO` thread pool, and events are published on the `Dispatchers.Default`
 * thread pool.
 *
 * @param sensorName the name of the sensor
 * @param readFunction function to read the sensor value
 * @param checkInterval the interval at which to check the sensor
 * @param runCheck function determines whether the app is running or not - must be thread safe;
 *        defaults to `AppCommon.applicationRunning`
 */
fun <T> startSensorPublish(
    sensorName: String,
    readFunction: () -> T,
    checkInterval: Duration,
    runCheck: () -> Boolean = { AppCommon.applicationRunning }
) {
    AppScope.scheduleWithFixedDelay(checkInterval, checkInterval, runCheck = runCheck, block = {
        val value = readFunction()
        EventBus.publish(KobotSensorEvent(sensorName, value))
    })
}
