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

package crackers.kobots.coroutine

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Co-routine stuff for the async functions,
 */
object AppScope {
    @Volatile
    private var numThreads = -1

    private val ioDispatcher: CoroutineDispatcher by lazy {
        if (numThreads > 0) Dispatchers.IO.limitedParallelism(numThreads) else Dispatchers.IO
    }

    val appScope: CoroutineScope by lazy {
        val dispatcher = if (numThreads > 0) Dispatchers.Default.limitedParallelism(numThreads) else Dispatchers.Default
        CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, e ->
            LoggerFactory.getLogger("AppScope Dispatch").error("Uncaught", e)

        }).also { scope ->
            Runtime.getRuntime().addShutdownHook(Thread {
                runBlocking { withTimeoutOrNull(2.seconds) { scope.coroutineContext[Job]!!.cancelAndJoin() } }
            })
        }
    }

    /**
     * Set the number of threads to use for the appScope and ioDispatcher.
     * If not set, the default number of threads will be used. This must be called before any coroutines
     * are launched in the appScope.
     */
    infix fun withThreads(numThreads: Int) {
        LoggerFactory.getLogger("AppScope").warn("Setting number of threads to $numThreads")
        this.numThreads = numThreads
    }

    /**
     * Schedules a [block] of code to run after an [initialDelay], and then periodically with a fixed [delayPeriod]
     * between the end of one execution and the start of the next. The [runCheck] is evaluated before each execution.
     */
    fun scheduleWithFixedDelay(
        initialDelay: Duration,
        delayPeriod: Duration,
        block: () -> Unit,
        runCheck: () -> Boolean = { true },
    ): Job = appScope.launch {
        delay(initialDelay)
        while (isActive && runCheck()) {
            try {
                withContext(ioDispatcher) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoggerFactory.getLogger("AppScope").warn("Periodic task failed", e)
            }
            delay(delayPeriod)
        }
    }
}
