package com.adobe.marketing.mobile.internal.eventhub

import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.ExtensionV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal data class ExtensionV2Info(
    val name: String,
    val friendlyName: String?,
    val version: String?,
    val metadata: Map<String, String>?
)
internal object TestOnly{
    internal val dispatchedEvents = mutableListOf<Event>()
}


internal class ExtensionV2Manager {
    private val extensionV2ManagerScope = CoroutineScope(Dispatchers.IO)

    private val registeredExtensions: ConcurrentHashMap<String, Pair<ExtensionV2Info, SharedStateManager>> =
        ConcurrentHashMap()

    private val lastProcessedEventUUIDs: ConcurrentHashMap<String, String> =
        ConcurrentHashMap()

    private val _events =
        MutableSharedFlow<Event>(
            replay = 0,
            extraBufferCapacity = 100,
            onBufferOverflow = BufferOverflow.SUSPEND
        )
    private val eventFlow = _events.asSharedFlow()

    internal fun forwardEvent(event: Event) {
        TestOnly.dispatchedEvents.add(event)
        extensionV2ManagerScope.launch {
            _events.emit(event)
        }
    }

    internal fun getSharedStateManager(extensionName: String): SharedStateManager? {
        return registeredExtensions[extensionName]?.second
    }

    internal fun getRegisteredExtensionInfo(): Iterable<ExtensionV2Info> {
        return registeredExtensions.values.map { it.first }
    }

    private fun updateLastProcessedEventUUID(extensionName: String, uuid: String) {
        lastProcessedEventUUIDs[extensionName] = uuid
    }

    internal fun getLastProcessedEventUUID(extensionName: String): String? {
        return lastProcessedEventUUIDs[extensionName]
    }

    internal fun isRegistered(extensionClassName: String): Boolean {
        return registeredExtensions.containsKey(extensionClassName);
    }

    internal fun registerV2Extension(
        extensionClass: Class<out ExtensionV2>,
        completion: ((error: EventHubError?) -> Unit)? = null
    ) {
        val extensionV2: ExtensionV2 = extensionClass.getDeclaredConstructor().newInstance()
        val extensionName = extensionV2.name
        val sharedStateManager = SharedStateManager(extensionName)
        extensionV2.onRegistered { readyForEvent ->
            return@onRegistered ExtensionContainerV2(
                readyForEvent,
                eventFlow,
                extensionName
            ) { uuid ->
                updateLastProcessedEventUUID(extensionName, uuid)
            }
        }
        registeredExtensions[extensionClass.name] = Pair(
            ExtensionV2Info(
                extensionV2.name,
                extensionV2.friendlyName,
                extensionV2.version,
                extensionV2.metadata
            ), sharedStateManager
        )
        if (completion != null) {
            completion(null)
        }
    }

}