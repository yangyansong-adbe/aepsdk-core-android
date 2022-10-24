package com.adobe.marketing.mobile.simplification.extension

import android.app.Application
import com.adobe.marketing.mobile.AdobeCallback
import com.adobe.marketing.mobile.Extension
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.services.Log
import dalvik.system.DexFile
import java.util.*

internal object InternalExtensions {

    private val CLASS_NAMES = listOf(
        "com.adobe.marketing.mobile.lifecycle.LifecycleExtension",
        "com.adobe.marketing.mobile.signal.SignalExtension"
    )

    private val PACKAGE_NAMES = listOf(
        "com.adobe.marketing.mobile.lifecycle.",
        "com.adobe.marketing.mobile.signal."
    )

    @JvmName("registerInternalExtensions")
    internal fun registerInternalExtensions(
        appId: String?,
        application: Application,
        completionCallback: AdobeCallback<*>?
    ) {
        MobileCore.setApplication(application)
        appId?.let {
            MobileCore.configureWithAppID(appId)
        }
        val classLoader = application.classLoader
        val extensionClasses = mutableListOf<Class<out Extension>>()
        CLASS_NAMES.forEach { className ->
            try {
                @Suppress("UNCHECKED_CAST") val extensionClass =
                    classLoader.loadClass(className) as? Class<out Extension>
                extensionClass?.let {
                    extensionClasses.add(it)
                    @Suppress("UNCHECKED_CAST")
                    Log.debug(
                        "Core",
                        "InternalExtensions",
                        "$extensionClass extension is found."
                    )
                }
            } catch (e: Exception) {
                Log.error(
                    "Core",
                    "InternalExtensions",
                    "$className is not found."
                )
            }

        }
        MobileCore.registerExtensions(extensionClasses, completionCallback)
    }

    private fun isAdobeClasses(className: String): Boolean {
        PACKAGE_NAMES.forEach {
            if (className.startsWith(it)) return true
        }
        return false
    }

    @JvmName("registerInternalExtensionsWithSuperPower")
    internal fun registerInternalExtensionsWithSuperPower(
        appId: String?,
        application: Application,
        completionCallback: AdobeCallback<*>?
    ) {
        MobileCore.setApplication(application)
        appId?.let {
            MobileCore.configureWithAppID(appId)
        }
        val path = application.packageCodePath
        //https://developer.android.com/reference/dalvik/system/DexFile?hl=en
        //This class was deprecated in API level 26.
        val dexFile = DexFile(path)
        val classNames: Enumeration<String> = dexFile.entries()
        val classLoader = application.classLoader
        val extensionClasses = mutableListOf<Class<out Extension>>()
        for (className in classNames) {
//            if (className.startsWith("com.adobe.marketing.mobile.")) {
            if (isAdobeClasses(className)) {
                try {
                    val adobeClass = classLoader.loadClass(className)
                    if (adobeClass.isAnnotationPresent(AdobeExtensions::class.java)) {
                        val extensionClass = adobeClass as? Class<out Extension>
                        extensionClass?.let {
                            extensionClasses.add(extensionClass)
                            Log.debug(
                                "Core",
                                "InternalExtensions",
                                "$extensionClass extension is found."
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.error(
                        "Core",
                        "InternalExtensions",
                        "$className is not found."
                    )
                }
            }
        }

        MobileCore.registerExtensions(extensionClasses, completionCallback)
    }

}