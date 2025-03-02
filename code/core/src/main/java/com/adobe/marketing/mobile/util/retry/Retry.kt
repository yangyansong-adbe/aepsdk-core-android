/*
  Copyright 2025 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile.util.retry

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

object Retry {
    internal fun <T> createExecutor(
        config: RetryConfig = RetryConfig.Builder<Any?>().build()
    ): Executor<T> {
        // keep a weak reference inside Retry??
        return ExecutorImpl(config)
    }

    private class ExecutorImpl<T>(val config: RetryConfig) : Executor<T> {

        var retryOnResultFunction: (T?) -> Boolean = { _: T? -> false }
        var retryIntervalOnResultFunction: (T?) -> Long = { _: T? -> 0 }

        override suspend fun execute(block: suspend () -> T?): T? {
            val initialInterval = config.initialInterval
            val intervalFunction = config.intervalFunction
            val maxInterval = config.maxInterval
            val maxAttempts = config.maxAttempts
            val executionTimeoutInMilliseconds = config.executionTimeoutInMilliseconds
            var attempt = 0
            var lastInterval = initialInterval

            repeat(maxAttempts) {
                attempt++
                try {
                    val result: T? = withTimeout(executionTimeoutInMilliseconds) {
                        block()
                    }
                    if (!retryOnResultFunction(result)) {
                        return result
                    }
                } catch (e: Throwable) {
                    return null
                }

                lastInterval = if (lastInterval >= maxInterval) {
                    maxInterval
                } else {
                    intervalFunction(initialInterval, attempt, lastInterval)
                }
                delay(lastInterval)
            }
            return null
        }

        override fun retryOnResult(block: (T?) -> Boolean): Executor<T> {
            retryOnResultFunction = block
            return this
        }

        override fun retryIntervalOnResult(block: (T?) -> Long): Executor<T> {
            retryIntervalOnResultFunction = block
            return this
        }

        override fun cancel() {}
        override fun monitorRetry(block: (attempts: Int, lastIntervalWithJitter: Long) -> Unit): Executor<T> {
            return this
        }
    }
}