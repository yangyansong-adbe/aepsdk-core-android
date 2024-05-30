package com.adobe.marketing.mobile.internal

import com.adobe.marketing.mobile.internal.util.createMultipleThreadFactory
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

val AdobeDispatcher = Executors.newFixedThreadPool(2, createMultipleThreadFactory("AdobeDispatcher")).asCoroutineDispatcher()