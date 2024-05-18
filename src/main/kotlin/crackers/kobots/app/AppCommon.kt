/*
 * Copyright 2022-2023 by E. A. Graham, Jr.
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

package crackers.kobots.app

import com.typesafe.config.ConfigFactory
import crackers.hassk.HAssKClient
import crackers.kobots.mqtt.KobotsMQTT
import crackers.kobots.parts.app.KobotsEvent
import crackers.kobots.parts.app.publishToTopic
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Common application control and configuration -- e.g. how many times have you written this code?
 *
 * This is _highly_ opinionated and is not necessarily intended to be a general purpose library for anyone but myself.
 */
object AppCommon {
    private val logger by lazy { LoggerFactory.getLogger("AppCommon") }

    /**
     * A generally sharable executor for running things. Most apps will rely on either callbacks or futures, so this
     * just provides a simple way to manage the application's threads.
     */
    val executor by lazy { Executors.newScheduledThreadPool(4) }

    /**
     * A flag to indicate if the application is running. This is used to control execution loops, when necessary
     * (usually in the main application, polling for inputs).
     *
     * It's recommended that [applicationRunning] is used instead of this directly.
     */
    val runFlag = AtomicBoolean(true)

    /**
     * A useful latch for waiting for the application to stop. Used with the [applicationRunning] property.
     */
    fun awaitTermination() = applicationLatch.await()
    private val applicationLatch = CountDownLatch(1)

    /**
     * Convenience property for managing the application state. This should really only be controlled in the main
     * application, but generally readable everywhere else. Once set to `false`, it cannot be re-set back to `true`.
     *
     * If set to `false`, the [awaitTermination] latch will trip.
     *
     * **Note:** this does _not_ terminate **any** threads or co-routines: it is only a "system indicator".
     */
    var applicationRunning: Boolean
        get() = runFlag.get()
        set(value) {
            if (!runFlag.get()) {
                logger.error("Application is already stopped")
                return
            }
            if (!value) applicationLatch.countDown()

            runFlag.set(value)
        }

    /**
     * Run a [block] if the application is running. This is a convenience method for checking the [applicationRunning]
     * and catching any errors that occur in the block.
     */
    fun whileRunning(block: () -> Unit) {
        if (applicationRunning) {
            try {
                block()
            } catch (t: Throwable) {
                logger.error("Error in execution", t)
            }
        }
    }

    /**
     * Convenience property for the application configuration.
     */
    val applicationConfig by lazy { ConfigFactory.load() }

    /**
     * HomeAssistant client using the configuration in `application.conf`.
     */
    val hasskClient by lazy {
        val port = if (applicationConfig.hasPath("ha.port")) applicationConfig.getInt("ha.port") else 8123

        HAssKClient(
            applicationConfig.getString("ha.token"),
            applicationConfig.getString("ha.server"),
            port
        )
    }

    /**
     * MQTT client using the configuration in `application.conf`.
     */
    val mqttClient by lazy {
        KobotsMQTT(InetAddress.getLocalHost().hostName, applicationConfig.getString("mqtt.broker"))
    }

    /**
     * Generic topic and event for sleep/wake events on the internal event bus.
     */
    const val SLEEP_TOPIC = "System.Sleep"

    class SleepEvent(val sleep: Boolean) : KobotsEvent

    fun goToSleep() = publishToTopic(SLEEP_TOPIC, SleepEvent(true))
    fun wakey() = publishToTopic(SLEEP_TOPIC, SleepEvent(false))

    fun <F> ignoreErrors(executionBlock: () -> F?, logIt: Boolean = false): F? = try {
        executionBlock()
    } catch (t: Throwable) {
        if (logIt) logger.error("Error trying to ignore: ${t.localizedMessage}")
        null
    }

    /**
     * Start/stop things.
     */
    interface Startable {
        fun start()
        fun stop()
    }

    fun List<Startable>.convenientStartupHook() {
        forEach(Startable::start)
    }

    fun List<Startable>.convenientShutdownHook(logErrors: Boolean = false) {
        forEach { ignoreErrors(it::stop, logErrors) }
    }

    /**
     * The remote hostname property used for the diozero remote daemon.
     */
    const val REMOTE_PI = "diozero.remote.hostname"
}
