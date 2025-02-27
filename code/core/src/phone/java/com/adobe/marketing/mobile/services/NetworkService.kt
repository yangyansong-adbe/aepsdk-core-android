/*
  Copyright 2025 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.marketing.mobile.services

import androidx.annotation.VisibleForTesting
import com.adobe.marketing.mobile.internal.util.isInternetAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


/** Implementation of [Networking] service  */
internal class NetworkService : Networking {

    override fun connectAsync(request: NetworkRequest, callback: NetworkCallback?) {
        val connectivityManager =
            ServiceProvider.getInstance().appContextService.connectivityManager
        if (connectivityManager != null) {
            if (!isInternetAvailable(connectivityManager)) {
                Log.trace(ServiceConstants.LOG_TAG, TAG, "The Android device is offline.")
                callback?.call(null)
                return
            }
        } else {
            Log.debug(
                ServiceConstants.LOG_TAG,
                TAG,
                "ConnectivityManager instance is null. Unable to the check the network"
                        + " condition."
            )
        }
        try {
            CoroutineScope(Dispatchers.IO).launch {
                Log.warning(
                    ServiceConstants.LOG_TAG,
                    TAG,
                    "ADB-NS: ${Thread.currentThread().name}"
                )
                val connection = doConnection(request)
                callback?.call(connection)
            }
        } catch (e: Exception) {
            // to catch RejectedExecutionException when the thread pool is saturated
            Log.warning(
                ServiceConstants.LOG_TAG,
                TAG,
                String.format(
                    "Failed to send request for (%s) [%s]",
                    request.url,
                    (if (e.localizedMessage != null
                    ) e.localizedMessage
                    else e.message)
                )
            )

            callback?.call(null)
        }
    }

    /**
     * Performs the actual connection to the specified `url`.
     *
     *
     * It sets the default connection headers if none were provided through the `requestProperty` parameter. You can override the default user agent and language headers if
     * they are present in `requestProperty`
     *
     *
     * This method will return null, if failed to establish connection to the resource.
     *
     * @param request [NetworkRequest] used for connection
     * @return [HttpConnecting] instance, representing a connection attempt
     */
    private fun doConnection(request: NetworkRequest): HttpConnecting? {
        var connection: HttpConnecting? = null

        if (request.url == null || !request.url.contains("https")) {
            Log.warning(
                ServiceConstants.LOG_TAG,
                TAG,
                String.format(
                    "Invalid URL (%s), only HTTPS protocol is supported",
                    request.url
                )
            )
            return null
        }

        val headers = defaultHeaders

        if (request.headers != null) {
            headers.putAll(request.headers)
        }

        try {
            val serverUrl = URL(request.url)
            val protocol = serverUrl.protocol

            /*
             * Only https is supported as of now.
             * No special handling for https is supported for now.
             */
            if (protocol != null && "https".equals(protocol, ignoreCase = true)) {
                try {
                    val httpConnectionHandler =
                        HttpConnectionHandler(serverUrl)

                    if (httpConnectionHandler.setCommand(request.method)) {
                        httpConnectionHandler.setRequestProperty(headers)
                        httpConnectionHandler.setConnectTimeout(
                            request.connectTimeout * SEC_TO_MS_MULTIPLIER
                        )
                        httpConnectionHandler.setReadTimeout(
                            request.readTimeout * SEC_TO_MS_MULTIPLIER
                        )
                        connection = httpConnectionHandler.connect(request.body)
                    }
                } catch (e: IOException) {
                    Log.warning(
                        ServiceConstants.LOG_TAG,
                        TAG,
                        String.format(
                            "Could not create a connection to URL (%s) [%s]",
                            request.url,
                            (if (e.localizedMessage != null
                            ) e.localizedMessage
                            else e.message)
                        )
                    )
                } catch (e: SecurityException) {
                    Log.warning(
                        ServiceConstants.LOG_TAG,
                        TAG,
                        String.format(
                            "Could not create a connection to URL (%s) [%s]",
                            request.url,
                            (if (e.localizedMessage != null
                            ) e.localizedMessage
                            else e.message)
                        )
                    )
                }
            }
        } catch (e: MalformedURLException) {
            Log.warning(
                ServiceConstants.LOG_TAG,
                TAG,
                String.format(
                    "Could not connect, invalid URL (%s) [%s]!!", request.url, e
                )
            )
        }

        return connection
    }

