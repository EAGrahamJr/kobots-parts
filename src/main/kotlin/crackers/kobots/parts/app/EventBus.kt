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

package crackers.kobots.parts.app

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher

interface KobotsMessage
interface KobotsAction : KobotsMessage {
    val interruptable: Boolean
}

interface KobotsEvent : KobotsMessage

private val eventBusMap = ConcurrentHashMap<String, SubmissionPublisher<KobotsMessage>>()

/**
 * Receives an item from a topic. See [joinTopic]
 */
fun interface KobotsSubscriber<T : KobotsMessage> {
    fun receive(msg: T)
}

/**
 * Wraps the subscriber in all the extra stuff necessary. Requests [batchSize] items.
 */
private class KobotsSubscriberDecorator<T : KobotsMessage>(val listener: KobotsSubscriber<T>, val batchSize: Long) :
    Flow.Subscriber<T> {

    private val logger by lazy { LoggerFactory.getLogger("EventSubscriber") }

    private lateinit var mySub: Flow.Subscription
    override fun onSubscribe(subscription: Flow.Subscription) {
        mySub = subscription
        mySub.request(batchSize)
    }

    override fun onNext(item: T) {
        try {
            listener.receive(item)
        } catch (t: Throwable) {
            logger.error("Error in bus -- unable to receive message", t)
        }
        mySub.request(batchSize)
    }

    override fun onError(throwable: Throwable?) {
        logger.error("Error in bus", throwable)
    }

    override fun onComplete() {
//        TODO("Not yet implemented")
    }
}

/**
 * Get items (default 1 at a time) asynchronously.
 */
fun <T : KobotsMessage> joinTopic(topic: String, listener: KobotsSubscriber<T>, batchSize: Long = 1) {
    getPublisher<T>(topic).subscribe(KobotsSubscriberDecorator(listener, batchSize))
}

/**
 * Stop getting items.
 */
@Suppress("UNCHECKED_CAST")
fun <T : KobotsMessage> leaveTopic(topic: String, listener: KobotsSubscriber<T>) {
    getPublisher<T>(topic).subscribers.removeIf { (it as KobotsSubscriberDecorator<T>).listener == listener }
}

/**
 * Publish one or more [items] to a [topic].
 */
fun <T : KobotsMessage> publishToTopic(topic: String, vararg items: T) {
    publishToTopic(topic, items.toList())
}

/**
 * Publish a collection of [items] to a [topic]
 */
fun <T : KobotsMessage> publishToTopic(topic: String, items: Collection<T>) {
    val publisher = getPublisher<T>(topic)
    items.forEach { item -> publisher.submit(item) }
}

@Suppress("UNCHECKED_CAST")
private fun <T : KobotsMessage> getPublisher(topic: String) =
    eventBusMap.computeIfAbsent(topic) { SubmissionPublisher<KobotsMessage>() } as SubmissionPublisher<T>

// specific messages ==================================================================================================
class EmergencyStop() : KobotsAction {
    override val interruptable: Boolean = false
}

val allStop = EmergencyStop()
