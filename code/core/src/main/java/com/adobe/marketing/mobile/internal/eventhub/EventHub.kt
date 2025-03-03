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

import androidx.annotation.VisibleForTesting
import com.adobe.marketing.mobile.AdobeCallback
import com.adobe.marketing.mobile.AdobeCallbackWithError
import com.adobe.marketing.mobile.AdobeError
import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.EventSource
import com.adobe.marketing.mobile.EventType
import com.adobe.marketing.mobile.Extension
import com.adobe.marketing.mobile.LoggingMode
import com.adobe.marketing.mobile.SharedStateResolution
import com.adobe.marketing.mobile.SharedStateResolver
import com.adobe.marketing.mobile.SharedStateResult
import com.adobe.marketing.mobile.SharedStateStatus
import com.adobe.marketing.mobile.WrapperType
import com.adobe.marketing.mobile.internal.CoreConstants
import com.adobe.marketing.mobile.internal.eventhub.EventHub.Companion.LOG_TAG
import com.adobe.marketing.mobile.internal.eventhub.history.AndroidEventHistory
import com.adobe.marketing.mobile.internal.eventhub.history.EventHistory
import com.adobe.marketing.mobile.internal.util.prettify
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.util.EventDataUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * EventHub class is responsible for delivering events to listeners and maintaining registered extension's lifecycle.
 */
internal class EventHub {

    companion object {
        const val LOG_TAG = "EventHub"
        var shared = EventHub()
    }

    /**
     * Concurrent map which stores the backing extension container for each Extension and can be referenced by extension type name
     */

    // A SharedFlow to dispatch events to subscribers
    private val _events =
        MutableSharedFlow<Event>(
            replay = 0,
            extraBufferCapacity = 100,
            onBufferOverflow = BufferOverflow.SUSPEND
        )
    internal val events = _events.asSharedFlow()

