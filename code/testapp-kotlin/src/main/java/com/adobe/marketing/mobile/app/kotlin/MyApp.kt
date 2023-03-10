/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
 */
package com.adobe.marketing.mobile.app.kotlin

import android.app.Application
import com.adobe.marketing.mobile.LoggingMode
import com.adobe.marketing.mobile.MobilePrivacyStatus
import com.adobe.marketing.mobile.core.ktx.MobileCore
import com.adobe.marketing.mobile.core.ktx.start
import com.adobe.marketing.mobile.core.ktx.trackState
import com.adobe.marketing.mobile.services.Log

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        MobileCore.start(this) {
            logLevel(LoggingMode.VERBOSE)
            privacyStatus(MobilePrivacyStatus.OPT_IN)
            appId("App_ID")
//            resetIdentities()
//            clearUpdatedConfiguration()
//            resetIdentities()
//            configureFileInAssets("abc.json")
//            configureWithFileInPath("path_to_file")
//            largeIconResourceID(1)
//            smallIconResourceID(2)
            updateConfiguration(mapOf("global.privacy" to "optedout"))
//            registerExtensions(listOf(PerfExtension::class.java)) {}
            registerExtensions {
                Log.debug("tag", "MyApp", "SDK has started.")
            }
        }

        MobileCore.trackState("state_1")
        MobileCore.trackState("state_1", mapOf("key" to "value"))
        Log.debug("tag", "MyApp", "Core version is : ${MobileCore.EXTENSION_VERSION}.")
    }

}
