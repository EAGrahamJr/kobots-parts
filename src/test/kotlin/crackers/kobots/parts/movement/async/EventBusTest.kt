/*
 * Copyright 2022-2025 by E. A. Graham, Jr.
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

package crackers.kobots.parts.movement.async

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
                delay(100.milliseconds)
                EventBus.publish(event)
            }
            gotMessage.await(1, TimeUnit.SECONDS) shouldBe true
        }
    })
