package com.adobe.marketing.mobile.util.resilience

internal interface Executor <T>{
        suspend fun execute(block: () -> T?): T?
        fun retryOnException(block: (Exception) -> Boolean): Executor<T>
        fun retryOnResult(block: (T?) -> Boolean): Executor<T>
        fun retryIntervalOnResult(block: (T?) -> Long): Executor<T>
        fun resolveThrowable(block: (Throwable) -> T?): Executor<T>
        fun cancel()
    }