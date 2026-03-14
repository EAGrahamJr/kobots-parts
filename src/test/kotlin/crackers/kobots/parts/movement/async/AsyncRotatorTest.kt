package crackers.kobots.parts.movement.async

import com.diozero.api.ServoDevice
import com.diozero.api.ServoTrim
import com.diozero.devices.sandpit.motor.BasicStepperMotor
import com.diozero.devices.sandpit.motor.StepperMotorInterface
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.mockk.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AsyncServoRotatorTest : FunSpec(
    {

        context("servo") {
            val servoRange = ServoTrim.TOWERPRO_SG90.minAngle..ServoTrim.TOWERPRO_SG90.maxAngle
            val defaultPhysicalRange = servoRange

            lateinit var servo: ServoDevice
            var currentServoAngle = slot<Float>()

            beforeTest {
                currentServoAngle.clear()
                servo = mockk(relaxed = true)
                every {
                    servo.angle = capture(currentServoAngle)
                } just Runs
                every {
                    servo.angle
                } returns
                    if (currentServoAngle.isCaptured) currentServoAngle.captured else 0f
                every {
                    servo.trim
                } returns ServoTrim.TOWERPRO_SG90
            }

            test("init sets servo angle to servoRange.first") {
                val rotator = AsyncServoRotator(servo, defaultPhysicalRange, servoRange)
                rotator.init()
                verify { servo.angle = servoRange.first.toFloat() }
            }

            test("angle out of range throws exception") {
                val rotator = AsyncServoRotator(servo, defaultPhysicalRange, servoRange)
                shouldThrow<IllegalArgumentException> {
                    rotator.rotateAsync(200, 10.milliseconds)
                }
            }
            test("moves servo to correct position") {
                val rotator = AsyncServoRotator(servo, defaultPhysicalRange, servoRange)

                rotator.rotateAsync(180, 10.milliseconds) // Should clamp to 180

                verify(atLeast = 1) {
                    servo.angle = 180f
                }
                currentServoAngle.captured shouldBe 180f
            }
            test("physical range is twice the servo range") {
                val physicalRange = 0..360
                val rotator = AsyncServoRotator(servo, physicalRange, servoRange)


                rotator.rotateAsync(90, 10.milliseconds)

                verify(atLeast = 1) {
                    servo.angle = 45f
                }
                currentServoAngle.captured shouldBe 45f
            }
            test("physical range is half the servo range") {
                val physicalRange = 0..90
                val rotator = AsyncServoRotator(servo, physicalRange, servoRange)


                rotator.rotateAsync(45, .01.seconds)

                verify(atLeast = 1) {
                    servo.angle = 90f
                }
                currentServoAngle.captured shouldBe 90f
            }
        }

        xcontext("stepper") {
            withData(
                1024, 2048, 4096
            ) { stepsPerRotation ->
                val mockStepper = mockk<BasicStepperMotor>(relaxed = true)
                every {
                    mockStepper.stepsPerRotation
                } answers {
                    stepsPerRotation.toLong()
                }

                val stepperRotator = AsyncStepperRotator(mockStepper)

                test("move to positive angle") {
                    val target = Arb.int(0, 360).next()
                    val targetSteps = 5

                    stepperRotator.rotateAsync(target, 500.milliseconds)

                    verify(atLeast = targetSteps) {
                        mockStepper.step(any(), any())
                    }
                    stepperRotator.current shouldBe target

                }
                test("move to positive, then backwards but non-negative") {
                    val firstTarget = Arb.int(0, 360).next()
                    val secondTarget = Arb.int(0, firstTarget).next()

                    val firstTargetSteps = 5
                    val secondTargetSteps = 6

                    stepperRotator.rotateAsync(firstTarget, 500.milliseconds)
                    stepperRotator.rotateAsync(secondTarget, 500.milliseconds)

                    verify(atLeast = firstTargetSteps) {
                        mockStepper.step(any(), any())
                    }
                    verify(atLeast = (firstTargetSteps - secondTargetSteps)) {
                        mockStepper.step(any(), any())
                    }
                    stepperRotator.current shouldBe secondTarget

                }
                test("move to negative angle") {
                    val target = Arb.int(-360, -1).next()
                    val positiveTarget = 360 + target
                    val targetSteps = 5
                    stepperRotator.rotateAsync(target, 1.seconds)
                    verify(atLeast = targetSteps) {
                        mockStepper.step(any(), any())
                    }
                    stepperRotator.current shouldBe positiveTarget
                }
            }

            test("copy of real-world from RotatorTest") {
                val mockStepper = mockk<BasicStepperMotor>(relaxed = true)
                every { mockStepper.stepsPerRotation } answers { 2048 }

                val rotor = AsyncStepperRotator(mockStepper, .78f)

                rotor.rotateAsync(90, 500.milliseconds)
                verify(atLeast = 398, atMost = 400) {
                    mockStepper.step(StepperMotorInterface.Direction.FORWARD, any())
                }
                rotor.current shouldBe 90
            }
        }
    })
