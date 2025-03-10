package com.adobe.marketing.mobile.internal.eventhub

import com.adobe.marketing.mobile.internal.util.CustomThreadFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

// TODO: for testing only, will be using Dispatchers.IO.limitedParallelism() in the future
@OptIn(ExperimentalCoroutinesApi::class)
internal object SDKDispatcher {
    // ExtensionContainer.kt
//    internal val extensionEventDispatcher = Executors.newFixedThreadPool(2, CustomThreadFactory("ADB-Ext")).asCoroutineDispatcher()

//    internal val subscriberDispatcher = Executors.newFixedThreadPool(2, CustomThreadFactory("ADB-Ext")).asCoroutineDispatcher()

    // NetworkService.kt
//    internal val networkThreadPoolDispatcher = Executors.newFixedThreadPool(2, CustomThreadFactory("ADB-NS")).asCoroutineDispatcher()


    // EventHub.kt
//    internal val eventHubDispatcher = Executors.newFixedThreadPool(1, CustomThreadFactory("ADB-eventHubDispatcher")).asCoroutineDispatcher()
//    internal val eventDispatcher = Executors.newFixedThreadPool(1, CustomThreadFactory("ADB-eventDispatcher")).asCoroutineDispatcher()
//    internal val completionHandlerDispatcher = Executors.newFixedThreadPool(1, CustomThreadFactory("ADB-completionHandler")).asCoroutineDispatcher()

    //PersistentHitQueueV2.kt
//    private var queueCounter = 0;
//    fun createPersistQueueDispatcher(): CoroutineDispatcher {
//        return Executors.newSingleThreadExecutor(CustomThreadFactory("ADB-PQ- ${queueCounter++}")).asCoroutineDispatcher()
//    }

    fun createPersistQueueDispatcher(): CoroutineDispatcher {
        return Dispatchers.IO.limitedParallelism(1)
    }
    internal val extensionEventDispatcher = Dispatchers.IO.limitedParallelism(2)
    internal val subscriberDispatcher = Dispatchers.IO.limitedParallelism(2)
    internal val networkThreadPoolDispatcher = Dispatchers.IO.limitedParallelism(32)


    internal val eventHubDispatcher = Dispatchers.IO.limitedParallelism(1)
    internal val eventDispatcher = Dispatchers.IO.limitedParallelism(1)
    internal val completionHandlerDispatcher = Dispatchers.IO.limitedParallelism(1)

}