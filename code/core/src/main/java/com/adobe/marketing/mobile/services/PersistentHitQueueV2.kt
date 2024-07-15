/*
  Copyright 2024 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.marketing.mobile.services

import com.adobe.marketing.mobile.internal.AdobeDispatcher
import com.adobe.marketing.mobile.util.retry.Retry
import com.adobe.marketing.mobile.util.retry.retryConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Provides functionality for asynchronous processing of hits in a synchronous manner while
 * providing the ability to retry hits.
 */
class PersistentHitQueueV2(
    queue: DataQueue,
    processor: HitProcessingV2,
) : HitQueuing() {
    private val queue: DataQueue
    private val processor: HitProcessingV2
    private val suspended = AtomicBoolean(true)
    private val isTaskScheduled = AtomicBoolean(false)

    init {
        this.queue = queue
        this.processor = processor
    }

    override fun queue(entity: DataEntity): Boolean {
        val result = queue.add(entity)
        processNextHit()
        return result
    }

    override fun beginProcessing() {
        suspended.set(false)
        processNextHit()
    }

    override fun suspend() {
        suspended.set(true)
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
//        scheduledExecutorService.shutdown()
    }

    /**
     * A Recursive function for processing persisted hits. I will continue processing all the Hits
     * until none are left in the DataQueue.
     */
    private fun processNextHit() {
        if (suspended.get()) {
            return
        }

        // If taskScheduled is false, then set to true and return true.
        // If taskScheduled is true, then compareAndSet returns false
        if (!isTaskScheduled.compareAndSet(false, true)) {
            return
        }

        CoroutineScope(AdobeDispatcher).launch {
            val entity = queue.peek()
            if (entity == null) {
                isTaskScheduled.set(false)
                return@launch
            }
            val customIntervalFunction = { _: Long, _: Int, _: Long ->
                processor.retryInterval(entity).toLong()
            }
            val config = retryConfig {
                intervalFunction(customIntervalFunction)
            }
            val executor = Retry.createExecutor<Boolean?>(config)
                .retryOnException {
                    it.printStackTrace()
                    return@retryOnException true
                }.retryOnResult {
                    return@retryOnResult it == false
                }
            executor.execute {
                val result = processor.processHit(entity)
                if (result) {
                    queue.remove()
                    isTaskScheduled.set(false)
                    processNextHit()
                }
                return@execute result
            }

        }
    }
}
