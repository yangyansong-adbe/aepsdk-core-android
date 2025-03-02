package com.adobe.marketing.mobile.internal.eventhub

import com.adobe.marketing.mobile.internal.util.CustomThreadFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

internal object SDKDispatcher {
    private val extensionThreadPoolDispatcher = Executors.newFixedThreadPool(5, CustomThreadFactory("ADB-Worker")).asCoroutineDispatcher()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun createDispatcher(parallelism: Int): CoroutineDispatcher {
        return extensionThreadPoolDispatcher.limitedParallelism(parallelism)
    }

}