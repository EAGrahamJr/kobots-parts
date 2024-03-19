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
import com.diozero.devices.sandpit.motor.StepperMotorInterface.Direction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.shouldNotBeGreaterThan
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkClass
import io.mockk.verify
import kotlin.math.roundToInt

/**
 * Tests the linear actuators.
 */
class LinearTest : FunSpec(
    {
        context("Servo") {
            @MockK
            lateinit var mockServo: ServoDevice
            var currentServoAngle = 0f
            beforeTest {
                mockServo = mockkClass(ServoDevice::class)
                every { mockServo.angle } answers { currentServoAngle }
                every { mockServo.angle = any() } answers { currentServoAngle = arg(0) }
            }

            /**
             * Test the servo linear actuator with the max angle set to 180 and the minimum angle set to 0, moves about 100
             * degrees when the actuator is moved to 60 percent
             */
            test("full arc, extend to 60%") {
                val linear = ServoLinearActuator(mockServo, 0f, 180f)
                currentServoAngle = 0f
                while (!linear.extendTo(60)) {
                    // just count things
                }
                verify(atMost = (.6 * 180).roundToInt()) {
                    mockServo.angle = any()
                }
            }

            /**
             * Test a linear actuator with a servo motor with the home angle set to 100 degrees and the max angle set
             * to 0 degrees. The servo is at 75 degrees and test that moving to the 0% position moves the motor the
             * appropriate number of steps.
             */
            test("limited range, and in reverse, to 75%") {
                val linear = ServoLinearActuator(mockServo, 100f, 0f)
                currentServoAngle = 75f

                while (!linear.extendTo(0)) {
                    // just count things
                }
                verify(atMost = 25) {
                    mockServo.angle = any()
                }
                currentServoAngle shouldNotBeGreaterThan 100f
            }
        }
        context("Stepper") {
            @MockK
            lateinit var mockStepper: BasicStepperMotor
            beforeTest {
                mockStepper = mockkClass(BasicStepperMotor::class)
                every { mockStepper.step(any(), any()) } answers {
                    // TODO do we need anything here?
                }
            }

            /**
             * Test a linear actuator with a stepper motor so that there are 200 maximum steps for the actuator; move
             * the actuator to 75% and verify the result is 150 steps.
             */
            test("200 steps, forward to 50%") {
                val linear = StepperLinearActuator(mockStepper, 200)
                while (!linear.extendTo(75)) {
                    // just count things
                }
                verify(exactly = 150) { mockStepper.step(Direction.FORWARD, any()) }
            }

            /**
             * Test a stepper actuator with maximum steps of 50, reversed; the current position is at 50%, move the
             * actuator to 33% and verify that the result is steps in a forward direction (becuause it's going
             * backwards and the stepper is reversed).
             */
            test("50 steps max, backwards to 33% from 50%") {
                val linear = StepperLinearActuator(mockStepper, 50, true)
                while (!linear.extendTo(50)) {
                    // just count things
                }
                while (!linear.extendTo(33)) {
                    // just count things
                }
                verify(exactly = 25) { mockStepper.step(Direction.BACKWARD, any()) }
                verify(exactly = 8) { mockStepper.step(Direction.FORWARD, any()) }
            }
        }
    }
)
