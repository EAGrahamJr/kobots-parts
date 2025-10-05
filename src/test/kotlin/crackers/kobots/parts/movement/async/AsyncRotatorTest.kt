package crackers.kobots.parts.movement.async

import com.diozero.api.ServoDevice
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

class AsyncServoRotatorTest : FunSpec(
    {

        val servoRange = 0..180
        val defaultPhysicalRange = 0..180

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
        }

        test("init sets servo angle to servoRange.first") {
            val rotator = AsyncServoRotator(servo, defaultPhysicalRange, servoRange)
            rotator.init()
            verify { servo.angle = servoRange.first.toFloat() }
        }

        test("rotate calls fail") {
            val rotator = AsyncServoRotator(servo, defaultPhysicalRange, servoRange)
            shouldThrow<UnsupportedOperationException> {
                rotator.rotateTo(90)
            }
        }
        test("angle out of range throws exception") {
            val rotator = AsyncServoRotator(servo, defaultPhysicalRange, servoRange)
            shouldThrow<IllegalArgumentException> {
                runBlocking {
                    rotator.rotateAsync(200, 1.seconds)
                }
            }
        }
        test("moves servo to correct position") {
            val rotator = AsyncServoRotator(servo, defaultPhysicalRange, servoRange)
            every { servo.angle } returns 0f

            runBlocking {
                rotator.rotateAsync(180, .01.seconds) // Should clamp to 180
            }

            verify(atLeast = 1) {
                servo.angle = 180f
            }
            currentServoAngle.captured shouldBe 180f
        }
        test("physical range is twice the servo range") {
            val physicalRange = 0..360
            val rotator = AsyncServoRotator(servo, physicalRange, servoRange)
            every { servo.angle } returns 0f

            runBlocking {
                rotator.rotateAsync(90, .01.seconds)
            }

            verify(atLeast = 1) {
                servo.angle = 45f
            }
            currentServoAngle.captured shouldBe 45f
        }
        test("physical range is half the servo range") {
            val physicalRange = 0..90
            val rotator = AsyncServoRotator(servo, physicalRange, servoRange)
            every { servo.angle } returns 0f

            runBlocking {
                rotator.rotateAsync(45, .01.seconds)
            }

            verify(atLeast = 1) {
                servo.angle = 90f
            }
            currentServoAngle.captured shouldBe 90f
        }
    })
