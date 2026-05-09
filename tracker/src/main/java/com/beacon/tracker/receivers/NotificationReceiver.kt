package com.beacon.tracker.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationReceiver : BroadcastReceiver() {
    private val tag = "NotificationReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(tag, "Notification action received")
        // Handle notification actions if needed
    }
}
