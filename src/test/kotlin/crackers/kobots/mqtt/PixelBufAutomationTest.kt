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

package crackers.kobots.mqtt

import crackers.kobots.devices.lighting.PixelBuf
import crackers.kobots.devices.lighting.WS2811
import crackers.kobots.mqtt.homeassistant.LightCommand
import crackers.kobots.mqtt.homeassistant.PixelBufController
import crackers.kobots.parts.GOLDENROD
import crackers.kobots.parts.colorIntervalFromHSB
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.future.await
import java.awt.Color

/**
 * TODO fill this in
 */
class PixelBufAutomationTest : FunSpec(autoTest()) {
}

fun autoTest(): FunSpec.() -> Unit = {
    val mockBuf = mockk<PixelBuf>()
    val pixels = Array(5) { WS2811.PixelColor(Color.BLACK, brightness = 0f) }
    beforeSpec {
        every { mockBuf.get() } returns pixels
        every { mockBuf.fill(any<Color>()) }.answers {
            val b = pixels[0].brightness
            pixels.fill(WS2811.PixelColor(arg(0), brightness = b))
        }
        every { mockBuf.fill(any<WS2811.PixelColor>()) }.answers {
            pixels.fill(arg(0))
        }
        every { mockBuf.get(any()) }.answers { pixels[arg(0)] }
    }


    test("turn on with color") {
        val controller = PixelBufController(mockBuf)
        val command = LightCommand(true, 50, GOLDENROD)

        controller.set(command)
        pixels.all { it.color == GOLDENROD && it.brightness == .5f } shouldBe true
    }

    fun cycleNeo(b: PixelBuf) {
        colorIntervalFromHSB(0f, 359f, 360).map { WS2811.PixelColor(it, brightness = .3f) }.forEach {
            b.fill(it)
        }
    }

    test("execute effects") {
        val controller = PixelBufController(mockBuf, mapOf("foo" to ::cycleNeo))
        val f = controller.exec("foo")
        f.await()
        verify(atLeast = 360) { mockBuf.fill(any<WS2811.PixelColor>()) }
    }

}
