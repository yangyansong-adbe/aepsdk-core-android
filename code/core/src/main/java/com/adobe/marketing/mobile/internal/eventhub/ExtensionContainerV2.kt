package com.adobe.marketing.mobile.internal.eventhub

import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.EventHistoryRequest
import com.adobe.marketing.mobile.EventSource
import com.adobe.marketing.mobile.EventType
import com.adobe.marketing.mobile.ExtensionApiV2
import com.adobe.marketing.mobile.ProcessEvent
import com.adobe.marketing.mobile.ReadyForEvent
import com.adobe.marketing.mobile.SharedStateResolution
import com.adobe.marketing.mobile.SharedStateResolver
import com.adobe.marketing.mobile.SharedStateResult
import com.adobe.marketing.mobile.internal.CoreConstants
import com.adobe.marketing.mobile.services.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val handler = CoroutineExceptionHandler { _, exception ->
    Log.debug(
        CoreConstants.LOG_TAG,
        "ExtensionContainerV2",
        "Caught exception - $exception "
    )
}
private val subscriberScope = CoroutineScope(Dispatchers.IO + handler)

internal class ExtensionContainerV2(
    private val readyForEvent: ReadyForEvent,
    private val eventFlow: SharedFlow<Event>,
    private val sharedStateName: String,
    private val updateLastProcessedEventUUID: (String) -> Unit
) : ExtensionApiV2 {
    private val listeners = mutableListOf<ExtensionListenerContainerV2>()
    private val processingScope = CoroutineScope(Dispatchers.IO)
    private val eventChannel = Channel<Event>(Channel.UNLIMITED)
    private val eventQueue: Queue<Event> = ConcurrentLinkedQueue()

    private val job: Job = subscriberScope.launch {
        eventFlow.collect {
            eventChannel.send(it)
        }
    }

    init {
        processingScope.launch {
            for (event in eventChannel) {
                eventQueue.add(event)
                processEventQueue()
            }
        }
    }

    private suspend fun processEventQueue() =
//        withTimeoutOrNull(10000) {
        // TODO: do we need timeout for event processing?
        coroutineScope {
            while (eventQueue.isNotEmpty()) {
                // Check the event at the front of the queue.
                val candidate = eventQueue.peek() ?: return@coroutineScope
                if (readyForEvent(candidate)) {
                    eventQueue.poll()?.let { event ->

                        listeners.forEach {
                            if (it.shouldNotify(event)) {
                                it.notify(event)
                            }
                        }
                        updateLastProcessedEventUUID(event.uniqueIdentifier)
                    }

                } else {
                    return@coroutineScope
                }
            }
        }

    override fun registerEventListener(
        eventType: String,
        eventSource: String,
        eventListener: ProcessEvent
    ) {
        listeners.add(ExtensionListenerContainerV2(eventType, eventSource, eventListener))
    }

    override fun dispatch(event: Event) {
        EventHub.shared.dispatch(event)
    }

    override suspend fun createSharedState(state: MutableMap<String, Any?>, event: Event?) {
        EventHub.shared.createSharedState(
            SharedStateType.STANDARD,
            sharedStateName,
            state,
            event
        )
    }

    override suspend fun createPendingSharedState(event: Event?): SharedStateResolver? {
        return EventHub.shared.createPendingSharedState(
            SharedStateType.STANDARD,
            sharedStateName,
            event
        )
    }

    override suspend fun getSharedState(
        extensionName: String,
        event: Event?,
        barrier: Boolean,
        resolution: SharedStateResolution
    ): SharedStateResult? {
        return EventHub.shared.getSharedState(
            SharedStateType.STANDARD,
            extensionName,
            event,
            barrier,
            resolution
        )
    }

    override suspend fun createXDMSharedState(state: MutableMap<String, Any?>, event: Event?) {
        EventHub.shared.createSharedState(SharedStateType.XDM, sharedStateName, state, event)
    }

    override suspend fun createPendingXDMSharedState(event: Event?): SharedStateResolver? {
        return EventHub.shared.createPendingSharedState(SharedStateType.XDM, sharedStateName, event)
    }

    override suspend fun getXDMSharedState(
        extensionName: String,
        event: Event?,
        barrier: Boolean,
        resolution: SharedStateResolution
    ): SharedStateResult? {
        return EventHub.shared.getSharedState(
            SharedStateType.XDM,
            extensionName,
            event,
            barrier,
            resolution
        )
    }

    override suspend fun getHistoricalEvents(
        eventHistoryRequests: Array<EventHistoryRequest>,
        enforceOrder: Boolean
    ): Int? {
        return suspendCoroutine {
            EventHub.shared.eventHistory?.getEvents(
                eventHistoryRequests,
                enforceOrder
            ) { result -> it.resume(result) }
        }
    }

}

private class ExtensionListenerContainerV2(
    val eventType: String,
    val eventSource: String,
    val listener: ProcessEvent
) {
    fun shouldNotify(event: Event): Boolean {
        // Wildcard listeners should only be notified of paired response events.
        return if (event.responseID != null) {
            (eventType == EventType.WILDCARD && eventSource == EventSource.WILDCARD)
        } else {
            eventType.equals(event.type, ignoreCase = true) && eventSource.equals(
                event.source,
                ignoreCase = true
            ) ||
                    eventType == EventType.WILDCARD && eventSource == EventSource.WILDCARD
        }
    }

    suspend fun notify(event: Event) {
        try {
            listener(event)
        } catch (ex: Exception) {
            Log.debug(
                CoreConstants.LOG_TAG,
                "ExtensionListenerContainer",
                "Exception thrown for EventId ${event.uniqueIdentifier}. $ex"
            )
        }
    }
}