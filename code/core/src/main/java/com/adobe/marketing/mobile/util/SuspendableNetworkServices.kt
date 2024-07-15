package com.adobe.marketing.mobile.util

import com.adobe.marketing.mobile.services.HttpConnecting
import com.adobe.marketing.mobile.services.NetworkRequest
import com.adobe.marketing.mobile.services.ServiceProvider
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object SuspendableNetworkServices {
    suspend fun connect(request: NetworkRequest): HttpConnecting? {
        return suspendCoroutine {
            ServiceProvider.getInstance().networkService.connectAsync(request){ connection -> it.resume(connection)}
        }
    }
}
