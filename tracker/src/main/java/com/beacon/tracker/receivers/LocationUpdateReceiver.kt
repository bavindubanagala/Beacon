package com.beacon.tracker.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class LocationUpdateReceiver : BroadcastReceiver() {
    private val tag = "LocationUpdateReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(tag, "Location update broadcast received")
        // Handle location updates if needed
    }
}
