package com.beacon.tracker.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class LocationRequestConfig(
    val intervalMillis: Long,
    val minUpdateDistanceMeters: Float,
    val priority: Int
)

@Singleton
class AdaptiveLocationPolicyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "AdaptiveLocationPolicy"

    private val _isBatteryLow = MutableStateFlow(false)
    private val _isPowerConnected = MutableStateFlow(false)
    private val _isNearGeofence = MutableStateFlow(false)
    private val _isStationary = MutableStateFlow(false)
    private val _isSosActive = MutableStateFlow(false)
    private val _remoteCommandMode = MutableStateFlow("interval")

    private val _locationConfigFlow = MutableStateFlow(getInitialConfig())
    val locationConfigFlow: StateFlow<LocationRequestConfig> = _locationConfigFlow.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_LOW -> _isBatteryLow.value = true
                Intent.ACTION_BATTERY_OKAY -> _isBatteryLow.value = false
                Intent.ACTION_POWER_CONNECTED -> _isPowerConnected.value = true
                Intent.ACTION_POWER_DISCONNECTED -> _isPowerConnected.value = false
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(batteryReceiver, filter)
        
        // Initial state probe
        val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        _isPowerConnected.value = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (scale > 0) {
            _isBatteryLow.value = (level * 100 / scale.toFloat()) < 20f
        }

        startPolicyThrottling()
    }

    private fun startPolicyThrottling() {
        combine(
            _isSosActive,
            _remoteCommandMode,
            _isPowerConnected,
            _isNearGeofence,
            _isBatteryLow,
            _isStationary
        ) { args ->
            val sos = args[0] as Boolean
            val remoteMode = args[1] as String
            val power = args[2] as Boolean
            val nearFence = args[3] as Boolean
            val batteryLow = args[4] as Boolean
            val stationary = args[5] as Boolean
            
            computeTargetConfig(sos, remoteMode, power, nearFence, batteryLow, stationary)
        }.transformLatest { config ->
            if (_isSosActive.value) {
                emit(config)
            } else {
                delay(1000)
                emit(config)
            }
        }.distinctUntilChanged()
        .onEach { config ->
            Log.d(TAG, "Location Policy Transition: $config")
            _locationConfigFlow.value = config
        }.launchIn(scope)
    }

    private fun computeTargetConfig(
        sos: Boolean,
        remoteMode: String,
        power: Boolean,
        nearFence: Boolean,
        batteryLow: Boolean,
        stationary: Boolean
    ): LocationRequestConfig {
        if (sos) {
            return LocationRequestConfig(3000L, 0f, Priority.PRIORITY_HIGH_ACCURACY)
        }
        if (remoteMode == "live") {
            return LocationRequestConfig(5000L, 0f, Priority.PRIORITY_HIGH_ACCURACY)
        }
        if (nearFence || power) {
            return LocationRequestConfig(10000L, 2f, Priority.PRIORITY_HIGH_ACCURACY)
        }
        if (remoteMode == "standby") {
            return LocationRequestConfig(3600000L, 100f, Priority.PRIORITY_LOW_POWER)
        }
        if (batteryLow || stationary) {
            return LocationRequestConfig(60000L, 25f, Priority.PRIORITY_LOW_POWER)
        }
        return LocationRequestConfig(15000L, 5f, Priority.PRIORITY_BALANCED_POWER_ACCURACY)
    }

    fun setNearGeofence(isNear: Boolean) { _isNearGeofence.value = isNear }
    fun setStationary(isStationary: Boolean) { _isStationary.value = isStationary }
    fun setSosActive(isActive: Boolean) { _isSosActive.value = isActive }
    fun setRemoteCommandMode(mode: String) { _remoteCommandMode.value = mode }

    private fun getInitialConfig() = LocationRequestConfig(
        intervalMillis = 15000L,
        minUpdateDistanceMeters = 5f,
        priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY
    )

    fun cleanup() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) { }
    }
}
