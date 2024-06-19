package com.adobe.marketing.mobile.util

import com.adobe.marketing.mobile.AdobeCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class CoroutineWrapper {

    companion object{
        fun launchCoroutine(coroutineScope: CoroutineScope, task: Runnable) {
            coroutineScope.launch {
                task.run()
            }
        }

        fun <T> asyncCoroutine(coroutineScope: CoroutineScope, block: () -> T, callback: AdobeCallback<T>) {
            coroutineScope.launch {
                val result = async {
                    block()
                }.await()
                callback.call(result)
            }
        }

    }

}