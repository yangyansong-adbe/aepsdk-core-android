package com.adobe.marketing.mobile.internal.util

import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

class ThreadUtils {
}
private val count = AtomicInteger(0)
internal fun createSingleThreadFactory(name: String): ThreadFactory {
    val number = count.incrementAndGet()

    return ThreadFactoryBuilder().setNameFormat("a-$number-${name.takeLast(8)}").build()
}
internal fun createMultipleThreadFactory(name: String): ThreadFactory {
    return ThreadFactoryBuilder().setNameFormat("a-$name-%d").build()
}