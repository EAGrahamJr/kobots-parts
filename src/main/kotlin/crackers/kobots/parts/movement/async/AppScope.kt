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

package crackers.kobots.parts.movement.async

import kotlinx.coroutines.*
import kotlin.time.Duration

/**
 * Co-routine stuff for the async functions,
 */
object AppScope {
    val appScope: CoroutineScope by lazy {
        CoroutineScope(Dispatchers.Default + SupervisorJob()).also {
            Runtime.getRuntime().addShutdownHook(Thread {
                it.coroutineContext[Job]?.cancel()
            })
        }
    }


    fun scheduleWithFixedDelay(
        initialDelay: Duration,
        delayPeriod: Duration,
        block: () -> Unit
    ): Job {
        return appScope.launch {
            delay(initialDelay)
            while (isActive) {
                with(Dispatchers.IO) {
                    block()
                }
                delay(delayPeriod)
            }
        }
    }
}
