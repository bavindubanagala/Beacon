package com.beacon.admin.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.beacon.shared.models.DeviceStatus
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject
    lateinit var fusedLocationClient: FusedLocationProviderClient

    @Inject
    lateinit var batteryOptimizationManager: BatteryOptimizationManager

    private lateinit var locationCallback: LocationCallback
    private var currentStatus: DeviceStatus = DeviceStatus.GREEN_LIVE

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        setupLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_STOP -> stopSelf()
                ACTION_START -> {
                    val statusName = intent.getStringExtra(EXTRA_TRACKING_MODE)
                    if (statusName != null) {
                        try {
                            currentStatus = DeviceStatus.valueOf(statusName)
                            restartLocationUpdates()
                        } catch (e: Exception) {
                            // Fallback to existing currentStatus
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun setupLocationUpdates() {
        val config = batteryOptimizationManager.calculateOptimalConfig(currentStatus)
        val locationRequest = LocationRequest.Builder(config.priority, config.intervalMillis)
            .setMinUpdateIntervalMillis(config.minUpdateIntervalMillis)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    // Location payload handling (routed to TelemetrySocketEngine in production)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // Permission checks handled prior to launching service
        }
    }

    private fun restartLocationUpdates() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        setupLocationUpdates()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Beacon Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active background location tracking channel"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Beacon Location Engine Active")
            .setContentText("Monitoring live device coordinates with battery optimization...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "beacon_location_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        const val EXTRA_TRACKING_MODE = "EXTRA_TRACKING_MODE"
    }
}