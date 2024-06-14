package crackers.kobots.parts

import com.diozero.util.SleepUtil
import java.time.Duration
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.time.toJavaDuration

/*
 * Extension functions for executors
 */

/**
 * Run a thing at a fixed rate extension function for better readability. Granularity is **nanoseconds**.
 */
fun ScheduledExecutorService.scheduleAtFixedRate(intialDelay: Duration, period: Duration, command: () -> Unit) =
    scheduleAtFixedRate(command, intialDelay.toNanos(), period.toNanos(), TimeUnit.NANOSECONDS)

/**
 * Run a thing at a fixed delay extension function for better readability. Granularity is **nanoseconds**.
 */
fun ScheduledExecutorService.scheduleWithFixedDelay(intialDelay: Duration, delay: Duration, command: () -> Unit) =
    scheduleWithFixedDelay(command, intialDelay.toNanos(), delay.toNanos(), TimeUnit.NANOSECONDS)

/**
 * Run a thing at a fixed rate extension function for better readability. Granularity is **nanoseconds**.
 */
fun ScheduledExecutorService.scheduleAtFixedRate(
    intialDelay: kotlin.time.Duration,
    period: kotlin.time.Duration,
    command: () -> Unit
) = scheduleAtFixedRate(intialDelay.toJavaDuration(), period.toJavaDuration(), command)

/**
 * Rate and delay are the same.
 */
fun ScheduledExecutorService.scheduleAtRate(rate: kotlin.time.Duration, command: () -> Unit) =
    scheduleAtFixedRate(rate, rate, command)

/**
 * Run a thing at a fixed delay extension function for better readability. Granularity is **nanoseconds**.
 */
fun ScheduledExecutorService.scheduleWithFixedDelay(
    intialDelay: kotlin.time.Duration,
    delay: kotlin.time.Duration,
    command: () -> Unit
) = scheduleWithFixedDelay(intialDelay.toJavaDuration(), delay.toJavaDuration(), command)

/**
 * Delay to start and delay between are the same.
 */
fun ScheduledExecutorService.scheduleWithDelay(delay: kotlin.time.Duration, command: () -> Unit) =
    scheduleWithFixedDelay(delay, delay, command)

/**
 * Extension function to use a duration for sleeping.
 */
fun Duration.sleep() = SleepUtil.busySleep(toNanos())

/**
 * Extension function to use a duration for sleeping.
 */
fun kotlin.time.Duration.sleep() = SleepUtil.busySleep(inWholeNanoseconds)
