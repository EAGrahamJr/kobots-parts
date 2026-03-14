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

package crackers.kobots.parts.movement.async

import com.diozero.devices.sandpit.motor.BasicStepperController
import com.diozero.devices.sandpit.motor.BasicStepperMotor
import com.diozero.devices.sandpit.motor.StepperMotorInterface.Direction.BACKWARD
import com.diozero.devices.sandpit.motor.StepperMotorInterface.Direction.FORWARD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration

/**
 * Wraps up a stepper motor for asynchronous movement handling. This utilizes "step-counting" to do the actual
 * movements.
 *
 *  The [stepPause] parameter is provided to introduce a delay between steps to prevent overdriving the stepper motor
 *  or driver circuits (e.g. "slow" response from the hardware).
 *
 * **NOTE** The stepper is _not_ automatically released after movement; it is assumed that holding torque is desired.
 *
 *  @param theStepper The underlying stepper motor to control.
 *  @param stepStyle The stepping style to use (default: [BasicStepperController.StepStyle.SINGLE]).
 *  @param stepPause The pause duration between steps (default: `Duration.ZERO`).
 */
open class AsyncStepper(
    private val theStepper: BasicStepperMotor,
    val stepStyle: BasicStepperController.StepStyle = BasicStepperController.StepStyle.SINGLE,
    private val stepPause: Duration = Duration.ZERO,
) : AsyncActuator {

    protected val mutex = Mutex()

    private val _stepsFromZero = AtomicInteger(0)
    override val current: Int
        get() = _stepsFromZero.get()

    suspend fun moveSteps(steps: Int, elapsedTime: Duration, easingFunction: EasingFunction) = mutex.withLock {
        val whereAmI = _stepsFromZero.get()
        val target = whereAmI + steps
        val totalSteps = abs(steps)
        easeTo(
            start = whereAmI.toFloat(),
            end = target.toFloat(),
            duration = elapsedTime,
            updateFn = { howMany ->
                repeat(howMany.roundToInt()) {
                    stopCheck(steps)
                    val (direction, offset) = if (steps > 0) FORWARD to 1 else BACKWARD to -1
                    withContext(Dispatchers.IO) { theStepper.step(direction, stepStyle) }
                    _stepsFromZero.addAndGet(offset)
                    delay(stepPause)
                }
            },
            easingFn = easingFunction,
            steps = totalSteps,
        )
    }

    override suspend fun softLanding(target: Int, time: Duration) = moveSteps(
        steps = target,
        elapsedTime = time,
        easingFunction = softLanding
    )

    override suspend fun softLaunch(target: Int, time: Duration) = moveSteps(
        steps = target,
        elapsedTime = time,
        easingFunction = softLaunch
    )

    override suspend fun smoothMove(target: Int, time: Duration) = moveSteps(
        steps = target,
        elapsedTime = time,
        easingFunction = smooth
    )

    /**
     * Release the stepper motor, allowing it to be turned freely.
     */
    open fun release() {
        theStepper.release()
    }
}