    private val eventPreprocessorsChannel = Channel<Event>(Channel.UNLIMITED)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventHubScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))
    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventDispatcherScope =
        CoroutineScope(Dispatchers.IO.limitedParallelism(1))

    private val registeredExtensions: ConcurrentHashMap<String, ExtensionContainer> =
        ConcurrentHashMap()

    /**
     * Concurrent list which stores the registered event preprocessors.
     * Preprocessors will be executed on each event before distributing it to extension queue.
     */
    private val eventPreprocessors: ConcurrentLinkedQueue<EventPreprocessor> =
        ConcurrentLinkedQueue()

    /**
     * Atomic counter which is incremented when processing event and shared state.
     */
    private val lastEventNumber: AtomicInteger = AtomicInteger(0)

    /**
     * A cache that maps UUID of an Event to an internal sequence of its dispatch.
     */
    private val eventNumberMap: ConcurrentHashMap<String, Int> = ConcurrentHashMap<String, Int>()

    /**
     * Boolean to denote if event hub has started processing events
     */
    private var hubStarted = false


    /**
     * Responsible for managing event history.
     */
    var eventHistory: EventHistory? = null

    init {
        registerExtension(EventHubPlaceholderExtension::class.java)
    }

    @Volatile
    private var _wrapperType = WrapperType.NONE
    var wrapperType: WrapperType
        get() {
            return _wrapperType
        }
        set(value) {
            eventHubScope.launch {
                if (hubStarted) {
                    Log.warning(
                        CoreConstants.LOG_TAG,
                        LOG_TAG,
                        "Wrapper type can not be set after EventHub starts processing events"
                    )
                } else {
                    _wrapperType = value
                    Log.debug(
                        CoreConstants.LOG_TAG,
                        LOG_TAG,
                        "Wrapper type set to $value"
                    )
                }
            }
        }

    /**
     * Initializes event history. This must be called after the SDK has application context.
     */
    fun initializeEventHistory() {
        if (eventHistory != null) {
            Log.warning(
                CoreConstants.LOG_TAG,
                LOG_TAG,
                "Event history is already initialized"
            )
            return
        }

        eventHistory = try {
            AndroidEventHistory()
        } catch (ex: Exception) {
            Log.warning(
                CoreConstants.LOG_TAG,
                LOG_TAG,
                "Event history initialization failed with exception ${ex.message}"
            )
            null
        }
    }

    /**
     * `EventHub` will begin processing `Event`s when this API is invoked.
     */
    fun start() {
        eventHubScope.launch {
            hubStarted = true
            eventDispatcherScope.launch {
                for (event in eventPreprocessorsChannel) {
                    var processedEvent: Event = event
                    for (eventPreprocessor in eventPreprocessors) {
                        processedEvent = eventPreprocessor.process(processedEvent)
                    }

                    // Handle response event listeners
                    if (processedEvent.responseID != null) {

                        // responseEvent.responseID == triggerEvent.uuid
                        CompletionHandler.handleEvent(processedEvent.responseID, processedEvent)
                    }

                    // Notify to extensions for processing
//                    registeredExtensions.values.forEach {
//                        it.eventProcessor.offer(processedEvent)
//                    }
                    _events.emit(event)

                    if (Log.getLogLevel() >= LoggingMode.DEBUG) {
                        Log.debug(
                            CoreConstants.LOG_TAG,
                            LOG_TAG,
                            "Dispatched Event #${getEventNumber(event)} to extensions after processing rules - ($processedEvent)"
                        )
                    }

                    // Record event history
                    processedEvent.mask?.let {
                        eventHistory?.recordEvent(processedEvent) { result ->
                            if (!result) {
                                Log.debug(
                                    CoreConstants.LOG_TAG,
                                    LOG_TAG,
                                    "Failed to insert Event(${processedEvent.uniqueIdentifier}) into EventHistory database"
                                )
                            }
                        }
                    }

                }
            }
            shareEventHubSharedState()
            Log.trace(
                CoreConstants.LOG_TAG,
                LOG_TAG,
                "EventHub started. Will begin processing events"
            )
        }
    }

    /**
     * Dispatches a new [Event] to all listeners who have registered for the event type and source.
     * If the `event` has a `mask`, this method will attempt to record the `event` in `eventHistory`.
     * See [eventDispatcher] for more details.
     *
     * @param event the [Event] to be dispatched to listeners
     */
    fun dispatch(event: Event) {
        eventHubScope.launch {
            dispatchInternal(event)
        }
    }

    /**
     * Internal method to dispatch an event
     */
    private suspend fun dispatchInternal(event: Event) {
        val eventNumber = lastEventNumber.incrementAndGet()
        eventNumberMap[event.uniqueIdentifier] = eventNumber
        try {
            eventPreprocessorsChannel.send(event)
        } catch (e: Exception) {
            Log.warning(
                CoreConstants.LOG_TAG,
                LOG_TAG,
                "Failed to dispatch event #$eventNumber - ($event), error: ${e.message}"
            )
        }

        if (Log.getLogLevel() >= LoggingMode.DEBUG) {
            Log.debug(
                CoreConstants.LOG_TAG,
                LOG_TAG,
                "Dispatching Event #$eventNumber - ($event)"
            )
        }
    }

    /**
     * This is a convenience method to register a set of [Extension]s with the `EventHub` and start processing events.
     * This method attempts to register each extension using the [EventHub.registerExtension] method.
     * The provided callback is invoked after the registration status for all extensions is received.
     *
     * @param extensions A set of extension classes to register with the EventHub.
     * @param completion A callback that is invoked once the extensions have been registered
     *                   (or failed to register).
     */
    @JvmOverloads
    fun registerExtensions(
        extensions: Set<Class<out Extension>>,
        completion: (() -> Unit)? = null
    ) {
        val registeredExtensions = AtomicInteger(0)
        extensions.forEach {
            registerExtension(it) {
                if (registeredExtensions.incrementAndGet() == extensions.size) {
                    start()
                    completion?.let { CompletionHandler.execute { it() } }
                }
            }
        }
    }

    /**
     * Registers a new [Extension] to the `EventHub`. This extension must extend [Extension] class
     *
     * @param extensionClass The class of extension to register
     * @param completion Invoked when the extension has been registered or failed to register
     */
    @JvmOverloads
    fun registerExtension(
        extensionClass: Class<out Extension>,
        completion: ((error: EventHubError) -> Unit)? = null
    ) {
        eventHubScope.launch {
            val extensionTypeName = extensionClass.extensionTypeName
            if (registeredExtensions.containsKey(extensionTypeName)) {
                completion?.let { CompletionHandler.execute { it(EventHubError.DuplicateExtensionName) } }
                return@launch
            }

            val container = ExtensionContainer(extensionClass) { error ->
                eventHubScope.launch {
                    completion?.let { CompletionHandler.execute { it(error) } }
                    extensionPostRegistration(extensionClass, error)
                }
            }
            registeredExtensions[extensionTypeName] = container
        }
    }

    /**
     * Called after creating extension container to hold the extension
     *
     * @param extensionClass The class of extension to register
     * @param error Error denoting the status of registration
     */
    private suspend fun extensionPostRegistration(
        extensionClass: Class<out Extension>,
        error: EventHubError
    ) {
        if (error != EventHubError.None) {
            Log.warning(
                CoreConstants.LOG_TAG,
                LOG_TAG,
                "Extension $extensionClass registration failed with error $error"
            )
            unregisterExtensionInternal(extensionClass)
        } else {
            Log.trace(
                CoreConstants.LOG_TAG,
                LOG_TAG,
                "Extension $extensionClass registered successfully"
            )
            shareEventHubSharedState()
        }
    }

    /**
     * Unregisters the extension from the `EventHub` if registered
     * @param extensionClass The class of extension to unregister
     * @param completion Invoked when the extension has been unregistered or failed to unregister
     */
    fun unregisterExtension(
        extensionClass: Class<out Extension>,
        completion: ((error: EventHubError) -> Unit)
    ) {
        eventHubScope.launch {
            unregisterExtensionInternal(extensionClass, completion)
        }
    }

    private suspend fun unregisterExtensionInternal(
        extensionClass: Class<out Extension>,
        completion: ((error: EventHubError) -> Unit)? = null
    ) {
        val extensionName = extensionClass.extensionTypeName
        val container = registeredExtensions.remove(extensionName)
        val error: EventHubError = if (container != null) {
            container.shutdown()
            shareEventHubSharedState()
            Log.trace(
                CoreConstants.LOG_TAG,
                LOG_TAG,
                "Extension $extensionClass unregistered successfully"
            )
            EventHubError.None
        } else {
            Log.warning(
                CoreConstants.LOG_TAG,
                LOG_TAG,
                "Extension $extensionClass unregistration failed as extension was not registered"
            )
            EventHubError.ExtensionNotRegistered
        }

        completion.let { CompletionHandler.execute { it?.invoke(error) } }
    }

    /**
     * Registers an event listener which will be invoked when the response event to trigger event is dispatched
     * @param triggerEvent An [Event] which will trigger a response event
     * @param timeoutMS A timeout in milliseconds, if the response listener is not invoked within the timeout, then the `EventHub` invokes the fail method.
     * @param listener An [AdobeCallbackWithError] which will be invoked whenever the `EventHub` receives the response event for trigger event
     */
    fun registerResponseListener(
        triggerEvent: Event,
        timeoutMS: Long,
        listener: AdobeCallbackWithError<Event>
    ) {
        eventHubScope.launch {
            val triggerEventId = triggerEvent.uniqueIdentifier
            CompletionHandler.scheduleTimeoutHandler(triggerEventId, timeoutMS, listener)
        }
    }

    /**
     * Registers an event listener which will be invoked whenever an [Event] with matched type and source is dispatched
     * @param eventType A String indicating the event type the current listener is listening for
     * @param eventSource A `String` indicating the event source the current listener is listening for
     * @param listener An [AdobeCallback] which will be invoked whenever the `EventHub` receives a event with matched type and source
     */
    fun registerListener(eventType: String, eventSource: String, listener: AdobeCallback<Event>) {
        eventHubScope.launch {
            val eventHubContainer = getExtensionContainer(EventHubPlaceholderExtension::class.java)
            eventHubContainer?.registerEventListener(eventType, eventSource) { listener.call(it) }
        }
    }

    /**
     * Registers an [EventPreprocessor] with the eventhub
     * Note that this is an internal only method for use by ConfigurationExtension,
     * until preprocessors are supported via a public api.
     *
     * @param eventPreprocessor the [EventPreprocessor] that should be registered
     */
    internal fun registerEventPreprocessor(eventPreprocessor: EventPreprocessor) {
        if (eventPreprocessors.contains(eventPreprocessor)) {
            return
        }
        eventPreprocessors.add(eventPreprocessor)
    }

    /**
     * Creates a new shared state for the extension with provided data, versioned at [Event]
     * If `event` is nil, one of two behaviors will be observed:
     * 1. If this extension has not previously published a shared state, shared state will be versioned at 0
     * 2. If this extension has previously published a shared state, shared state will be versioned at the latest
     * @param sharedStateType The type of shared state to be set
     * @param extensionName Extension whose shared state is to be updated
     * @param state Map which contains data for the shared state
     * @param event [Event] for which the `SharedState` should be versioned
     * @return true - if shared state is created successfully
     */
    fun createSharedState(
        sharedStateType: SharedStateType,
        extensionName: String,
        state: MutableMap<String, Any?>?,
        event: Event?
    ): Boolean {
        val immutableState = try {
            EventDataUtils.immutableClone(state)
        } catch (ex: Exception) {
            Log.warning(
                CoreConstants.LOG_TAG,
                LOG_TAG,
                "Creating $sharedStateType shared state for extension $extensionName at event ${event?.uniqueIdentifier} with null - Cloning state failed with exception $ex"
            )
            null
        }
        return runBlocking {
            eventHubScope.async {
                createSharedStateInternal(
                    sharedStateType,
                    extensionName,
                    immutableState,
                    event
                )
            }.await()
        }
    }

    /**
     * Internal method to creates a new shared state for the extension with provided data, versioned at [Event]
     */
    private suspend fun createSharedStateInternal(
        sharedStateType: SharedStateType,
        extensionName: String,
        state: MutableMap<String, Any?>?,
        event: Event?
    ): Boolean {
        val sharedStateManager = getSharedStateManager(sharedStateType, extensionName)
        sharedStateManager ?: run {
            Log.warning(
                CoreConstants.LOG_TAG,
                LOG_TAG,
                "Create $sharedStateType shared state for extension \"$extensionName\" for event ${event?.uniqueIdentifier} failed - SharedStateManager is null"
            )
            return false
        }

        val version = resolveSharedStateVersion(sharedStateManager, event)
        val didSet = sharedStateManager.setState(version, state)
        if (!didSet) {
            Log.warning(
                CoreConstants.LOG_TAG,
                LOG_TAG,
                "Create $sharedStateType shared state for extension \"$extensionName\" for event ${event?.uniqueIdentifier} failed - SharedStateManager failed"
            )
        } else {
            Log.debug(
                CoreConstants.LOG_TAG,
                LOG_TAG,
                "Created $sharedStateType shared state for extension \"$extensionName\" with version $version and data ${state?.prettify()}"
            )
            dispatchSharedStateEvent(sharedStateType, extensionName)
        }

        return didSet
    }

    /**
     * Sets the shared state for the extension to pending at event's version and returns a [SharedStateResolver] which is to be invoked with data for the shared state once available.
     * If event is nil, one of two behaviors will be observed:
     * 1. If this extension has not previously published a shared state, shared state will be versioned at 0
     * 2. If this extension has previously published a shared state, shared state will be versioned at the latest
     * @param sharedStateType The type of shared state to be set
     * @param extensionName Extension whose shared state is to be updated
     * @param event [Event] for which the `SharedState` should be versioned
     * @return A [SharedStateResolver] which is invoked to set pending the shared state versioned at [Event]
     */
    fun createPendingSharedState(
        sharedStateType: SharedStateType,
        extensionName: String,
        event: Event?
    ): SharedStateResolver? {
        return runBlocking {
            eventHubScope.async {
                val sharedStateManager = getSharedStateManager(sharedStateType, extensionName)
                sharedStateManager ?: run {
                    Log.warning(
                        CoreConstants.LOG_TAG,
                        LOG_TAG,
                        "Create pending $sharedStateType shared state for extension \"$extensionName\" for event ${event?.uniqueIdentifier} failed - SharedStateManager is null"
                    )
                    return@async null
                }

                val pendingVersion = resolveSharedStateVersion(sharedStateManager, event)
                val didSetPending = sharedStateManager.setPendingState(pendingVersion)
                if (!didSetPending) {
                    Log.warning(
                        CoreConstants.LOG_TAG,
                        LOG_TAG,
                        "Create pending $sharedStateType shared state for extension \"$extensionName\" for event ${event?.uniqueIdentifier} failed - SharedStateManager failed"
                    )
                    return@async null
                }

                Log.debug(
                    CoreConstants.LOG_TAG,
                    LOG_TAG,
                    "Created pending $sharedStateType shared state for extension \"$extensionName\" with version $pendingVersion"
                )
                return@async SharedStateResolver {
                    resolvePendingSharedState(sharedStateType, extensionName, it, pendingVersion)
                }
            }.await()
        }
    }

    /**
     * Updates a pending shared state and dispatches it to the `EventHub`
     * Providing a version for which there is no pending state will result in a no-op.
     * @param sharedStateType The type of shared state to be set
     * @param extensionName Extension whose shared state is to be updated
     * @param state Map which contains data for the shared state
     * @param version An `Int` containing the version of the state being updated
     */
    private fun resolvePendingSharedState(
        sharedStateType: SharedStateType,
        extensionName: String,
        state: MutableMap<String, Any?>?,
        version: Int
    ) {
        eventHubScope.launch {
            val immutableState = try {
                EventDataUtils.immutableClone(state)
            } catch (ex: Exception) {
                Log.warning(
                    CoreConstants.LOG_TAG,
                    LOG_TAG,
                    "Resolving pending $sharedStateType shared state for extension \"$extensionName\" and version $version with null - Clone failed with exception $ex"
                )
                null
            }
            val sharedStateManager = getSharedStateManager(sharedStateType, extensionName) ?: run {
                Log.warning(
                    CoreConstants.LOG_TAG,
                    LOG_TAG,
                    "Resolve pending $sharedStateType shared state for extension \"$extensionName\" and version $version failed - SharedStateManager is null"
                )
                return@launch
            }

            val didUpdate = sharedStateManager.updatePendingState(version, immutableState)
            if (!didUpdate) {
                Log.warning(
                    CoreConstants.LOG_TAG,
                    LOG_TAG,
                    "Resolve pending $sharedStateType shared state for extension \"$extensionName\" and version $version failed - SharedStateManager failed"
                )
                return@launch
            }

            Log.debug(
                CoreConstants.LOG_TAG,
                LOG_TAG,
                "Resolved pending $sharedStateType shared state for \"$extensionName\" and version $version with data ${immutableState?.prettify()}"
            )
            dispatchSharedStateEvent(sharedStateType, extensionName)
        }
    }

    /**
     * Retrieves the shared state for a specific extension
     * @param sharedStateType The type of shared state to be set
     * @param extensionName Extension whose shared state will be returned
     * @param event If not nil, will retrieve the shared state that corresponds with this event's version or latest if not yet versioned. If event is nil will return the latest shared state
     * @param barrier If true, the `EventHub` will only return [SharedStateStatus.SET] if [extensionName] has moved past [Event]
     * @param resolution The [SharedStateResolution] to determine how to resolve the shared state
     * @return The shared state data and status for the extension with [extensionName]
     */
    fun getSharedState(
        sharedStateType: SharedStateType,
        extensionName: String,
        event: Event?,
        barrier: Boolean,
        resolution: SharedStateResolution
    ): SharedStateResult? {

        return runBlocking {
            eventHubScope.async {
                val container = getExtensionContainer(extensionName) ?: run {
                    Log.debug(
                        CoreConstants.LOG_TAG,
                        LOG_TAG,
                        "Unable to retrieve $sharedStateType shared state for \"$extensionName\". No such extension is registered."
                    )

                    return@async null
                }

                val sharedStateManager =
                    getSharedStateManager(sharedStateType, extensionName) ?: run {
                        Log.warning(
                            CoreConstants.LOG_TAG,
                            LOG_TAG,
                            "Unable to retrieve $sharedStateType shared state for \"$extensionName\". SharedStateManager is null"
                        )
                        return@async null
                    }

                val version = getEventNumber(event) ?: SharedStateManager.VERSION_LATEST

                val result: SharedStateResult = when (resolution) {
                    SharedStateResolution.ANY -> sharedStateManager.resolve(version)
                    SharedStateResolution.LAST_SET -> sharedStateManager.resolveLastSet(version)
                }

                val stateProviderLastVersion = getEventNumber(container.lastProcessedEvent) ?: 0
                // shared state is still considered pending if barrier is used and the state provider has not processed past the previous event
                val hasProcessedEvent =
                    if (event == null) true else stateProviderLastVersion > version - 1
                return@async if (barrier && !hasProcessedEvent && result.status == SharedStateStatus.SET) {
                    SharedStateResult(SharedStateStatus.PENDING, result.value)
                } else {
                    result
                }
            }.await()
        }
    }

    /**
     * Clears all shared state previously set by [extensionName].
     *
     * @param sharedStateType the type of shared state that needs to be cleared.
     * @param extensionName the name of the extension for which the state is being cleared
     * @return true - if the shared state has been cleared, false otherwise
     */
    fun clearSharedState(
        sharedStateType: SharedStateType,
        extensionName: String
    ): Boolean {

        eventHubScope.launch {
            val sharedStateManager = getSharedStateManager(sharedStateType, extensionName) ?: run {
                Log.warning(
                    CoreConstants.LOG_TAG,
                    LOG_TAG,
                    "Clear $sharedStateType shared state for extension \"$extensionName\" failed - SharedStateManager is null"
                )
                return@launch
            }

            sharedStateManager.clear()
            Log.warning(
                CoreConstants.LOG_TAG,
                LOG_TAG,
                "Cleared $sharedStateType shared state for extension \"$extensionName\""
            )
            return@launch
        }

        return true
    }

    /**
     * Stops processing events and shuts down all registered extensions.
     */
    fun shutdown() {
        // Shutdown and clear all the extensions.
        eventHubScope.launch {
            //TODO:
//            eventDispatcher.shutdown()

            // Unregister all extensions
            registeredExtensions.forEach { (_, extensionContainer) ->
                extensionContainer.shutdown()
            }
            registeredExtensions.clear()
        }
        // TODO: ...
        eventHubScope.cancel()
        //        scheduledExecutor.shutdown()
    }

    /**
     * Retrieve the event number for the Event from the [eventNumberMap]
     *
     * @param event the [Event] for which the event number should be resolved
     * @return the event number for the event if it exists (if it has been recorded/dispatched),
     *         null otherwise
     */
    private fun getEventNumber(event: Event?): Int? {
        if (event == null) {
            return null
        }
        val eventUUID = event.uniqueIdentifier
        return eventNumberMap[eventUUID]
    }

    /**
     * Retrieves a registered [ExtensionContainer] with [extensionClass] provided.
     *
     * @param extensionClass the extension class for which an [ExtensionContainer] should be fetched.
     * @return [ExtensionContainer] with [extensionName] provided if one was registered,
     *         null if no extension is registered with the [extensionName]
     */
    @VisibleForTesting
    internal fun getExtensionContainer(extensionClass: Class<out Extension>): ExtensionContainer? {
        return registeredExtensions[extensionClass.extensionTypeName]
    }

    /**
     * Retrieves a registered [ExtensionContainer] with [extensionTypeName] provided.
     *
     * @param extensionName the name of the extension for which an [ExtensionContainer] should be fetched.
     *        This should match [Extension.name] of an extension registered with the event hub.
     * @return [ExtensionContainer] with [extensionName] provided if one was registered,
     *         null if no extension is registered with the [extensionName]
     */
    private fun getExtensionContainer(extensionName: String): ExtensionContainer? {
        val extensionContainer = registeredExtensions.entries.firstOrNull {
            return@firstOrNull (
                    it.value.sharedStateName?.equals(
                        extensionName,
                        true
                    ) ?: false
                    )
        }
        return extensionContainer?.value
    }

    /**
     * Retrieves the [SharedStateManager] of type [sharedStateType] with [extensionName] provided.
     *
     * @param sharedStateType the [SharedStateType] for which an [SharedStateManager] should be fetched.
     * @param extensionName the name of the extension for which an [SharedStateManager] should be fetched.
     *        This should match [Extension.name] of an extension registered with the event hub.
     * @return [SharedStateManager] with [extensionName] provided if one was registered and initialized
     *         null otherwise
     */
    private fun getSharedStateManager(
        sharedStateType: SharedStateType,
        extensionName: String
    ): SharedStateManager? {
        val extensionContainer = getExtensionContainer(extensionName) ?: run {
            return null
        }
        val sharedStateManager = extensionContainer.getSharedStateManager(sharedStateType) ?: run {
            return null
        }
        return sharedStateManager
    }

    /**
     * Retrieves the appropriate shared state version for the event.
     *
     * @param sharedStateManager A [SharedStateManager] to version the event.
     * @param event An [Event] which may contain a specific event from which the correct shared state can be retrieved
     * @return Int denoting the version number
     */
    private fun resolveSharedStateVersion(
        sharedStateManager: SharedStateManager,
        event: Event?
    ): Int {
        // 1) If event is not null, pull the version number from internal map
        // 2) If event is null, start with version 0 if shared state is empty.
        //    We start with '0' because extensions can call createSharedState() to export initial state
        //    before handling any event and other extensions should be able to read this state.
        return when {
            event != null -> getEventNumber(event) ?: 0
            !sharedStateManager.isEmpty() -> lastEventNumber.incrementAndGet()
            else -> 0
        }
    }

    /**
     * Dispatch shared state update event for the [sharedStateType] and [extensionName]
     * @param sharedStateType The type of shared state set
     * @param extensionName Extension whose shared state was updated
     */
    private suspend fun dispatchSharedStateEvent(
        sharedStateType: SharedStateType,
        extensionName: String
    ) {
        val eventName =
            if (sharedStateType == SharedStateType.STANDARD) EventHubConstants.STATE_CHANGE else EventHubConstants.XDM_STATE_CHANGE
        val data =
            mapOf(EventHubConstants.EventDataKeys.Configuration.EVENT_STATE_OWNER to extensionName)

        val event = Event.Builder(eventName, EventType.HUB, EventSource.SHARED_STATE)
            .setEventData(data).build()
        dispatchInternal(event)
    }

    private suspend fun shareEventHubSharedState() {
        if (!hubStarted) return

        val extensionsInfo = mutableMapOf<String, Any?>()
        registeredExtensions.values.forEach {
            val extensionName = it.sharedStateName
            if (extensionName != null && extensionName != EventHubConstants.NAME) {
                val extensionInfo = mutableMapOf<String, Any?>(
                    EventHubConstants.EventDataKeys.FRIENDLY_NAME to it.friendlyName,
                    EventHubConstants.EventDataKeys.VERSION to it.version
                )
                it.metadata?.let { metadata ->
                    extensionInfo[EventHubConstants.EventDataKeys.METADATA] = metadata
                }

                extensionsInfo[extensionName] = extensionInfo
            }
        }

        val wrapperInfo = mapOf(
            EventHubConstants.EventDataKeys.TYPE to _wrapperType.wrapperTag,
            EventHubConstants.EventDataKeys.FRIENDLY_NAME to _wrapperType.friendlyName
        )

        val data = mapOf(
            EventHubConstants.EventDataKeys.VERSION to EventHubConstants.VERSION_NUMBER,
            EventHubConstants.EventDataKeys.WRAPPER to wrapperInfo,
            EventHubConstants.EventDataKeys.EXTENSIONS to extensionsInfo
        )

        createSharedStateInternal(
            SharedStateType.STANDARD,
            EventHubConstants.NAME,
            EventDataUtils.immutableClone(data),
            null
        )
    }
}

