package com.adobe.marketing.mobile.util.resilience

import kotlinx.coroutines.delay


internal object Retry {
    internal fun <T> createExecutor(
        config: RetryConfig = RetryConfig.Builder<Any?>().build()
    ): Executor<T> {
        // keep a weak reference inside Retry??
        return ExecutorImpl(config)
    }

    private class ExecutorImpl<T>(val config: RetryConfig) : Executor<T> {

        val DEFAULT_RANDOIZED_FACTOR = 0.5
        var retryOnResultFunction: (T?) -> Boolean = { _: T? -> false }
        var retryOnExceptionFunction: (Exception) -> Boolean = { _: Exception -> false }
        var resolveThrowableFunction: (Throwable) -> T? = { _: Throwable -> null }
        var retryIntervalOnResultFunction: (T?) -> Long = { _: T? -> 0 }


        //TODO: this is the jitter formula used by Polly, will do more investigation and change it later
        private fun randomize(current: Double, randomizationFactor: Double): Double {
            val delta = randomizationFactor * current
            val min = current - delta
            val max = current + delta
            return min + Math.random() * (max - min + 1)
        }

        override suspend fun execute(block: () -> T?): T? {
            val initialInterval = config.initialInterval
            val intervalFunction = config.intervalFunction
            val maxInterval = config.maxInterval
            var attempt = 0
            var lastInterval = initialInterval
            while (true) {
                attempt++

                try {
                    val result = block()
                    if (!retryOnResultFunction(result)) {
                        return result
                    }
                } catch (e: Throwable) {
                    when (e) {
                        is Exception -> {
                            if (!retryOnExceptionFunction(e)) {
                                return null
                            }
                        }
                        else -> return resolveThrowableFunction(e)
                    }
                }


                lastInterval = if (lastInterval >= maxInterval) {
                    maxInterval
                }else{
                    intervalFunction(initialInterval, attempt, lastInterval)
                }

                if (config.useJitter) {
                    val interval = randomize(lastInterval.toDouble(), DEFAULT_RANDOIZED_FACTOR).toLong()
                    delay(interval)
                }else{
                    delay(lastInterval)
                }
            }
        }

        override fun retryOnException(block: (Exception) -> Boolean): Executor<T> {
            retryOnExceptionFunction = block
            return this
        }

        override fun retryOnResult(block: (T?) -> Boolean): Executor<T> {
            retryOnResultFunction = block
            return this
        }

        override fun retryIntervalOnResult(block: (T?) -> Long): Executor<T> {
            retryIntervalOnResultFunction = block
            return this
        }

        override fun resolveThrowable(block: (Throwable) -> T?): Executor<T> {
            resolveThrowableFunction = block
            return this
        }

        override fun cancel() {}
    }

}