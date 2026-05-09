package com.beacon.tracker.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.beacon.shared.constants.NotificationDefaults
import com.beacon.shared.constants.TrackingDefaults
import com.beacon.shared.models.Location as BeaconLocation
import com.beacon.tracker.MainActivity
import com.beacon.tracker.auth.DeviceAuthManager
import com.beacon.tracker.repository.FirebaseTrackerRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Date

class LocationTrackingService : Service() {

companion object {  
    private const val TAG = "LocationTrackingService"  
    const val ACTION_UPDATE_TRACKING_STATE = "com.beacon.tracker.ACTION_UPDATE_TRACKING_STATE"  
    const val EXTRA_TRACKING_PAUSED = "extra_tracking_paused"  
    const val EXTRA_DEVICE_AUTHORIZED = "extra_device_authorized"  
}  

private lateinit var fusedLocationClient: FusedLocationProviderClient  
private lateinit var deviceAuthManager: DeviceAuthManager  
private lateinit var repository: FirebaseTrackerRepository  
private lateinit var locationManager: LocationManager  

private val serviceJob = SupervisorJob()  
private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)  
private val handler = Handler(Looper.getMainLooper())  

private var trackingIntervalSeconds: Long = TrackingDefaults.DEFAULT_INTERVAL_SECONDS.toLong()  
private var locationAccuracy: String = "high"  
private var isTrackingPaused: Boolean = false  
private var isDeviceAuthorized: Boolean = true  
private var isRequestingLocation: Boolean = false  
private var isReceiversRegistered: Boolean = false  
private var isServiceDestroyed: Boolean = false  

private var locationRunnable: Runnable? = null  
private var pendingLocationTask: Job? = null  
private var currentLocationCallback: LocationCallback? = null  

private var lastBatteryLevel: Int = 0  
private var lastSignalStrength: Int = 0  

private val batteryReceiver = object : BroadcastReceiver() {  
    override fun onReceive(context: Context?, intent: Intent?) {  
        if (intent?.action == BatteryMonitorService.BATTERY_UPDATE_ACTION) {  
            lastBatteryLevel = intent.getIntExtra("battery_level", lastBatteryLevel)  
            Log.d(TAG, "Battery update received: $lastBatteryLevel%")  
        }  
    }  
}  

private val signalReceiver = object : BroadcastReceiver() {  
    override fun onReceive(context: Context?, intent: Intent?) {  
        if (intent?.action == SignalStrengthMonitorService.SIGNAL_UPDATE_ACTION) {  
            lastSignalStrength = intent.getIntExtra("signal_strength", lastSignalStrength)  
            Log.d(TAG, "Signal update received: $lastSignalStrength%")  
        }  
    }  
}  

private val stateUpdateReceiver = object : BroadcastReceiver() {  
    override fun onReceive(context: Context?, intent: Intent?) {  
        if (intent?.action != ACTION_UPDATE_TRACKING_STATE) return  
        isTrackingPaused = intent.getBooleanExtra(EXTRA_TRACKING_PAUSED, isTrackingPaused)  
        isDeviceAuthorized = intent.getBooleanExtra(EXTRA_DEVICE_AUTHORIZED, isDeviceAuthorized)  

        if (!isDeviceAuthorized) {  
            enterIdleState()  
        } else {  
            if (isTrackingPaused) {  
                stopLocationLoop()  
            } else {  
                startLocationLoop()  
            }  
        }  
    }  
}  

override fun onCreate() {  
    super.onCreate()  

    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)  
    deviceAuthManager = DeviceAuthManager(this)  
    locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager  
    repository = FirebaseTrackerRepository(  
        FirebaseFirestore.getInstance(),  
        FirebaseDatabase.getInstance(),  
        deviceAuthManager  
    )  

    createNotificationChannel()  
    startForeground(  
        NotificationDefaults.TRACKING_NOTIFICATION_ID,  
        createTrackingNotification()  
    )  

    registerReceiversSafely()  
    loadTrackingSettings()  
    startLocationLoop()  

    Log.d(TAG, "Service created")  
}  

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {  
    if (intent != null) {  
        isTrackingPaused = intent.getBooleanExtra(EXTRA_TRACKING_PAUSED, isTrackingPaused)  
        isDeviceAuthorized = intent.getBooleanExtra(EXTRA_DEVICE_AUTHORIZED, isDeviceAuthorized)  
    }  

    if (!isDeviceAuthorized) {  
        enterIdleState()  
    } else if (!isTrackingPaused) {  
        startLocationLoop()  
    }  

    return START_STICKY  
}  

