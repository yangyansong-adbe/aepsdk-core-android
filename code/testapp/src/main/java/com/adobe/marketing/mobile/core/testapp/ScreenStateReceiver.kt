package com.adobe.marketing.mobile.core.testapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.os.UserManagerCompat

class ScreenStateReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent!!.action
        if (Intent.ACTION_SCREEN_ON == action) {
            Log.i("ScreenStateReceiver", "Screen is on. isUserUnlocked = ${
                UserManagerCompat
                    .isUserUnlocked(context!!)}")
        } else if (Intent.ACTION_SCREEN_OFF == action) {
            Log.i("ScreenStateReceiver", "Screen is off. isUserUnlocked = ${
                UserManagerCompat
                    .isUserUnlocked(context!!)}")
        }
    }

}