package com.adobe.marketing.mobile

import com.adobe.marketing.mobile.internal.CoreConstants
import com.adobe.marketing.mobile.services.Log

fun interface ExtensionEventListenerV2 {
    suspend fun hear(event: Event)
}

interface ExtensionApiV1 {
    fun registerEventListener(
        eventType: String,
        eventSource: String,
        eventListener: ExtensionEventListenerV2
    )

    fun dispatch(event: Event)

    suspend fun createSharedState(
        state: Map<String?, Any?>, event: Event?
    )

    suspend fun createPendingSharedState(
        event: Event?
    ): SharedStateResolver?

    suspend fun getSharedState(
        extensionName: String,
        event: Event?,
        barrier: Boolean,
        resolution: SharedStateResolution
    ): SharedStateResult?

    suspend fun createXDMSharedState(
        state: Map<String?, Any?>, event: Event?
    )

    suspend fun createPendingXDMSharedState(
        event: Event?
    ): SharedStateResolver?

    suspend fun getXDMSharedState(
        extensionName: String,
        event: Event?,
        barrier: Boolean,
        resolution: SharedStateResolution
    ): SharedStateResult?

    fun unregisterExtension()

    fun getHistoricalEvents(
        eventHistoryRequests: Array<EventHistoryRequest?>,
        enforceOrder: Boolean,
        handler: EventHistoryResultHandler<Int?>
    )
}

abstract class ExtensionV1 protected constructor(
    val api: ExtensionApiV1
) {
    protected abstract val name: String

    protected open val friendlyName: String?
        get() = null

    protected open val version: String?
        get() = null

    protected val metadata: Map<String, String>?
        get() = null

    protected open fun onRegistered() {
        Log.trace(CoreConstants.LOG_TAG, logTag, "Extension registered successfully.")
    }

    protected open fun onUnregistered() {
        Log.trace(CoreConstants.LOG_TAG, logTag, "Extension unregistered successfully.")
    }

    open suspend  fun readyForEvent(event: Event): Boolean {
        return true
    }

    private val logTag: String
        get() = "Extension[" + name + "(" + version + ")]"
}