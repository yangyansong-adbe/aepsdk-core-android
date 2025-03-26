package com.adobe.marketing.mobile

typealias ProcessEvent = suspend (Event) -> Unit
typealias ReadyForEvent = suspend (Event) -> Boolean
typealias ExtensionApiV2Builder = (ReadyForEvent) -> ExtensionApiV2

interface ExtensionApiV2 {

    fun registerEventListener(
        eventType: String,
        eventSource: String,
        eventListener: ProcessEvent
    )

    fun dispatch(event: Event)

    suspend fun createSharedState(
        state: MutableMap<String, Any?>, event: Event?
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
        state: MutableMap<String, Any?>, event: Event?
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

    // TODO: it's better to notify eventhub through an Event
//    suspend fun unregisterExtension()

    suspend fun getHistoricalEvents(
        eventHistoryRequests: Array<EventHistoryRequest>,
        enforceOrder: Boolean
    ): Int?
}


