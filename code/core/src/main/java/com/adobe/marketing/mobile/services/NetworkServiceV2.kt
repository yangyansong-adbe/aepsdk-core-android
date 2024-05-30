package com.adobe.marketing.mobile.services

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL

class NetworkServiceV2{

    suspend fun connect(request: NetworkRequest):HttpConnecting? = coroutineScope {
        var connection: HttpConnecting? = null
        if (request.url == null || !request.url.contains("https")) {
            Log.warning(
                ServiceConstants.LOG_TAG,
                TAG, String.format(
                    "Invalid URL (%s), only HTTPS protocol is supported",
                    request.url
                )
            )
            return@coroutineScope null
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
             */if (protocol != null && "https".equals(protocol, ignoreCase = true)) {
                try {
                    val httpConnectionHandler = HttpConnectionHandler(serverUrl)
                    if (httpConnectionHandler.setCommand(request.method)) {
                        httpConnectionHandler.setRequestProperty(headers)
                        httpConnectionHandler.setConnectTimeout(
                            request.connectTimeout * SEC_TO_MS_MULTIPLIER
                        )
                        httpConnectionHandler.setReadTimeout(
                            request.readTimeout * SEC_TO_MS_MULTIPLIER
                        )

                        launch {
                            connection = httpConnectionHandler.connect(request.body)
                        }.join()
                    }
                } catch (e: IOException) {
                    Log.warning(
                        ServiceConstants.LOG_TAG,
                        TAG, String.format(
                            "Could not create a connection to URL (%s) [%s]",
                            request.url,
                            if (e.localizedMessage != null) e.localizedMessage else e.message
                        )
                    )
                } catch (e: SecurityException) {
                    Log.warning(
                        ServiceConstants.LOG_TAG,
                        TAG, String.format(
                            "Could not create a connection to URL (%s) [%s]",
                            request.url,
                            if (e.localizedMessage != null) e.localizedMessage else e.message
                        )
                    )
                }
            }
        } catch (e: MalformedURLException) {
            Log.warning(
                ServiceConstants.LOG_TAG,
                TAG, String.format(
                    "Could not connect, invalid URL (%s) [%s]!!", request.url, e
                )
            )
        }
        return@coroutineScope connection
    }

    private val defaultHeaders: MutableMap<String, String>

        private get() {
            val defaultHeaders: MutableMap<String, String> = HashMap()
            val deviceInfoService = ServiceProvider.getInstance().deviceInfoService
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
        private val TAG = NetworkService::class.java.simpleName
        private const val REQUEST_HEADER_KEY_USER_AGENT = "User-Agent"
        private const val REQUEST_HEADER_KEY_LANGUAGE = "Accept-Language"
        private const val SEC_TO_MS_MULTIPLIER = 1000
    }
}