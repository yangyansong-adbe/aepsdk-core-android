package com.adobe.marketing.mobile.internal.util

import java.util.concurrent.ThreadFactory

internal class CustomThreadFactory(private val baseName: String) : ThreadFactory {
    private var counter = 0

    override fun newThread(r: Runnable): Thread {
        val thread = Thread(r, "$baseName-${counter++}")
        thread.isDaemon = false
        thread.priority = Thread.NORM_PRIORITY
        return thread
    }
}