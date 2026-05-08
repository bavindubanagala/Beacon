package com.beacon.tracker.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log

/**
 * Monitors battery level changes and updates LocationTrackingService via local broadcast.
 * NOT used for direct Firebase writes—all data is batched into location updates.
 */
class BatteryMonitorService : Service() {
    private val tag = "BatteryMonitorService"
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val batteryPct = (level * 100) / scale

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                Log.d(tag, "Battery level: $batteryPct%, charging: $isCharging")

                // Broadcast to LocationTrackingService
                val broadcastIntent = Intent(BATTERY_UPDATE_ACTION)
                broadcastIntent.putExtra("battery_level", batteryPct)
                broadcastIntent.putExtra("is_charging", isCharging)
                sendBroadcast(broadcastIntent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "BatteryMonitorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "Starting battery monitoring")
        
        // Register broadcast receiver
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(tag, "BatteryMonitorService destroyed")
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.w(tag, "Failed to unregister receiver", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        const val BATTERY_UPDATE_ACTION = "com.beacon.tracker.BATTERY_UPDATE"
    }
}

