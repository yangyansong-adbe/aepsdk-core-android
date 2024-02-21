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

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.ExperienceEvent
import com.adobe.marketing.mobile.app.kotlin.ui.theme.AepsdkcoreandroidTheme
import com.adobe.marketing.mobile.internal.util.isInternetAvailable
import com.adobe.marketing.mobile.services.ServiceProvider
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue


@OptIn(ExperimentalTime::class)
@Composable
fun HomeView(navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            val (result, duration) = measureTimedValue {
                (1..1000).forEach {
                    val context = ServiceProvider.getInstance().appContextService
                        .applicationContext ?: throw Error("Context is null")
                    if (isInternetAvailable(context)){
                        Log.e("HomeView", "Internet is available")
                    }else{
                        Log.e("HomeView", "Internet is not available")
                    }
                }
                // eg: productRepository.getBaseProducts(batchOfProductNos)
            }
            Log.e("Performance Test","operation took $duration")



        }) {
            Text(text = "Test")
        }
        Button(onClick = {
            networkRetryTest()
        }) {
            Text(text = "NetworkRetryTest")
        }
        Button(onClick = {
            navController.navigate(NavRoutes.CoreView.route)
        }) {
            Text(text = "Core")
        }
        Button(onClick = {
            navController.navigate(NavRoutes.ServicesView.route)
        }) {
            Text(text = "Service")
        }
        Button(onClick = {
            navController.navigate(NavRoutes.SignalView.route)
        }) {
            Text(text = "Signal")
        }
        Button(onClick = {
            navController.navigate(NavRoutes.LifecycleView.route)
        }) {
            Text(text = "Lifecycle")
        }
        Button(onClick = {
            navController.navigate(NavRoutes.IdentityView.route)
        }) {
            Text(text = "Identity")
        }
        Button(onClick = {
            navController.navigate(NavRoutes.PerformanceView.route)
        }) {
            Text(text = "PerformanceTest")
        }
    }

}

private fun networkRetryTest() {
    val reviewXdmData: MutableMap<String, Any> = HashMap()
    reviewXdmData["productSku"] = "demo123"
    reviewXdmData["rating"] = 5
    reviewXdmData["reviewText"] = "I love this demo!"
    reviewXdmData["reviewerId"] = "Anonymous user"

    val xdmData: MutableMap<String, Any> = HashMap()
    xdmData["eventType"] = "MyFirstXDMExperienceEvent"

    val experienceEvent = ExperienceEvent.Builder()
        .setXdmSchema(xdmData)
        .build()
    Edge.sendEvent(experienceEvent, null)
}

@Preview(showBackground = true)
@Composable
fun DefaultPreviewForHomeView() {
    AepsdkcoreandroidTheme {
        HomeView(rememberNavController())
    }
}