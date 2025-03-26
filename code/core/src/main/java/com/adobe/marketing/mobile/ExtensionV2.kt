package com.adobe.marketing.mobile

import com.adobe.marketing.mobile.internal.CoreConstants
import com.adobe.marketing.mobile.services.Log

abstract class ExtensionV2 {
    abstract val name: String
    abstract val friendlyName: String?
    abstract val version: String?
    abstract val metadata: Map<String, String>?
    abstract fun onRegistered(buildExtensionApi: ExtensionApiV2Builder)
    open fun onUnregistered() {
        Log.trace(
            CoreConstants.LOG_TAG,
            "Extension[$name($version)]",
            "Extension unregistered successfully."
        )
    }
}

abstract class ExtensionV2Delegate : Extension(dummyContainer) {
    override fun getName(): String {
        return "ExtensionV2Delegate"
    }

    abstract fun getExtensionV2Class(): Class<out ExtensionV2>

}

private val dummyContainer = object : ExtensionApi() {
    override fun registerEventListener(
        eventType: String,
        eventSource: String,
        eventListener: ExtensionEventListener
    ) {
    }

    override fun dispatch(event: Event) {}
    override fun startEvents() {}
    override fun stopEvents() {}
    override fun createSharedState(state: MutableMap<String, Any>, event: Event?) {}
    override fun createPendingSharedState(event: Event?): SharedStateResolver? {
        return null
    }

    override fun getSharedState(
        extensionName: String,
        event: Event?,
        barrier: Boolean,
        resolution: SharedStateResolution
    ): SharedStateResult? {
        return null
    }

    override fun createXDMSharedState(state: MutableMap<String, Any>, event: Event?) {}
    override fun createPendingXDMSharedState(event: Event?): SharedStateResolver? {
        return null
    }

    override fun getXDMSharedState(
        extensionName: String,
        event: Event?,
        barrier: Boolean,
        resolution: SharedStateResolution
    ): SharedStateResult? {
        return null
    }

    override fun unregisterExtension() {}
    override fun getHistoricalEvents(
        eventHistoryRequests: Array<out EventHistoryRequest>,
        enforceOrder: Boolean,
        handler: EventHistoryResultHandler<Int>
    ) {
    }
}