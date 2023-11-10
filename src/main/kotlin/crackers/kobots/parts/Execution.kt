package crackers.kobots.parts

import java.time.Duration
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.time.toJavaDuration

/*
 * Extension functions for executors
 */
/**
 * Run a thing at a fixed rate extension function for better readability. Granularity is milliseconds.
 */
fun ScheduledExecutorService.scheduleAtFixedRate(intialDelay: Duration, period: Duration, command: Runnable) =
    scheduleAtFixedRate(command, intialDelay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS)

/**
 * Run a thing at a fixed delay extension function for better readability. Granularity is milliseconds.
 */
fun ScheduledExecutorService.scheduleWithFixedDelay(intialDelay: Duration, delay: Duration, command: Runnable) =
    scheduleWithFixedDelay(command, intialDelay.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS)

/**
 * Run a thing at a fixed rate extension function for better readability. Granularity is milliseconds.
 */
fun ScheduledExecutorService.scheduleAtFixedRate(
    intialDelay: kotlin.time.Duration,
    period: kotlin.time.Duration,
    command: Runnable
) =
    scheduleAtFixedRate(intialDelay.toJavaDuration(), period.toJavaDuration(), command)

/**
 * Run a thing at a fixed delay extension function for better readability. Granularity is milliseconds.
 */
fun ScheduledExecutorService.scheduleWithFixedDelay(
    intialDelay: kotlin.time.Duration,
    delay: kotlin.time.Duration,
    command: Runnable
) =
    scheduleWithFixedDelay(intialDelay.toJavaDuration(), delay.toJavaDuration(), command)
