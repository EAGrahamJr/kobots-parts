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

package crackers.kobots.parts.movement

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeBetween
import io.kotest.matchers.shouldBe
import kotlin.math.roundToInt

class MovementsTest : FunSpec(
    {
        context("gearedAngleTable") {
            test("one-to-one") {
                val table = gearedAngleTable(0..180, 0..180)
                for (i in 0..180) {
                    table[i] shouldBe i
                }
            }
            test("half-size") {
                val table = gearedAngleTable(0..180, 0..90)
                for (i in 0..90) {
                    val t = (i / 2.0)
                    // going to bounde a little
                    table[i]!!.shouldBeBetween(t.toInt(), t.roundToInt())
                }
            }
            test("inverse mapping") {
                val table = gearedAngleTable(0..180, IntRange(180, 0))
                // should be reversed
                table[180] shouldBe 0
                table[90] shouldBe 90
                table[0] shouldBe 180
            }
        }
    })
