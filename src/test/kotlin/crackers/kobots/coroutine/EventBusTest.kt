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

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.runBlocking
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class EventBusTest : FunSpec(
    {
        test("EventBus registers and notifies listeners") {
            val gotMessage = CountDownLatch(1)
            EventBus.subscribe<KobotsButtonEvent>("TestButton") { event ->
                event.name shouldBe "TestButton"
                gotMessage.countDown()
            }
            val event = KobotsButtonEvent("TestButton")
            runBlocking {
                delay(50.milliseconds)
                EventBus.publish(event)
            }
            gotMessage.await(200, TimeUnit.MILLISECONDS) shouldBe true
        }
        test("multiple subscribers get all events") {
            val latch1 = CountDownLatch(1)
            val latch2 = CountDownLatch(1)
            EventBus.subscribe<KobotsButtonEvent>("MultiTest") {
                latch1.countDown()
            }
            EventBus.subscribe<KobotsButtonEvent>("MultiTest") {
                latch2.countDown()
            }
            val event = KobotsButtonEvent("MultiTest")
            runBlocking {
                delay(50.milliseconds)
                EventBus.publish(event)
            }
            latch1.await(200, TimeUnit.MILLISECONDS) shouldBe true
            latch2.await(200, TimeUnit.MILLISECONDS) shouldBe true
        }
        test("same name, different event types") {
            val latchA = CountDownLatch(2)
            val latchB = CountDownLatch(1)
            // this should only receive one, but we'll "expect" two to show they are separate
            EventBus.subscribe<KobotsButtonEvent>("SameName") {
                latchA.countDown()
            }
            EventBus.subscribe<KobotSensorEvent<String>>("SameName") {
                latchB.countDown()
            }
            val eventA = KobotsButtonEvent("SameName")
            val eventB = KobotSensorEvent("SameName", "Hello")
            runBlocking {
                delay(50.milliseconds)
                EventBus.publish(eventA)
                EventBus.publish(eventB)
            }
            latchA.await(200, TimeUnit.MILLISECONDS) shouldBe false
            latchB.await(200, TimeUnit.MILLISECONDS) shouldBe true
        }
    })
