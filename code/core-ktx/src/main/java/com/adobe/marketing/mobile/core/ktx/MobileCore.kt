/*
  Copyright 2023 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
 */
package com.adobe.marketing.mobile.core.ktx

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.adobe.marketing.mobile.AdobeCallback
import com.adobe.marketing.mobile.Extension
import com.adobe.marketing.mobile.LoggingMode
import com.adobe.marketing.mobile.internal.ExtensionRegistrationService
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.services.ServiceProvider

object MobileCore {
    val EXTENSION_VERSION: String
        get() = com.adobe.marketing.mobile.MobileCore.extensionVersion()
}

private const val LOG_TAG = "MobileCoreKT"
private const val CLASS_NAME = "MobileCoreKT"


fun MobileCore.start(application: Application, init: InitOptions.() -> Unit) {
    val lifecycle = ProcessLifecycleOwner.get().lifecycle
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            lifecycleStart(null)
        }

        override fun onPause(owner: LifecycleOwner) {
            lifecyclePause()
        }
    })
    com.adobe.marketing.mobile.MobileCore.setApplication(application)
    val initOption = InitOptions()
    initOption.init()
}

class InitOptions {
    fun appId(appId: String) = com.adobe.marketing.mobile.MobileCore.configureWithAppID(appId)
    fun logLevel(level: LoggingMode) = com.adobe.marketing.mobile.MobileCore.setLogLevel(level)
    fun configureFileInAssets(fileName: String) =
        com.adobe.marketing.mobile.MobileCore.configureWithFileInAssets(fileName)

    fun updateConfiguration(configMap: Map<String, Any>) =
        com.adobe.marketing.mobile.MobileCore.updateConfiguration(configMap)

    fun registerExtensions(
        extensions: List<Class<out Extension?>> = emptyList(),
        completionHandler: AdobeCallback<*>
    ) {
        registerExtensionsInternal(extensions, completionHandler)
    }
}

fun MobileCore.lifecycleStart(additionalContextData: Map<String, String>?) =
    com.adobe.marketing.mobile.MobileCore.lifecycleStart(additionalContextData)

fun MobileCore.lifecyclePause() = com.adobe.marketing.mobile.MobileCore.lifecyclePause()

fun MobileCore.updateConfiguration(configMap: Map<String, Any?>) =
    com.adobe.marketing.mobile.MobileCore.updateConfiguration(configMap)

fun MobileCore.trackAction(
    action: String, contextData: Map<String, String?> = emptyMap()
) = com.adobe.marketing.mobile.MobileCore.trackAction(action, contextData)

fun MobileCore.trackState(
    state: String, contextData: Map<String, String?> = emptyMap()
) = com.adobe.marketing.mobile.MobileCore.trackState(state, contextData)

private fun registerExtensionsInternal(
    extensions: List<Class<out Extension?>>,
    completionCallback: AdobeCallback<*>?
) {
    val allExtensions = loadAllExtensions().toMutableList()
    if (extensions.isNotEmpty()) {
        allExtensions += extensions
    }

    com.adobe.marketing.mobile.MobileCore.registerExtensions(allExtensions, completionCallback)
}

private fun loadAllExtensions(): List<Class<out Extension?>> {
    val allExtensions: List<Class<out Extension?>> = ArrayList()
    val context = ServiceProvider.getInstance().appContextService.applicationContext
    return try {
        val manager = context!!.packageManager
        if (manager == null) {
            Log.debug(
                LOG_TAG,
                CLASS_NAME,
                "PackageManager is null."
            )
            return allExtensions
        }
        val info = manager.getServiceInfo(
            ComponentName(context, ExtensionRegistrationService::class.java),
            PackageManager.GET_META_DATA
        )
        if (info == null) {
            Log.debug(
                LOG_TAG,
                CLASS_NAME,
                "ExtensionRegistrationService has no service info."
            )
            return allExtensions
        }
        val metadata = info.metaData
        if (metadata == null) {
            Log.debug(
                LOG_TAG,
                CLASS_NAME,
                "No metadata found."
            )
            return allExtensions
        }
        val classNameList: MutableList<String> = mutableListOf()
        for (key in metadata.keySet()) {
            val value = metadata.getString(key)
            if (key.startsWith("com.adobe.marketing.mobile:") && value != null) {
                classNameList.add(value)
            }
        }
        return loadExtensionClassByName(classNameList)
    } catch (e: PackageManager.NameNotFoundException) {
        Log.debug(
            LOG_TAG,
            CLASS_NAME,
            "Application info not found."
        )
        allExtensions
    }
}

private fun loadExtensionClassByName(classNameList: List<String>): List<Class<out Extension?>> {
    val application = ServiceProvider.getInstance().appContextService.application ?: return listOf()
    val extensionClasses = mutableListOf<Class<out Extension>>()
    classNameList.forEach { className ->
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