    private val defaultHeaders: MutableMap<String, String>
        /**
         * Creates a `Map<String, String>` with the default headers: default user agent and active
         * language.
         *
         *
         * This method is used to retrieve the default headers to be appended to any network
         * connection made by the SDK.
         *
         * @return `Map<String, String>` containing the default user agent and active language if
         * `#DeviceInforming` is not null or an empty Map otherwise
         * @see DeviceInforming.getDefaultUserAgent
         * @see DeviceInforming.getLocaleString
         */
        get() {
            val defaultHeaders: MutableMap<String, String> = HashMap()
            val deviceInfoService =
                ServiceProvider.getInstance().deviceInfoService
                    ?: return defaultHeaders

            val userAgent = deviceInfoService.defaultUserAgent

            if (!isNullOrEmpty(userAgent)) {
                defaultHeaders[REQUEST_HEADER_KEY_USER_AGENT] = userAgent
            }

            val locale = deviceInfoService.localeString

            if (!isNullOrEmpty(locale)) {
                defaultHeaders[REQUEST_HEADER_KEY_LANGUAGE] = locale
            }

            return defaultHeaders
        }

    private fun isNullOrEmpty(str: String?): Boolean {
        return str == null || str.trim { it <= ' ' }.isEmpty()
    }

    companion object {
        private val TAG: String = NetworkService::class.java.simpleName
        private const val REQUEST_HEADER_KEY_USER_AGENT = "User-Agent"
        private const val REQUEST_HEADER_KEY_LANGUAGE = "Accept-Language"
        private const val THREAD_POOL_CORE_SIZE = 0
        private const val THREAD_POOL_MAXIMUM_SIZE = 32
        private const val THREAD_POOL_KEEP_ALIVE_TIME = 60
        private const val SEC_TO_MS_MULTIPLIER = 1000
    }
}

