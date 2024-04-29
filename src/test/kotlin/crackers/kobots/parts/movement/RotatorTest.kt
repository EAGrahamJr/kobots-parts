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

package crackers.kobots.parts.movement

import com.diozero.api.ServoDevice
import com.diozero.devices.sandpit.motor.BasicStepperMotor
import com.diozero.devices.sandpit.motor.StepperMotorInterface
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkClass
import io.mockk.verify

private fun Rotator.test(angle: Int) {
    while (!rotateTo(angle)) {
        // just count things
    }
}

/**
 * Testing the rotator classes.
 */
class RotatorTest : FunSpec(
    {
        /**
         * Test steppers.
         */
        context("Stepper") {
            @MockK
            lateinit var mockStepper: BasicStepperMotor

            beforeTest {
                mockStepper = mockkClass(BasicStepperMotor::class)
                every { mockStepper.step(any(), any()) } answers { }
            }

            /**
             * The gear ratio is 1:1 and the stepper motor has 200 steps per rotation. The rotator is set to move from
             * 0 to 360 degrees, and it's starting position is assumed to be 0 degrees. The target angle is 83 degrees.
             */
            test("max steps < 360, 1:1 gear ratio") {
                every { mockStepper.stepsPerRotation } answers { 200 }
                val rotor = BasicStepperRotator(mockStepper)

                rotor.test(83)
                verify(exactly = 46) {
                    mockStepper.step(StepperMotorInterface.Direction.FORWARD, any())
                }
            }

            /**
             * With a gear ratio of 1:1.11 and the motor has 200 steps per rotation. The rotator is to be moved 90
             * degrees in a forward direction. The stepper is currently at 0 degrees.
             */
            test("max steps < 360, 1:11:1 gear ratio") {
                every { mockStepper.stepsPerRotation } answers { 200 }
                val rotor = BasicStepperRotator(mockStepper, 1.11f)

                rotor.test(90)
                verify(exactly = 45) {
                    mockStepper.step(StepperMotorInterface.Direction.FORWARD, any())
                }
            }

            /**
             * Motor has 2048 steps per rotation and a gear ratio of 1.28 (simulated from real-world). The target is
             * 90 degrees.
             */
            test("max steps > 360, 1.28:1 gear ratio") {
                every { mockStepper.stepsPerRotation } answers { 2048 }
                val rotor = BasicStepperRotator(mockStepper, 1.28f)

                rotor.test(90)
                verify(exactly = 400) {
                    mockStepper.step(StepperMotorInterface.Direction.FORWARD, any())
                }
            }
        }

        /**
         * Test servos.
         */
        context("Servo") {
            @MockK
            lateinit var mockServo: ServoDevice
            var currentAngle = 0f
            beforeTest {
                mockServo = mockkClass(ServoDevice::class)
                every { mockServo.angle } answers { currentAngle }
                every { mockServo.angle = any() } answers { currentAngle = args[0] as Float }
            }

            /**
             * The physical range is 0 to 90 degrees and the servo has a range of 0 to 180.
             * Assume the servo angle is at 42 degrees, physical 21, and the target is 87 physical degrees.
             */
            test("2:1 gear ratio, start at 21, move to 87") {
                currentAngle = 42f
                val rotor = ServoRotator(mockServo, 0..90, 0..180).apply {
                    where = 21
                }
                rotor.test(87)
                verify(atLeast = 131) {
                    mockServo.angle = any()
                }
                currentAngle shouldBe 174f
            }

            /**
             * The physical range is -10 to 90 degrees and the servo has a range of 180 to 0.
             * The physical starting point is 0, servo at 162.
             */
            test("ratio reversed") {
                currentAngle = 162f
                val rotor = ServoRotator(mockServo, -10..90, IntRange(180, 0)).apply {
                    where = 0
                }
                rotor.test(45)
                currentAngle shouldBe 81f
                verify(atLeast = 80) {
                    mockServo.angle = any()
                }
            }

            /**
             * The physical range is 0 to 90 and the servo has a range of 0 to 180. The delta is 5 degrees. The servo
             * is currently at an angle of 30 degrees and the target is 32 degrees. Verify the servo does not move.
             */
            test("delta prevents movement") {
                currentAngle = 60f
                val rotor = ServoRotator(mockServo, 0..90, 0..180, 5).apply {
                    where = 30
                }

                rotor.test(32)
                verify(exactly = 0) {
                    mockServo.angle = any()
                }
                currentAngle shouldBe 60f
            }

            /**
             * The physical range is 1 to 133 and the servo range is 0 to 200. This tests that rounding errors will
             * still allow the rotator to advance by 1 physical degree -- this was discovered in previous iteration
             * trying to "step" from 67 to 68+ degrees.
             */
            test("incremental movements") {
                val rotor = ServoRotator(mockServo, 0..133, 0..200)
                // move it to 68 and then attempt to iterate to 69 (should take 2 moves)
                rotor.test(68)

                val startingAngle = rotor.current()
                var t = false
                var count = 0
                while (rotor.current() == startingAngle && !t && count < 10) {
                    val c = rotor.current()
                    t = rotor.rotateTo(c + 1)
                    count++
                }
                count shouldBe 2
            }
        }
    }
)
