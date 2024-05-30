package com.adobe.marketing.mobile.services

interface HitProcessingV2 {
    /**
     * Determines the interval at which a hit should be retried
     *
     * @param entity The hit whose retry interval is to be computed
     * @return Hit retry interval in seconds.
     */
    fun retryInterval(entity: DataEntity): Int

    suspend fun processHit(entity: DataEntity): Boolean
}