//internal class NetworkService : Networking {
//    private val executorService: ExecutorService
//    private class CustomThreadFactory(private val baseName: String) : ThreadFactory {
//        private var counter = 0
//
//        override fun newThread(r: Runnable): Thread {
//            val thread = Thread(r, "$baseName-${counter++}")
//            thread.isDaemon = false
//            thread.priority = Thread.NORM_PRIORITY
//            return thread
//        }
//    }
//    constructor() {
//        // define THREAD_POOL_MAXIMUM_SIZE instead of using a unbounded thread pool, mainly to
//        // prevent a wrong usage from extensions
//        // to blow off the Android system.
//        executorService =
//            ThreadPoolExecutor(
//                THREAD_POOL_CORE_SIZE,
//                THREAD_POOL_MAXIMUM_SIZE,
//                THREAD_POOL_KEEP_ALIVE_TIME.toLong(),
//                TimeUnit.SECONDS,
//                SynchronousQueue<Runnable>(),
//                CustomThreadFactory("ADB-NS")
//            )
//    }
//
//    @VisibleForTesting
//    constructor(executorService: ExecutorService) {
//        this.executorService = executorService
//    }
//
//    override fun connectAsync(request: NetworkRequest, callback: NetworkCallback?) {
//        val connectivityManager =
//            ServiceProvider.getInstance().appContextService.connectivityManager
//        if (connectivityManager != null) {
//            if (!isInternetAvailable(connectivityManager)) {
//                Log.trace(ServiceConstants.LOG_TAG, TAG, "The Android device is offline.")
//                callback!!.call(null)
//                return
//            }
//        } else {
//            Log.debug(
//                ServiceConstants.LOG_TAG,
//                TAG,
//                "ConnectivityManager instance is null. Unable to the check the network"
//                        + " condition."
//            )
//        }
//        try {
//            executorService.submit {
//                val connection = doConnection(request)
//                callback?.call(connection)
//                    ?: // If no callback is passed by the client, close the connection.
//                    connection?.close()
//            }
//        } catch (e: Exception) {
//            // to catch RejectedExecutionException when the thread pool is saturated
//            Log.warning(
//                ServiceConstants.LOG_TAG,
//                TAG,
//                String.format(
//                    "Failed to send request for (%s) [%s]",
//                    request.url,
//                    (if (e.localizedMessage != null)
//                        e.localizedMessage
//                    else
//                        e.message)
//                )
//            )
//
//            callback?.call(null)
//        }
//    }
//
//    /**
//     * Performs the actual connection to the specified `url`.
//     *
//     *
//     * It sets the default connection headers if none were provided through the `requestProperty` parameter. You can override the default user agent and language headers if
//     * they are present in `requestProperty`
//     *
//     *
//     * This method will return null, if failed to establish connection to the resource.
//     *
//     * @param request [NetworkRequest] used for connection
//     * @return [HttpConnecting] instance, representing a connection attempt
//     */
//    private fun doConnection(request: NetworkRequest): HttpConnecting? {
//        var connection: HttpConnecting? = null
//
//        if (request.url == null || !request.url.contains("https")) {
//            Log.warning(
//                ServiceConstants.LOG_TAG,
//                TAG,
//                String.format(
//                    "Invalid URL (%s), only HTTPS protocol is supported",
//                    request.url
//                )
//            )
//            return null
//        }
//
//        val headers = getDefaultHeaders()
//
//        if (request.headers != null) {
//            headers.putAll(request.headers)
//        }
//
//        try {
//            val serverUrl = URL(request.url)
//            val protocol = serverUrl.protocol
//
//            /*
//             * Only https is supported as of now.
//             * No special handling for https is supported for now.
//             */
//            if (protocol != null && "https".equals(protocol, ignoreCase = true)) {
//                try {
//                    val httpConnectionHandler =
//                        HttpConnectionHandler(serverUrl)
//
//                    if (httpConnectionHandler.setCommand(request.method)) {
//                        httpConnectionHandler.setRequestProperty(headers)
//                        httpConnectionHandler.setConnectTimeout(
//                            request.connectTimeout * SEC_TO_MS_MULTIPLIER
//                        )
//                        httpConnectionHandler.setReadTimeout(
//                            request.readTimeout * SEC_TO_MS_MULTIPLIER
//                        )
//                        connection = httpConnectionHandler.connect(request.body)
//                    }
//                } catch (e: IOException) {
//                    Log.warning(
//                        ServiceConstants.LOG_TAG,
//                        TAG,
//                        String.format(
//                            "Could not create a connection to URL (%s) [%s]",
//                            request.url,
//                            (if (e.localizedMessage != null)
//                                e.localizedMessage
//                            else
//                                e.message)
//                        )
//                    )
//                } catch (e: SecurityException) {
//                    Log.warning(
//                        ServiceConstants.LOG_TAG,
//                        TAG,
//                        String.format(
//                            "Could not create a connection to URL (%s) [%s]",
//                            request.url,
//                            (if (e.localizedMessage != null)
//                                e.localizedMessage
//                            else
//                                e.message)
//                        )
//                    )
//                }
//            }
//        } catch (e: MalformedURLException) {
//            Log.warning(
//                ServiceConstants.LOG_TAG,
//                TAG,
//                String.format(
//                    "Could not connect, invalid URL (%s) [%s]!!", request.url, e
//                )
//            )
//        }
//
//        return connection
//    }
//
//    /**
//     * Creates a `Map<String, String>` with the default headers: default user agent and active
//     * language.
//     *
//     *
//     * This method is used to retrieve the default headers to be appended to any network
//     * connection made by the SDK.
//     *
//     * @return `Map<String, String>` containing the default user agent and active language if
//     * `#DeviceInforming` is not null or an empty Map otherwise
//     * @see DeviceInforming.getDefaultUserAgent
//     * @see DeviceInforming.getLocaleString
//     */
//    private fun getDefaultHeaders(): MutableMap<String, String> {
//        val defaultHeaders: MutableMap<String, String> = HashMap()
//        val deviceInfoService =
//            ServiceProvider.getInstance().deviceInfoService
//                ?: return defaultHeaders
//
//        val userAgent = deviceInfoService.defaultUserAgent
//
//        if (!isNullOrEmpty(userAgent)) {
//            defaultHeaders[REQUEST_HEADER_KEY_USER_AGENT] = userAgent
//        }
//
//        val locale = deviceInfoService.localeString
//
//        if (!isNullOrEmpty(locale)) {
//            defaultHeaders[REQUEST_HEADER_KEY_LANGUAGE] = locale
//        }
//
//        return defaultHeaders
//    }
//
//    private fun isNullOrEmpty(str: String?): Boolean {
//        return str == null || str.trim { it <= ' ' }.isEmpty()
//    }
//
//    companion object {
//        private val TAG: String = NetworkService::class.java.simpleName
//        private const val REQUEST_HEADER_KEY_USER_AGENT = "User-Agent"
//        private const val REQUEST_HEADER_KEY_LANGUAGE = "Accept-Language"
//        private const val THREAD_POOL_CORE_SIZE = 0
//        private const val THREAD_POOL_MAXIMUM_SIZE = 32
//        private const val THREAD_POOL_KEEP_ALIVE_TIME = 60
//        private const val SEC_TO_MS_MULTIPLIER = 1000
//    }
//}