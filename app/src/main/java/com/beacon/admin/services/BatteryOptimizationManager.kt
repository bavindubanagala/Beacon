package com.beacon.admin.services

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.beacon.shared.models.DeviceStatusLight
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class LocationConfig(
    val priority: Int,
    val intervalMillis: Long,
    val minUpdateIntervalMillis: Long
)

@Singleton
class BatteryOptimizationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun calculateOptimalConfig(targetStatus: DeviceStatusLight): LocationConfig {
        val batteryPct = getBatteryPercentage()
        val isCharging = isDeviceCharging()

        // Force Power Saver if battery is critical and device is not charging
        if (batteryPct < 15 && !isCharging) {
            return LocationConfig(
                priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                intervalMillis = 900000L, // 15 minutes
                minUpdateIntervalMillis = 300000L // 5 minutes
            )
        }

        return when (targetStatus) {
            DeviceStatusLight.GREEN_LIVE -> LocationConfig(
                priority = Priority.PRIORITY_HIGH_ACCURACY,
                intervalMillis = 5000L, // 5 seconds
                minUpdateIntervalMillis = 2000L // 2 seconds
            )
            DeviceStatusLight.BLUE_INTERVAL -> LocationConfig(
                priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                intervalMillis = 60000L, // 1 minute
                minUpdateIntervalMillis = 30000L // 30 seconds
            )
            DeviceStatusLight.YELLOW_IDLE -> LocationConfig(
                priority = Priority.PRIORITY_PASSIVE,
                intervalMillis = 3600000L, // 1 hour
                minUpdateIntervalMillis = 1800000L // 30 minutes
            )
            DeviceStatusLight.RED_OFFLINE -> LocationConfig(
                priority = Priority.PRIORITY_PASSIVE,
                intervalMillis = Long.MAX_VALUE,
                minUpdateIntervalMillis = Long.MAX_VALUE
            )
            DeviceStatusLight.GRAY_UNPAIRED -> LocationConfig(
                priority = Priority.PRIORITY_PASSIVE,
                intervalMillis = Long.MAX_VALUE,
                minUpdateIntervalMillis = Long.MAX_VALUE
            )
        }
    }

    private fun getBatteryPercentage(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 100
    }

    private fun isDeviceCharging(): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }
}