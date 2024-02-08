package com.adobe.marketing.mobile.internal.resilience

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class RetryTests {

    private val customIntervalFunction = { initialInterval: Long, attempt: Int, lastInterval: Long ->
        val nextInterval = initialInterval * attempt
        if (nextInterval > lastInterval) {
            lastInterval
        } else {
            nextInterval
        }
    }
    @Test
    fun testRetry() {
        val config = retryConfig {
            cancelInMinutes(5)
            maxAttempts(3)
            useJitter(true)
            executionTimeoutInSeconds(10)
            intervalFunction(fixedWaitInterval(), 1000L, 60000L)
            intervalFunction(exponentialWaitInterval(), 1000L, 60000L)
            intervalFunction(fibonacciWaitInterval(), 1000L, 60000L)
            intervalFunction(customIntervalFunction, 1000L, 60000L)
        }
//        val defaultConfig = retryConfig {}
        runBlocking {
            val executor = Retry.createExecutor<String?>(config)
                .retryOnException {
                    return@retryOnException true
                }
                .retryOnResult {
                    return@retryOnResult true
                }
                .retryIntervalOnResult {
                    // for the use case when the http response is 429/503/301, we extract the "Retry-After" value in the response header and use it as the next retry interval
                    // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Retry-After
                    return@retryIntervalOnResult 1000L
                }
                .resolveThrowable {
                    return@resolveThrowable null
                }

            val result = executor.execute {
                // run your code here
                return@execute "Hello World"
            }

            // handle result here

            // cancel the executor if needed
            executor.cancel()

        }

    }

    @Test
    fun testRetrySimple() {
        val config = retryConfig {
            intervalFunction(fixedWaitInterval(), 5000L)
        }
        var counter = -1
        runBlocking {
            val executor = Retry.createExecutor<String?>(config)
                .retryOnResult {
                    return@retryOnResult it?.equals("retry") ?: false
                }

            val result = executor.execute {
                counter++
                println("counter: $counter")
                if (counter > 0) {
                    return@execute "Hello World"
                }
                return@execute "retry"
            }

            // handle result here
            assertEquals("Hello World", result)
//            executor.cancel()

        }

    }

    @Test
    fun testCoroutineScope(){
        runBlocking {
            val result = CoroutineScope(Dispatchers.IO).launch {
                testSuspendFunction()
                println("Done ...")
            }
            println("Result: $result")
            delay(2000L)
        }
    }

    private suspend fun testSuspendFunction(){
        println("Testing suspend function")
        delay(1000L)
        println("Testing suspend function111")
    }

    @Test
    fun testF(){
        (0..20).forEach {
            println(fibonacci(it, 1000, 1000))
        }
    }
    private tailrec fun fibonacci(n: Int, a: Long = 0, b: Long = 1): Long =
        when (n) {
            0 -> a
            1 -> b
            else -> fibonacci(n - 1, b, a + b)
        }
}