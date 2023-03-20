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

package com.adobe.marketing.mobile.services;

import android.util.Log;

<<<<<<< HEAD:code/android-core-library/src/phone/java/com/adobe/marketing/mobile/services/AndroidLoggingService.java
import java.util.Map;

/**
 * The Android implementation for for {@link Logging}.
 */
=======
/** The Android implementation for for {@link Logging}. */
>>>>>>> acf498063ec181141a98ce8f9e2d1f5e22db4336:code/core/src/phone/java/com/adobe/marketing/mobile/services/AndroidLoggingService.java
class AndroidLoggingService implements Logging {

    private static final String TAG = "AdobeExperienceSDK";

    @Override
    public void trace(final String tag, final String message) {
        Log.v(TAG, tag + " - " + message);
    }

    @Override
    public void trace(String tag, String message, Map<String, Object> metaData) {

    }

    @Override
    public void debug(final String tag, final String message) {
        Log.d(TAG, tag + " - " + message);
    }

    @Override
    public void debug(String tag, String message, Map<String, Object> metaData) {

    }

    @Override
    public void warning(final String tag, final String message) {
        Log.w(TAG, tag + " - " + message);
    }

    @Override
    public void warning(String tag, String message, Map<String, Object> metaData) {

    }

    @Override
    public void error(final String tag, final String message) {
        Log.e(TAG, tag + " - " + message);
    }
<<<<<<< HEAD:code/android-core-library/src/phone/java/com/adobe/marketing/mobile/services/AndroidLoggingService.java

    @Override
    public void error(String tag, String message, Map<String, Object> metaData) {

    }

=======
>>>>>>> acf498063ec181141a98ce8f9e2d1f5e22db4336:code/core/src/phone/java/com/adobe/marketing/mobile/services/AndroidLoggingService.java
}
