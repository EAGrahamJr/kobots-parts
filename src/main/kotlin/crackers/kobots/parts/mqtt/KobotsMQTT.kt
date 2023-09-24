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
package crackers.kobots.mqtt

import org.eclipse.paho.mqttv5.client.*
import org.eclipse.paho.mqttv5.common.MqttException
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.MqttSubscription
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MQTT wrapper for Kobots.
 *
 * Because this is still in the experimental phase, QoS is **always** `0` and each session starts "clean" (the broker
 * and clients do not retain "state"). Auto-reconnect is enabled and any subscriptions are re-subscribed on re-connect.
 *
 * Because of the current default Qos, any actual persistence is not used.
 */
class KobotsMQTT(private val clientName: String, broker: String, persistence: MqttClientPersistence? = null) :
    AutoCloseable {
    private val logger = LoggerFactory.getLogger("${clientName}MQTT")
    private val subscribers = mutableMapOf<String, List<(String) -> Unit>>()
    private var aliveCheckInterval = 60L
    private lateinit var aliveCheckFuture: ScheduledFuture<*>
    private val aliveCheckEnabled = AtomicBoolean(false)

    private val mqttClient: MqttAsyncClient by lazy {
        val options = MqttConnectionOptions().apply {
            isAutomaticReconnect = true
            isCleanStart = true
            keepAliveInterval = 10
            connectionTimeout = 10
        }
        MqttAsyncClient(broker, clientName, persistence).apply {
            setCallback(mqttCallback)
            connect(options).waitForCompletion()
        }
    }

    private val mqttCallback = object : MqttCallback {
        override fun disconnected(disconnectResponse: MqttDisconnectResponse) {
            logger.error("Disconnected: ${disconnectResponse.exception}")
            // kill the alive-checker
            if (::aliveCheckFuture.isInitialized) {
                logger.error("Stopping alive-check")
                aliveCheckFuture.cancel(true)
            }
        }

        override fun mqttErrorOccurred(exception: MqttException) {
            logger.error("Error: $exception")
        }

        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            if (reconnect) {
                logger.error("Re-connected")
                // kill the old alive-checkser and start a new one
                if (aliveCheckEnabled.get()) {
                    startAliveCheck(aliveCheckInterval)
                } else {
                    logger.warn("Alive-check is not enabled")
                }
            } else {
                logger.warn("Connected")
            }
        }

        override fun authPacketArrived(reasonCode: Int, properties: MqttProperties?) {
            // don't care?
        }

        override fun messageArrived(topic: String, message: MqttMessage) {
            // just use the listener
        }

        override fun deliveryComplete(token: IMqttToken?) {
            // don't care?
        }
    }

    private val executor by lazy { Executors.newSingleThreadScheduledExecutor() }

    /**
     * Start an "alive check"/heartbeat message. This is a message published to the [KOBOTS_ALIVE] topic.
     *
     * If the connection is lost, the heartbeat will stop and restart when the connection is re-established.
     */
    fun startAliveCheck(intervalSeconds: Long = 30) {
        aliveCheckEnabled.compareAndExchange(false, true)
        if (aliveCheckInterval != intervalSeconds) aliveCheckInterval = intervalSeconds
        logger.warn("Starting alive-check at $intervalSeconds seconds")
        aliveCheckFuture = executor.scheduleAtFixedRate(
            {
                if (mqttClient.isConnected) {
                    publish(KOBOTS_ALIVE, clientName)
                } else {
                    logger.warn("Not connected, skipping alive check")
                }
            },
            intervalSeconds / 2,
            intervalSeconds,
            TimeUnit.SECONDS
        )
        logger.warn("Started alive-check at $intervalSeconds seconds")
    }

    private val lastCheckIn = mutableMapOf<String, ZonedDateTime>()

    /**
     * Listens for `KOBOTS_ALIVE` messages and tracks the last time a message was received from each host. The [listener]
     * is called when a host has not been seen for [deadIntervalSeconds] seconds.
     *
     * TODO this needs re-think
     */
    fun handleAliveCheck(listener: (String) -> Unit = {}, deadIntervalSeconds: Long = 30) {
        // store everybody's last time
        subscribe(KOBOTS_ALIVE) { s: String -> lastCheckIn[s] = ZonedDateTime.now() }

        // check for dead kobots
        val runner = Runnable {
            val now = ZonedDateTime.now()
            lastCheckIn.forEach { (host, lastSeenAt) ->
                if (Duration.between(lastSeenAt, now).seconds > deadIntervalSeconds) listener(host)
            }
        }
        executor.scheduleAtFixedRate(runner, deadIntervalSeconds / 2, deadIntervalSeconds / 2, TimeUnit.SECONDS)
    }

    /**
     * Subscribe to a topic. The [listener] is called when a message is received.
     */
    fun subscribe(topic: String, listener: (String) -> Unit) {
        // assume the users of this will only call it once
        subscribers.compute(topic) { _, list ->
            if (list == null) {
                listOf(listener)
            } else {
                list + listener
            }
        }

        val sub = MqttSubscription(topic, 0)
        // STUPID FUCKING BUG!!!!
        val props = MqttProperties().apply {
            subscriptionIdentifiers = listOf(0)
        }
        val wrapper = IMqttMessageListener { _, message ->
            try {
                listener(message.payload.decodeToString())
            } catch (t: Throwable) {
                logger.error("Subscriber error", t)
            }
        }
        mqttClient.subscribe(arrayOf(sub), null, null, wrapper, props).waitForCompletion()
        logger.warn("Subscribed to topic $topic")
    }

    fun publish(topic: String, payload: String) {
        try {
            mqttClient.publish(topic, MqttMessage(payload.toByteArray())).waitForCompletion()
        } catch (t: Throwable) {
            logger.error("Publisher error", t)
        }
    }

    override fun close() {
        mqttClient.close()
    }

    companion object {
        /**
         * The alive-check topic used by Kobots.
         */
        const val KOBOTS_ALIVE = "kobots/alive"
    }
}
