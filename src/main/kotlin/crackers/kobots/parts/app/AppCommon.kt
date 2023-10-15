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
import crackers.kobots.parts.app.KobotSleep
import crackers.kobots.parts.app.KobotsEvent
import crackers.kobots.parts.app.publishToTopic
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Common application control and configuration -- e.g. how many times have you written this code?
 *
 * This is _highly_ opinionated and is not necessarily intended to be a general purpose library for anyone but muself.
 */
object AppCommon {
    private val logger = LoggerFactory.getLogger("AppCommon")

    /**
     * A generally sharable executor for running things. Most apps will rely on either callbacks or futures, so this
     * just provides a simple way to manage the application's threads.
     */
    val executor by lazy { Executors.newScheduledThreadPool(4) }

    /**
     * A flag to indicate if the application is running. This is used to control execution loops, when necessary
     * (usually in the main application, polling for inputs).
     *
     * It's recommeneded that [applicationRunning] is used instead of this directly.
     */
    val runFlag = AtomicBoolean(true)

    /**
     * A useful latch for waiting for the application to stop. Used with the [applicationRunning] property.
     */
    fun awaitTermination() = applicationLatch.await()
    private val applicationLatch = CountDownLatch(1)

    /**
     * Convenience property for managing the application state. This should really only be controlled in the main
     * applicaiton, but generally readable everywhere else. Once set to `false`, it cannot be re-set back to `true`.
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
     * Run a [block] and ensure it takes up **at least** [maxPause] time. This is basically to keep polling or control
     * loops from running amok, if not using scheduling. The pause is executed even if an error occurs in the block.
     *
     * **Note:** the [applicationRunning] flag is checked before the pause is executed, so if the application is stopped
     * while the block is executing, the pause will not be executed.
     *
     * The purported granularity of the pause is _ostensibly_ nanoseconds.
     */
    @Deprecated("Use Java executors instead")
    fun <R> executeWithMinTime(maxPause: Duration, block: () -> R): R {
        val pauseForNanos = maxPause.toNanos()
        val startAt = System.nanoTime()

        return try {
            block()
        } finally {
            if (applicationRunning) {
                val runtime = System.nanoTime() - startAt
                if (runtime < pauseForNanos) KobotSleep.nanos(pauseForNanos - runtime)
            }
        }
    }

    /**
     * Run an execution loop until the run-flag says stop
     */
    @Deprecated("Use Java executors instead")
    fun checkRun(maxPause: Duration, block: () -> Unit): Future<*> = executor.submit {
        while (applicationRunning) executeWithMinTime(maxPause) { block() }
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
}
