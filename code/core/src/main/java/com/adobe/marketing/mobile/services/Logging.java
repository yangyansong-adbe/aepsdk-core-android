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

import java.util.Map;

public interface Logging {
    void trace(String tag, String message);

<<<<<<< HEAD:code/android-core-library/src/main/java/com/adobe/marketing/mobile/services/Logging.java
	void trace(String tag, String message);

	void trace(String tag, String message, Map<String, Object> metaData);

	void debug(String tag, String message);

	void debug(String tag, String message, Map<String, Object> metaData);

	void warning(String tag, String message);

	void warning(String tag, String message, Map<String, Object> metaData);

	void error(String tag, String message);

	void error(String tag, String message, Map<String, Object> metaData);

}
=======
    void debug(String tag, String message);

    void warning(String tag, String message);

    void error(String tag, String message);
}
>>>>>>> acf498063ec181141a98ce8f9e2d1f5e22db4336:code/core/src/main/java/com/adobe/marketing/mobile/services/Logging.java