private fun loadTrackingSettings() {  
    trackingIntervalSeconds = TrackingDefaults.DEFAULT_INTERVAL_SECONDS.toLong()  
    locationAccuracy = "high"  
}  

private fun startLocationLoop() {  
    if (isServiceDestroyed || isDeviceAuthorized.not() || isTrackingPaused) return  
    if (locationRunnable != null) return  

    locationRunnable = object : Runnable {  
        override fun run() {  
            if (isServiceDestroyed) return  

            if (!isDeviceAuthorized) {  
                enterIdleState()  
                return  
            }  

            if (isTrackingPaused) {  
                handler.postDelayed(this, trackingIntervalSeconds * 1000L)  
                return  
            }  

            requestSingleLocationUpdate()  
            handler.postDelayed(this, trackingIntervalSeconds * 1000L)  
        }  
    }  

    handler.post(locationRunnable!!)  
}  

private fun stopLocationLoop() {  
    locationRunnable?.let { handler.removeCallbacks(it) }  
    locationRunnable = null  
    clearPendingLocationTask()  
    removeLocationCallback()  
    Log.d(TAG, "Location tracking loop stopped")  
}  

private fun enterIdleState() {  
    isDeviceAuthorized = false  
    isTrackingPaused = false  
    stopLocationLoop()  
    Log.w(TAG, "Device unauthorized, entering idle state")  
}  

private fun requestSingleLocationUpdate() {  
    if (isRequestingLocation) return  
    if (!hasLocationPermission()) {  
        Log.w(TAG, "Location permission not granted")  
        return  
    }  

    isRequestingLocation = true  

    removeLocationCallback()  

    val priority = when (locationAccuracy.lowercase()) {  
        "high" -> Priority.PRIORITY_HIGH_ACCURACY  
        "medium" -> Priority.PRIORITY_BALANCED_POWER_ACCURACY  
        "low" -> Priority.PRIORITY_LOW_POWER  
        else -> Priority.PRIORITY_BALANCED_POWER_ACCURACY  
    }  

    val locationRequest = LocationRequest.Builder(  
        priority,  
        trackingIntervalSeconds * 1000L  
    )  
        .setMinUpdateIntervalMillis(1000L)  
        .setMaxUpdateDelayMillis(trackingIntervalSeconds * 1500L)  
        .setWaitForAccurateLocation(locationAccuracy.lowercase() == "high")  
        .build()  

    val callback = object : LocationCallback() {  
        override fun onLocationResult(result: LocationResult) {  
            super.onLocationResult(result)  
            val location = result.lastLocation  
            removeLocationCallback()  
            isRequestingLocation = false  

            if (location != null) {  
                handleLocationUpdate(location)  
            }  
        }  
    }  

    currentLocationCallback = callback  

    try {  
        fusedLocationClient.requestLocationUpdates(  
            locationRequest,  
            callback,  
            Looper.getMainLooper()  
        )  
    } catch (e: SecurityException) {  
        isRequestingLocation = false  
        Log.e(TAG, "SecurityException while requesting location updates", e)  
    } catch (e: Exception) {  
        isRequestingLocation = false  
        Log.e(TAG, "Failed to request location update", e)  
    }  
}  

private fun removeLocationCallback() {  
    currentLocationCallback?.let { callback ->  
        try {  
            fusedLocationClient.removeLocationUpdates(callback)  
        } catch (e: Exception) {  
            Log.w(TAG, "Failed to remove location callback", e)  
        }  
    }  
    currentLocationCallback = null  
    isRequestingLocation = false  
}  

private fun clearPendingLocationTask() {  
    pendingLocationTask?.cancel()  
    pendingLocationTask = null  
}  

private fun handleLocationUpdate(location: Location): Unit {  
    if (isServiceDestroyed) return  
    if (!isDeviceAuthorized) return  
    if (isTrackingPaused) return  

    val deviceId = deviceAuthManager.getDeviceId()  
    val batteryLevel = lastBatteryLevel  
    val signalStrength = lastSignalStrength  

    Log.d(
        TAG,
        "Location received: ${location.latitude}, ${location.longitude}, accuracy=${location.accuracy}m, battery=$batteryLevel%, signal=$signalStrength%"
    )  

    pendingLocationTask?.cancel()
    val job: Job = serviceScope.launch {  
        val locationData = BeaconLocation(  
            deviceId = deviceId,  
            timestamp = Date(location.time),  
            latitude = location.latitude,  
            longitude = location.longitude,  
            accuracy = location.accuracy,  
            provider = if (location.provider == LocationManager.GPS_PROVIDER) "gps" else "network",  
            speed = location.speed,  
            heading = location.bearing,  
            batteryLevel = batteryLevel,  
            signalStrength = signalStrength,  
            deviceMotionStatus = "idle"  
        )  

        try {  
            repository.uploadLocationToHistory(locationData)  
        } catch (e: Exception) {  
            Log.e(TAG, "Failed to upload location history", e)  
        }

        try {  
            repository.updateDeviceStatus(  
                latitude = location.latitude,  
                longitude = location.longitude,  
                accuracy = location.accuracy,  
                batteryLevel = batteryLevel,  
                signalStrength = signalStrength,  
                deviceMotionStatus = "idle"  
            )  
        } catch (e: Exception) {  
            Log.e(TAG, "Failed to update device status", e)  
        }

        try {  
            repository.updateLiveLocation(  
                latitude = location.latitude,  
                longitude = location.longitude,  
                accuracy = location.accuracy,  
                batteryLevel = batteryLevel,  
                signalStrength = signalStrength,  
                deviceMotionStatus = "idle"  
            )  
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update live location", e)
        }
    }
    pendingLocationTask = job
}

