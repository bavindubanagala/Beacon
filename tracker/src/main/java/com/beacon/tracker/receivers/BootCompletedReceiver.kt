package com.beacon.tracker.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.beacon.tracker.services.LocationTrackingService

class BootCompletedReceiver : BroadcastReceiver() {
    private val tag = "BootCompletedReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action in listOf(
            Intent.ACTION_BOOT_COMPLETED,
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )) {
            Log.d(tag, "Device boot detected, starting LocationTrackingService")

            val serviceIntent = Intent(context, LocationTrackingService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context?.startForegroundService(serviceIntent)
            } else {
                context?.startService(serviceIntent)
            }
        }
    }
}
