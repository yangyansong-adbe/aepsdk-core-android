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
package com.adobe.marketing.mobile

import kotlinx.coroutines.CoroutineScope

/**
 * Class that defines all the public methods an `Extension` may call to interface with the AEP
 * SDK.
 */
interface ExtensionApi {

    fun registerEventListener(
        eventType: String,
        eventSource: String,
        eventListener: ExtensionEventListener
    )

    fun dispatch(event: Event)

    /** Starts the `Event` queue for this extension  */
    fun startEvents()

    /** Stops the `Event` queue for this extension  */
    fun stopEvents()
    fun createSharedState(
        state: Map<String, Any?>, event: Event?
    )
    fun createPendingSharedState(
        event: Event?
    ): SharedStateResolver?

    fun getSharedState(
        extensionName: String,
        event: Event?,
        barrier: Boolean,
        resolution: SharedStateResolution
    ): SharedStateResult?

    fun createXDMSharedState(
        state: Map<String, Any?>, event: Event?
    )

    fun createPendingXDMSharedState(
        event: Event?
    ): SharedStateResolver?

    fun getXDMSharedState(
        extensionName: String,
        event: Event?,
        barrier: Boolean,
        resolution: SharedStateResolution
    ): SharedStateResult?

    fun unregisterExtension()

    fun getHistoricalEvents(
        eventHistoryRequests: Array<EventHistoryRequest>,
        enforceOrder: Boolean,
        handler: EventHistoryResultHandler<Int>
    )
//    val extensionScope: CoroutineScope
//    public void executeAsyncOnEventQueue(@NonNull Runnable task);
}