/**
 * Checks if the app has location permission
 */
private fun hasLocationPermission(): Boolean {
    // Check if app has fine location permission
    val fineGranted = ContextCompat.checkSelfPermission(  
        this,  
        android.Manifest.permission.ACCESS_FINE_LOCATION  
    ) == PackageManager.PERMISSION_GRANTED  

    // Check if app has coarse location permission
    val coarseGranted = ContextCompat.checkSelfPermission(  
        this,  
        android.Manifest.permission.ACCESS_COARSE_LOCATION  
    ) == PackageManager.PERMISSION_GRANTED  

    return fineGranted || coarseGranted  
}  

private fun registerReceiversSafely() {  
    if (isReceiversRegistered) return  

    try {  
        registerCompatReceiver(  
            batteryReceiver,  
            IntentFilter(BatteryMonitorService.BATTERY_UPDATE_ACTION)  
        )  
        registerCompatReceiver(  
            signalReceiver,  
            IntentFilter(SignalStrengthMonitorService.SIGNAL_UPDATE_ACTION)  
        )  
        registerCompatReceiver(  
            stateUpdateReceiver,  
            IntentFilter(ACTION_UPDATE_TRACKING_STATE)  
        )  
        isReceiversRegistered = true  
    } catch (e: Exception) {  
        Log.e(TAG, "Failed to register receivers", e)  
    }  
}  

private fun registerCompatReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {  
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)  
    } else {  
        @Suppress("DEPRECATION")  
        registerReceiver(receiver, filter)  
    }  
}  

private fun unregisterReceiversSafely() {  
    if (!isReceiversRegistered) return  

    try {  
        unregisterReceiver(batteryReceiver)  
    } catch (_: Exception) {  
    }  

    try {  
        unregisterReceiver(signalReceiver)  
    } catch (_: Exception) {  
    }  

    try {  
        unregisterReceiver(stateUpdateReceiver)  
    } catch (_: Exception) {  
    }  

    isReceiversRegistered = false  
}  

private fun createNotificationChannel() {  
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  
        val channel = NotificationChannel(  
            NotificationDefaults.TRACKING_NOTIFICATION_CHANNEL_ID,  
            NotificationDefaults.TRACKING_NOTIFICATION_CHANNEL_NAME,  
            NotificationManager.IMPORTANCE_LOW  
        ).apply {  
            description = "Beacon Tracker is running in the background"  
            setShowBadge(false)  
        }  

        val notificationManager = getSystemService(NotificationManager::class.java)  
        notificationManager?.createNotificationChannel(channel)  
    }  
}  

private fun createTrackingNotification(): Notification {  
    val intent = Intent(this, MainActivity::class.java)  
    val pendingIntent = PendingIntent.getActivity(  
        this,  
        0,  
        intent,  
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE  
    )  

    return NotificationCompat.Builder(this, NotificationDefaults.TRACKING_NOTIFICATION_CHANNEL_ID)  
        .setContentTitle("Beacon Tracker Running")  
        .setContentText("Location tracking is active")  
        .setSmallIcon(android.R.drawable.ic_dialog_map)  
        .setContentIntent(pendingIntent)  
        .setOngoing(true)  
        .setOnlyAlertOnce(true)  
        .setSilent(true)  
        .setPriority(NotificationCompat.PRIORITY_LOW)  
        .build()  
}  

override fun onDestroy() {  
    isServiceDestroyed = true  
    stopLocationLoop()  
    unregisterReceiversSafely()  
    serviceScope.cancel()  
    super.onDestroy()  
    Log.d(TAG, "Service destroyed")  
}  

override fun onBind(intent: Intent?): IBinder? = null

}