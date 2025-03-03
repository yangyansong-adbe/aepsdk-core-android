package com.adobe.marketing.mobile.internal.eventhub

import com.adobe.marketing.mobile.internal.util.CustomThreadFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

internal object SDKDispatcher {
    private val extensionThreadPoolDispatcher = Executors.newFixedThreadPool(2, CustomThreadFactory("ADB-Ext")).asCoroutineDispatcher()
    private val networkThreadPoolDispatcher = Executors.newFixedThreadPool(2, CustomThreadFactory("ADB-NS")).asCoroutineDispatcher()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun createExtensionDispatcher(parallelism: Int): CoroutineDispatcher {
        return extensionThreadPoolDispatcher.limitedParallelism(parallelism)
    }

    // For testing only. Switching to using IO dispatcher for network requests.
    fun getNetworkDispatcher(): CoroutineDispatcher {
        return networkThreadPoolDispatcher
    }

    private var queueCounter = 0;
    fun createPersistQueueDispatcher(): CoroutineDispatcher {
        return Executors.newSingleThreadExecutor(CustomThreadFactory("ADB-PQ- ${queueCounter++}")).asCoroutineDispatcher()
    }

}