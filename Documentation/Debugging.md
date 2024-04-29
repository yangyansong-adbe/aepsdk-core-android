# Implementation Validation with Assurance

The AEP SDK offers an extension to quickly inspect, validate, debug data collection, and experiences for any mobile app using the AEP SDK. We built Assurance to do the heavy lifting of getting an SDK implementation right, so app developers can focus on creating engaging experiences.

## Quick Setup

#### Add Assurance to your app

Installation via [Maven](https://maven.apache.org/) & [Gradle](https://gradle.org/) is the easiest and recommended way to get the AEP SDK into your Android app. In your `build.gradle` file, include the latest version of Assurance:

##### Kotlin
```kotlin
implementation(platform("com.adobe.marketing.mobile:sdk-bom:3.+"))
implementation("com.adobe.marketing.mobile:assurance")
```

##### Groovy
```groovy
implementation platform('com.adobe.marketing.mobile:sdk-bom:3.+')
implementation 'com.adobe.marketing.mobile:assurance'
```

#### Update Extension Registration

Register Assurance extension by including `Assurance.EXTENSION` in the list of extensions registered with `MobileCore.registerExtensions`.

##### Java
```diff
+ import com.adobe.marketing.mobile.Assurance;

@Override
public void onCreate() {

+  MobileCore.registerExtensions(Arrays.asList(Assurance.EXTENSION, Lifecycle.EXTENSION, Identity.EXTENSION, ...), callback -> {
+      // ...
+  });
-  MobileCore.registerExtensions(Arrays.asList(Lifecycle.EXTENSION, Identity.EXTENSION, ...), callback -> {
-      // ...
-  });

} 
```

##### Kotlin
```diff
+ import com.adobe.marketing.mobile.Assurance;

override fun onCreate() {

+  MobileCore.registerExtensions(listOf(Assurance.EXTENSION, Lifecycle.EXTENSION, Identity.EXTENSION, ...)) {
+      // ...
+  };
-  MobileCore.registerExtensions(listOf(Lifecycle.EXTENSION, Identity.EXTENSION, ...)) {
-      // ...
-  };

} 
```

#### Next steps

Now that Assurance is installed and registered with Core, there are just a few more steps required.

- Assurance implementation instructions can be found [here](https://developer.adobe.com/client-sdks/documentation/platform-assurance-sdk/#add-the-aep-assurance-extension-to-your-app).
- Steps on how to use Assurance to validate SDK implementation can be found [here](https://developer.adobe.com/client-sdks/documentation/platform-assurance/tutorials/).