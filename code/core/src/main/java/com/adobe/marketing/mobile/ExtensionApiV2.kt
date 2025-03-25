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

    // TODO: it's better to notify eventhub through an Event
//    suspend fun unregisterExtension()

    suspend fun getHistoricalEvents(
        eventHistoryRequests: Array<EventHistoryRequest?>,
        enforceOrder: Boolean
    ): Int?
}


internal class ExtensionContainerV2(private val readyForEvent: ReadyForEvent) : ExtensionApiV2 {

    override fun registerEventListener(
        eventType: String,
        eventSource: String,
        eventListener: ProcessEvent
    ) {
        TODO("Not yet implemented")
    }

    override fun dispatch(event: Event) {
        TODO("Not yet implemented")
    }

    override suspend fun createSharedState(state: Map<String?, Any?>, event: Event?) {
        TODO("Not yet implemented")
    }

    override suspend fun createPendingSharedState(event: Event?): SharedStateResolver? {
        TODO("Not yet implemented")
    }

    override suspend fun getSharedState(
        extensionName: String,
        event: Event?,
        barrier: Boolean,
        resolution: SharedStateResolution
    ): SharedStateResult? {
        TODO("Not yet implemented")
    }

    override suspend fun createXDMSharedState(state: Map<String?, Any?>, event: Event?) {
        TODO("Not yet implemented")
    }

    override suspend fun createPendingXDMSharedState(event: Event?): SharedStateResolver? {
        TODO("Not yet implemented")
    }

    override suspend fun getXDMSharedState(
        extensionName: String,
        event: Event?,
        barrier: Boolean,
        resolution: SharedStateResolution
    ): SharedStateResult? {
        TODO("Not yet implemented")
    }

    override suspend fun getHistoricalEvents(
        eventHistoryRequests: Array<EventHistoryRequest?>,
        enforceOrder: Boolean
    ): Int? {
        TODO("Not yet implemented")
    }

}
