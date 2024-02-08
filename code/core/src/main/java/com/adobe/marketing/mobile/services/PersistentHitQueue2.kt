package com.adobe.marketing.mobile.services

import com.adobe.marketing.mobile.internal.resilience.Retry
import com.adobe.marketing.mobile.internal.resilience.fixedWaitInterval
import com.adobe.marketing.mobile.internal.resilience.retryConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class PersistentHitQueue2(
    private val queue: DataQueue,
    private val processHit: (DataEntity) -> Boolean
) : HitQueuing() {
    private val suspended = AtomicBoolean(true)

    //TODO: add a function to set/update retry config later
    private val retryConfig = retryConfig {
        intervalFunction(fixedWaitInterval(), 5000L)
    }
    private var hitProcessingJob: Job? = null

    //TODO: should we use a global hit queue dispatcher??? It should be more efficient.
    private val hitDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    //TODO: the return value is not used in extensions, we can create a new interface for the new function
    override fun queue(entity: DataEntity?): Boolean {
        CoroutineScope(hitDispatcher).launch {
            queue.add(entity)
        }
        processHits()
        return true
    }

    override fun beginProcessing() {
        suspended.set(false)
        processHits()
    }

    override fun suspend() {
        suspended.set(true)
        cancelJob()
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
        cancelJob()
        hitDispatcher.close()
    }

    private fun cancelJob() {
        hitProcessingJob?.cancel()
    }

    private fun processHits() {
        if (suspended.get() && (hitProcessingJob?.isActive == true)) {
            return
        }

        hitProcessingJob = CoroutineScope(hitDispatcher).launch {
            while (true) {
                val entity = queue.peek() ?: return@launch
                val executor = Retry.createExecutor<Boolean?>(retryConfig)
                    .retryOnResult {
                        return@retryOnResult it ?: false
                    }

                val result = executor.execute {
                    return@execute processHit(entity)
                }

                if (result == true) {
                    queue.remove()
                }
            }
        }
    }
}