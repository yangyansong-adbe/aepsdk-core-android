package com.adobe.marketing.mobile.simplification.internal

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.adobe.marketing.mobile.AdobeCallback
import com.adobe.marketing.mobile.Extension
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.internal.configuration.ConfigurationExtension
import com.adobe.marketing.mobile.internal.eventhub.EventHub
import com.adobe.marketing.mobile.services.Log
import dalvik.system.DexFile
import java.util.*

internal object SDKInitializer {
    val lifecycle = ProcessLifecycleOwner.get().lifecycle

    private val CLASS_NAMES = listOf(
        "com.adobe.marketing.mobile.lifecycle.LifecycleExtension",
        "com.adobe.marketing.mobile.signal.SignalExtension"
    )

    private val PACKAGE_NAMES = listOf(
        "com.adobe.marketing.mobile.lifecycle.", "com.adobe.marketing.mobile.signal."
    )

    private fun observeAppLifecycle() {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                //TODO: will be called when initializing the app
            }

            override fun onStart(owner: LifecycleOwner) {
                //TODO: will be called when launching the app
            }

            override fun onResume(owner: LifecycleOwner) {
//                MobileCore.lifecycleStart(null)
            }

            override fun onPause(owner: LifecycleOwner) {
                MobileCore.lifecyclePause()
            }

            override fun onStop(owner: LifecycleOwner) {
                //TODO: will be called when stopping the app
            }
        })
    }

    @JvmName("start")
    internal fun start(
        initOptions: InitOptions, completionCallback: AdobeCallback<Any>?
    ) {
        EventHub.shared.registerExtension(
            ConfigurationExtension::class.java
        )

        initOptions.logLevel?.let {
            MobileCore.setLogLevel(initOptions.logLevel)
        }
        MobileCore.setApplication(initOptions.application)
        initOptions.appId?.let {
            MobileCore.configureWithAppID(initOptions.appId)
        }

        val extensionClasses = loadExtensionClasses(initOptions.application)
//        val extensionClasses = loadExtensionClassesWithSuperPower(application)
        MobileCore.registerExtensions(extensionClasses) {
            if(!initOptions.disableLifecycleStart()){
                MobileCore.lifecycleStart(null)
            }

            completionCallback?.call(it)
        }

    }

    @Suppress("unused")
    private fun loadExtensionClasses(application: Application): List<Class<out Extension>> {
        val extensionClasses = mutableListOf<Class<out Extension>>()
        CLASS_NAMES.forEach { className ->
            try {
                val classLoader = application.classLoader
                @Suppress("UNCHECKED_CAST") val extensionClass =
                    classLoader.loadClass(className) as? Class<out Extension>
                extensionClass?.let {
                    extensionClasses.add(it)
                    @Suppress("UNCHECKED_CAST") Log.debug(
                        "Core", "InternalExtensions", "$extensionClass extension is found."
                    )
                }
            } catch (e: Exception) {
                Log.error(
                    "Core", "InternalExtensions", "$className is not found."
                )
            }

        }
        return extensionClasses
    }

    private fun isAdobeClasses(className: String): Boolean {
        PACKAGE_NAMES.forEach {
            if (className.startsWith(it)) return true
        }
        return false
    }

    @Suppress("unused")
    private fun loadExtensionClassesWithSuperPower(application: Application): List<Class<out Extension>> {
        val classLoader = application.classLoader
        val path = application.packageCodePath
        //https://developer.android.com/reference/dalvik/system/DexFile?hl=en
        //This class was deprecated in API level 26.
        val dexFile = DexFile(path)
        val classNames: Enumeration<String> = dexFile.entries()
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
                                "Core", "InternalExtensions", "$extensionClass extension is found."
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.error(
                        "Core", "InternalExtensions", "$className is not found."
                    )
                }
            }
        }
        return extensionClasses
    }

}