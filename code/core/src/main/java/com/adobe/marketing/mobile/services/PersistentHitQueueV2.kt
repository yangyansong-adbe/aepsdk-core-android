/*
  Copyright 2025 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.marketing.mobile.services

import com.adobe.marketing.mobile.internal.eventhub.SDKDispatcher
import com.adobe.marketing.mobile.util.retry.Retry
import com.adobe.marketing.mobile.util.retry.retryConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * Provides functionality for asynchronous processing of hits in a synchronous manner while
 * providing the ability to retry hits.
 */
class PersistentHitQueueV2(
    private val queue: DataQueue,
    private val processor: SuspendableHitProcessing,
) : HitQueuing() {
    private val hitProcessingScope = CoroutineScope(SDKDispatcher.createDispatcher(1))

    @Volatile
    var isProcessing = false

    override fun queue(entity: DataEntity): Boolean {
        val result = queue.add(entity)
        processNextHit()
        return result
    }

    override fun beginProcessing() {
        processNextHit()
    }

    override fun suspend() {
        hitProcessingScope.coroutineContext.cancelChildren()
    }

    override fun clear() {
        queue.clear()
    }

    override fun count(): Int {
        return queue.count()
    }

    override fun close() {
        suspend()
        queue.close()
    }

    /**
     * A Recursive function for processing persisted hits. I will continue processing all the Hits
     * until none are left in the DataQueue.
     */
    private fun processNextHit() {
        if (isProcessing) {
            return
        }

        hitProcessingScope.launch {
            Log.debug(
                ServiceConstants.LOG_TAG,
                "PersistentHitQueueV2",
                "ADB-HQ: ${Thread.currentThread().name}"
            )
            val entity = queue.peek() ?: return@launch
            isProcessing = true
            val config = retryConfig {
                intervalFunction({ _, _, _ -> processor.retryInterval(entity).toLong() })
            }
            val executor = Retry.createExecutor<Boolean?>(config).retryOnResult {
                return@retryOnResult it == false
            }
            executor.execute {
                val result = processor.processHit(entity)
                if (result) {
                    queue.remove()
                    isProcessing = false
                    processNextHit()
                }
                return@execute result
            }
            isProcessing = false

        }
    }
}