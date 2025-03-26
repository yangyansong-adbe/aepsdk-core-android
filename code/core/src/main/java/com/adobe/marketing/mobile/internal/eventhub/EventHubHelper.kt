package com.adobe.marketing.mobile.internal.eventhub

import com.adobe.marketing.mobile.Extension
import com.adobe.marketing.mobile.ExtensionV2
import com.adobe.marketing.mobile.ExtensionV2Delegate

internal fun isExtensionV2Delegate(className: Class<out Extension>): Boolean {
    return ExtensionV2Delegate::class.java.isAssignableFrom(className)
}

internal fun identifyExtensionClasses(extensions: Set<Class<out Extension>>): Pair<Set<Class<out Extension>>, Set<Class<out ExtensionV2>>> {
    val v1Extensions = mutableSetOf<Class<out Extension>>()
    val v2Extensions = mutableSetOf<Class<out ExtensionV2>>()
    for (extension in extensions) {
        if (isExtensionV2Delegate(extension)) {
            try {
                @Suppress("UNCHECKED_CAST")
                val delegateClass = extension as Class<out ExtensionV2Delegate>
                val delegateObject =
                    delegateClass.getDeclaredConstructor().newInstance() as ExtensionV2Delegate
                v2Extensions.add(delegateObject.getExtensionV2Class())
            } catch (e: Exception) {
                // log error
            }
        } else {
            v1Extensions.add(extension)
        }
    }
    return Pair(v1Extensions, v2Extensions)
}