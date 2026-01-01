package crackers.kobots.parts.movement.async

import com.diozero.devices.Button
import crackers.kobots.app.AppCommon
import crackers.kobots.parts.app.io.NeoKeyHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import org.slf4j.LoggerFactory
import kotlin.time.Duration

interface KobotsEvent {
    val name: String
}

/**
 * Simple event bus for publishing and subscribing to Kobots events.
 */
object EventBus {
    val logger = LoggerFactory.getLogger("EventBus")
    private val _events = MutableSharedFlow<KobotsEvent>(extraBufferCapacity = 100)
    val events = _events.asSharedFlow()

    /**
     * Publish an event to the event bus.
     *
     * @param event the event to publish.
     */
    suspend fun <T : KobotsEvent> publish(event: T) {
        _events.emit(event)
    }

    /**
     * Subscribe to events of type [T] with the given name.
     *
     * @param name the name of the event to subscribe to.
     * @param onEvent the function to call when an event is received.
     */
    inline fun <reified T : KobotsEvent> subscribe(
        name: String,
        crossinline onEvent: (T) -> Unit,
    ) {
        AppScope.appScope.launch {
            events
                .filterIsInstance<T>()
                .filter { msg -> msg.name == name }
                .collect { event ->
                    launch(Dispatchers.Default) {
                        onEvent(event)
                    }
                }
        }
    }
}

/**
 * Event published when a Kobots button is pressed.
 *
 * @param name the name of the button that was pressed.
 */
data class KobotsButtonEvent(override val name: String) : KobotsEvent

/**
 * Start monitoring the button for press events and publish KobotsButtonEvent on press.
 *
 * @param checkInterval the interval at which to check the button state.
 * @param runScope scope to run the application in; defaults to `AppScope.appScope`
 * @param runCheck function determines whether the app is running or not - must be thread safe; defaults to `AppCommon
 * .aplicationRunning`
 */
fun Button.startAsync(
    checkInterval: Duration,
    runScope: CoroutineScope = AppScope.appScope,
    runCheck: () -> Boolean = { AppCommon.applicationRunning }
) {
    val me = this.name
    runScope.launch {
        var lastButton = false
        while (runCheck()) {
            val currentState = withContext(Dispatchers.IO) { isPressed }
            // detect buton UP event (e.g. true->false)
            if (lastButton && !currentState) {
                EventBus.publish(KobotsButtonEvent(me))
            }
            lastButton = currentState
            delay(checkInterval)
        }
    }

}

data class NeoKeyEvent(override val name: String, val buttons: List<Boolean>) : KobotsEvent

/**
 * Start monitoring the NeoKey buttons for press events and publish NeoKeyEvent on press.
 *
 * @param checkInterval the interval at which to check the button state.
 * @param runScope scope to run the application in; defaults to `AppScope.appScope`
 * @param runCheck function determines whether the app is running or not - must be thread safe; defaults to `AppCommon
 * .aplicationRunning`
 */
fun NeoKeyHandler.startAsync(
    checkInterval: Duration,
    runScope: CoroutineScope = AppScope.appScope,
    runCheck: () -> Boolean = { AppCommon.applicationRunning }
) {
    val me = this.name
    runScope.launch {
        val lastButtons = MutableList(numberOfButtons) { false }
        while (runCheck()) {
            val buttonStates = withContext(Dispatchers.IO) { read() }
            // detect button UP events (e.g. true->false)
            val fired = buttonStates.zip(lastButtons) { current, last -> last && !current }
            if (fired.any { it }) {
                EventBus.logger.info("$me Firing buttons: $fired")
                EventBus.publish(NeoKeyEvent(me, fired))
            }
            for (i in 0 until numberOfButtons) {
                lastButtons[i] = buttonStates[i]
            }
            delay(checkInterval)
        }
    }
}

data class KobotSensorEvent<T>(override val name: String, val value: T) : KobotsEvent

/**
 * Start monitoring a sensor by periodically calling [readFunction] and publishing the result as a
 * [KobotSensorEvent].
 *
 * @param sensorName the name of the sensor.
 * @param readFunction function to read the sensor value.
 * @param checkInterval the interval at which to check the sensor.
 * @param runScope scope to run the application in; defaults to `AppScope.appScope`
 * @param runCheck function determines whether the app is running or not - must be thread safe; defaults to `AppCommon
 * .aplicationRunning`
 */
fun <T> startSensorPublish(
    sensorName: String,
    readFunction: () -> T,
    checkInterval: Duration,
    runScope: CoroutineScope = AppScope.appScope,
    runCheck: () -> Boolean = { AppCommon.applicationRunning }
) {
    runScope.launch {
        while (runCheck()) {
            val value = withContext(Dispatchers.IO) { readFunction() }
            EventBus.publish(KobotSensorEvent(sensorName, value))
            delay(checkInterval)
        }
    }
}
