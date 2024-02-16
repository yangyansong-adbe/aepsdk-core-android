package com.adobe.marketing.mobile.util.resilience

internal inline fun retryConfig(config: RetryConfig.Builder<Any?>.() -> Unit): RetryConfig {
    return RetryConfig.Builder<Any?>().apply(config).build()
}

internal typealias NextInterval = (initialInterval: Long, attempt: Int, lastInterval: Long) -> Long

fun fixedWaitInterval(): NextInterval {
    return { _, _, lastInterval -> lastInterval }
}

fun exponentialWaitInterval(): NextInterval {
    return { _, _, lastInterval -> lastInterval * 2 }
}

fun fibonacciWaitInterval(): NextInterval {
    return { initialInterval, attempt, _ ->
        fibonacci(
            attempt,
            initialInterval,
            initialInterval + 1
        )
    }
}

private tailrec fun fibonacci(n: Int, a: Long = 0, b: Long = 1): Long =
    when (n) {
        0 -> a
        1 -> b
        else -> fibonacci(n - 1, b, a + b)
    }

fun linearWaitInterval(): NextInterval {
    return { initialInterval, _, lastInterval -> lastInterval + initialInterval }
}

internal class RetryConfig private constructor() {
    internal var intervalFunction: NextInterval = fixedWaitInterval()
        private set
    var initialInterval: Long = 1000L
        private set

    var maxInterval: Long = 1000000L // 1000000 milliseconds = 16 minutes and 40 seconds
        private set
    var maxAttempts: Int = -1
        private set
    var useJitter: Boolean = false
        private set
    var executionTimeoutInSeconds: Int = 10
        private set
    var cancelInMilliseconds: Long = -1
        private set

    internal class Builder<T> {
        val config = RetryConfig()

        fun build(): RetryConfig {
            return config
        }

        fun intervalFunction(
            intervalFunction: NextInterval,
            initialInterval: Long = 1000L,
            maxInterval: Long = 0
        ) {
            config.intervalFunction = intervalFunction
            config.initialInterval = initialInterval
            config.maxInterval = maxInterval
        }

        fun cancelRetry(inMilliseconds: Long) {
            if (inMilliseconds > 0) {
                config.cancelInMilliseconds = inMilliseconds
            }
        }

        fun cancelInMinutes(minutes: Int) {
            if (minutes > 0) {
                config.cancelInMilliseconds = minutes * 60 * 1000L
            }
        }
        fun cancelInSeconds(seconds: Int) {
            if (seconds > 0) {
                config.cancelInMilliseconds = seconds * 1000L
            }
        }

        fun maxAttempts(attempts: Int) {
            config.maxAttempts = attempts
        }

        fun useJitter(flag: Boolean) {
            config.useJitter = flag
        }

        fun executionTimeoutInSeconds(seconds: Int) {
            config.executionTimeoutInSeconds = seconds
        }

    }
}

