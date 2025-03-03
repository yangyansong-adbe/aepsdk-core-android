/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile.internal.eventhub

import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.EventHistoryRequest
import com.adobe.marketing.mobile.EventHistoryResultHandler
import com.adobe.marketing.mobile.Extension
import com.adobe.marketing.mobile.ExtensionApi
import com.adobe.marketing.mobile.ExtensionEventListener
import com.adobe.marketing.mobile.SharedStateResolution
import com.adobe.marketing.mobile.SharedStateResolver
import com.adobe.marketing.mobile.SharedStateResult
import com.adobe.marketing.mobile.internal.CoreConstants
import com.adobe.marketing.mobile.services.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

private val subscriberScope = CoroutineScope(
    SDKDispatcher.createExtensionDispatcher(1)
)

internal class ExtensionContainer(
    private val extensionClass: Class<out Extension>,
    callback: (EventHubError) -> Unit
) : ExtensionApi() {

    companion object {
        const val LOG_TAG = "ExtensionContainer"
    }

    var sharedStateName: String? = null
        private set

    var friendlyName: String? = null
        private set

    var version: String? = null
        private set

    var metadata: Map<String, String>? = null
        private set

    var lastProcessedEvent: Event? = null
        private set

    var extension: Extension? = null
        private set

    private var sharedStateManagers: Map<SharedStateType, SharedStateManager>? = null
    private val eventListeners: ConcurrentLinkedQueue<ExtensionListenerContainer> =
        ConcurrentLinkedQueue()

    private val processingScope = CoroutineScope(
        SDKDispatcher.createExtensionDispatcher(1)
    )

    private val eventChannel = Channel<Event>(Channel.UNLIMITED)

    private val eventQueue: Queue<Event> = ConcurrentLinkedQueue()

    private val job: Job

    private val teardownJob = Runnable {
        extension?.onExtensionUnregistered()
        Log.debug(
            CoreConstants.LOG_TAG,
            getTag(),
            "Extension unregistered"
        )
    }

    @Volatile
    private var state = State.IDLE

    init {
        job = subscribeTo(EventHub.shared.events)
        // Launch a coroutine in the processing scope to sequentially process events from the queue.

        processingScope.launch {
            state = State.RUNNING
            extension = extensionClass.initWith(this@ExtensionContainer)
            extension?.let { ext ->
                ext.extensionName.takeUnless { it.isNullOrBlank() }?.let { name ->
                    sharedStateName = name
                    friendlyName = ext.extensionFriendlyName
                    version = ext.extensionVersion
                    metadata = ext.extensionMetadata

                    sharedStateManagers = mapOf(
                        SharedStateType.XDM to SharedStateManager(name),
                        SharedStateType.STANDARD to SharedStateManager(name)
                    )

                    Log.debug(
                        CoreConstants.LOG_TAG,
                        getTag(),
                        "Extension registered"
                    )

                    callback(EventHubError.None)
                    ext.onExtensionRegistered()
                } ?: run {
                    callback(EventHubError.InvalidExtensionName)
                    //TODO: cleanup - cancel job?
                    return@launch
                }
            } ?: run {
                //TODO: cleanup - cancel job?
                callback(EventHubError.ExtensionInitializationFailure)
                return@launch
            }

            if (state == State.RUNNING) {
                state = State.IDLE
            }

            for (event in eventChannel) {
                eventQueue.add(event)
                if (friendlyName == "Edge") {
                    Log.warning(
                        CoreConstants.LOG_TAG,
                        getTag(),
                        "eventChannel - ${event.name}"
                    )
                }
                processEventQueue()
            }
        }

//        eventProcessor.setInitialJob(initJob)
//        eventProcessor.setFinalJob(teardownJob)
//        eventProcessor.start()
    }

    private suspend fun processEventQueue() =
//        withTimeoutOrNull(10000) {
        // TODO: when timeout reached, retry processing the event may run into issues. Consider remove the timeout.
        coroutineScope {

            if (state == State.RUNNING) {
                Log.warning(
                    CoreConstants.LOG_TAG,
                    getTag(),
                    "Still processing the previous event."
                )
                return@coroutineScope
            }
            while (eventQueue.isNotEmpty()) {
                if (state == State.PENDING) {
                    return@coroutineScope
                }
                state = State.RUNNING
                // Check the event at the front of the queue.
                val candidate = eventQueue.peek() ?: return@coroutineScope
                if (extension?.readyForEvent(candidate) == true) {
                    eventQueue.poll()?.let { event ->

                        eventListeners.forEach {
                            if (it.shouldNotify(event)) {
                                it.notify(event)
                            }
                        }
                        lastProcessedEvent = event
                    }

                } else {
                    state = State.IDLE
                    return@coroutineScope
                }
            }
            if (state == State.RUNNING) {
                state = State.IDLE
            }
        }

    private fun subscribeTo(events: SharedFlow<Event>) =
        subscriberScope.launch {
            events.collect {
                if (friendlyName == "Edge") {
                    Log.warning(
                        CoreConstants.LOG_TAG,
                        getTag(),
                        "${it.name}"
                    )
                }
                eventChannel.send(it)
            }
        }

    fun shutdown() {
        //TODO: ..
//        eventProcessor.shutdown()
    }

    /**
     * Returns instance of [SharedStateManager] for [SharedStateType]
     */
    fun getSharedStateManager(type: SharedStateType): SharedStateManager? {
        return sharedStateManagers?.get(type)
    }

    private fun getTag(): String {
        if (extension == null) {
            return LOG_TAG
        }

        return "ExtensionContainer[$sharedStateName($version)]"
    }

    // Override ExtensionApi Methods
    override fun registerEventListener(
        eventType: String,
        eventSource: String,
        eventListener: ExtensionEventListener
    ) {
        eventListeners.add(ExtensionListenerContainer(eventType, eventSource, eventListener))
    }

    override fun dispatch(
        event: Event
    ) {
        EventHub.shared.dispatch(event)
    }

    override fun startEvents() {
        if (state == State.PENDING) {
            state = State.IDLE
        }
        processingScope.launch {
            processEventQueue()
        }
    }

    override fun stopEvents() {
        state = State.PENDING
    }

    override fun createSharedState(
        state: MutableMap<String, Any?>,
        event: Event?
    ) {
        val sharedStateName = this.sharedStateName ?: run {
            Log.warning(
                CoreConstants.LOG_TAG,
                getTag(),
                "ExtensionContainer is not fully initialized. createSharedState should not be called from Extension constructor"
            )
            return
        }

        EventHub.shared.createSharedState(
            SharedStateType.STANDARD,
            sharedStateName,
            state,
            event
        )

    }

    override fun createPendingSharedState(
        event: Event?
    ): SharedStateResolver? {
        val sharedStateName = this.sharedStateName ?: run {
            Log.warning(
                CoreConstants.LOG_TAG,
                getTag(),
                "ExtensionContainer is not fully initialized. createPendingSharedState should not be called from 'Extension' constructor"
            )
            return null
        }

        return EventHub.shared.createPendingSharedState(
            SharedStateType.STANDARD,
            sharedStateName,
            event
        )

    }

    override fun getSharedState(
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

    override fun createXDMSharedState(
        state: MutableMap<String, Any?>,
        event: Event?
    ) {
        val sharedStateName = this.sharedStateName ?: run {
            Log.warning(
                CoreConstants.LOG_TAG,
                getTag(),
                "ExtensionContainer is not fully initialized. createXDMSharedState should not be called from Extension constructor"
            )
            return
        }

        EventHub.shared.createSharedState(SharedStateType.XDM, sharedStateName, state, event)


    }

    override fun createPendingXDMSharedState(
        event: Event?
    ): SharedStateResolver? {
        val sharedStateName = this.sharedStateName ?: run {
            Log.warning(
                CoreConstants.LOG_TAG,
                getTag(),
                "ExtensionContainer is not fully initialized. createPendingXDMSharedState should not be called from 'Extension' constructor"
            )
            return null
        }

        return EventHub.shared.createPendingSharedState(SharedStateType.XDM, sharedStateName, event)
    }

    override fun getXDMSharedState(
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

    override fun unregisterExtension() {
        EventHub.shared.unregisterExtension(extensionClass) {}
    }

    override fun getHistoricalEvents(
        eventHistoryRequests: Array<out EventHistoryRequest>,
        enforceOrder: Boolean,
        handler: EventHistoryResultHandler<Int>
    ) {
        EventHub.shared.eventHistory?.getEvents(eventHistoryRequests, enforceOrder, handler)
    }
}

private enum class State {
    PENDING,
    IDLE,
    RUNNING
}
