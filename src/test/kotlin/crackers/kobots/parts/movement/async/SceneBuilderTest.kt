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
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

class SceneBuilderTest : FunSpec(
    {

        lateinit var rotator: AsyncRotator

        beforeTest {
            rotator = mockk(relaxed = true)
        }

        test("moveTo adds a rotation action and executes it with correct parameters") {
            val builder = sceneBuilder {
                rotator moveTo {
                    angle = 90
                    duration = 1.seconds
                    ease = smooth
                }
            }
            runBlocking {
                builder.play()
            }
            coVerify { rotator.rotateAsync(90, 1.seconds, smooth) }
        }

        test("play executes all queued actions") {
            val rotator2 = mockk<AsyncRotator>(relaxed = true)
            val builder = sceneBuilder {
                rotator moveTo { angle = 45 }
                rotator2 moveTo { angle = 135 }
            }
            runBlocking {
                builder.play()
            }
            coVerify {
                rotator.rotateAsync(45, any(), any())
                rotator2.rotateAsync(135, any(), any())
            }
        }

        test("moveTo uses default duration and ease if not set") {
            val builder = sceneBuilder {
                rotator moveTo { angle = 30 }
            }
            runBlocking {
                builder.play()
            }
            coVerify { rotator.rotateAsync(30, 2.seconds, linear) }
        }

        test("play with no actions does not throw") {
            val builder = SceneBuilder()
            runBlocking {
                builder.play()
            }
            // No exception expected
        }
    })