private object CompletionHandler {
    private val coroutineScope = CoroutineScope(SDKDispatcher.createExtensionDispatcher(1))
    private val map = mutableMapOf<String, Job>()
    private val handlerMap = mutableMapOf<String, AdobeCallbackWithError<Event>>()

    fun execute(runnable: Runnable) {
        coroutineScope.launch {
            try {
                runnable.run()
            } catch (ex: Exception) {
                Log.debug(
                    CoreConstants.LOG_TAG,
                    LOG_TAG,
                    "Exception thrown from callback - $ex"
                )
            }
        }
    }

    fun scheduleTimeoutHandler(
        triggerId: String,
        timeoutInMilliseconds: Long,
        handler: AdobeCallbackWithError<Event>
    ) {
        val job = scheduleTimeoutJob(timeoutInMilliseconds, handler)
        map[triggerId] = job
        handlerMap[triggerId] = handler
    }

    fun handleEvent(triggerId: String, event: Event) {
        map.remove(triggerId)?.cancel()
        coroutineScope.launch {
            handlerMap[triggerId]?.call(event)
        }
    }

    private fun scheduleTimeoutJob(
        timeoutInMilliseconds: Long,
        handler: AdobeCallbackWithError<Event>
    ): Job {
        return coroutineScope.launch {
            delay(timeoutInMilliseconds)
            handler.fail(AdobeError.CALLBACK_TIMEOUT)
        }
    }
}
