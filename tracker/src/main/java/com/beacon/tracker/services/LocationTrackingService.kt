package com.beacon.tracker.services

import android.app.*
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
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class LocationTrackingService : Service() {

    companion object {
        private const val TAG = "LocationTrackingService"
        const val ACTION_UPDATE_TRACKING_STATE = "com.beacon.tracker.ACTION_UPDATE_TRACKING_STATE"
        const val ACTION_FORCE_UPDATE = "com.beacon.tracker.ACTION_FORCE_UPDATE"
        const val ACTION_STATUS_UPDATE = "com.beacon.tracker.ACTION_STATUS_UPDATE"
        const val EXTRA_STATUS_MESSAGE = "extra_status_message"
        const val EXTRA_TRACKING_PAUSED = "extra_tracking_paused"
        const val EXTRA_DEVICE_AUTHORIZED = "extra_device_authorized"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var deviceAuthManager: DeviceAuthManager
    private lateinit var repository: FirebaseTrackerRepository
    private lateinit var locationManager: LocationManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private var trackingIntervalSeconds: Long = TrackingDefaults.DEFAULT_INTERVAL_SECONDS.toLong()
    private var locationAccuracy: String = "high"
    private var isTrackingPaused: Boolean = false
    private var isDeviceAuthorized: Boolean = true
    private var isRequestingLocation: Boolean = false
    private var isReceiversRegistered: Boolean = false
    private var isServiceDestroyed: Boolean = false

    private var locationRunnable: Runnable? = null
    private var currentLocationCallback: LocationCallback? = null

    private var lastBatteryLevel: Int = 0
    private var lastSignalStrength: Int = 0
    private var lowBatteryThreshold: Int = 15
    
    private var assignedFences: List<com.beacon.shared.models.Fence> = emptyList()
    private var insideFenceIds = mutableSetOf<String>()
    private var fenceListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                lastBatteryLevel = (level * 100 / scale.toFloat()).toInt()
                Log.d(TAG, "Battery update received: $lastBatteryLevel%")
            }
        }
    }

    private val stateUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_UPDATE_TRACKING_STATE -> {
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
                ACTION_FORCE_UPDATE -> {
                    Log.d(TAG, "Force update requested via receiver")
                    requestSingleLocationUpdate()
                }
            }
        }
    }

    private var pairingListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        deviceAuthManager = DeviceAuthManager(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val firestore = FirebaseFirestore.getInstance()
        repository = FirebaseTrackerRepository(
            firestore,
            FirebaseDatabase.getInstance("https://gen-lang-client-0281237877-default-rtdb.asia-southeast1.firebasedatabase.app/"),
            deviceAuthManager
        )

        createNotificationChannel()
        startForeground(
            NotificationDefaults.TRACKING_NOTIFICATION_ID,
            createTrackingNotification()
        )

        registerReceiversSafely()
        
        // Use a live listener for pairing state and tracking mode commands
        val deviceId = deviceAuthManager.getDeviceId()
        pairingListener = firestore.collection("devices").document(deviceId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                val paired = snapshot?.getBoolean("is_paired") ?: false
                isDeviceAuthorized = paired
                
                // Sync insideFenceIds from Firestore
                val remoteInsideIds = snapshot?.get("insideFenceIds") as? List<String>
                if (remoteInsideIds != null) {
                    insideFenceIds.clear()
                    insideFenceIds.addAll(remoteInsideIds)
                }
                
                // Handle Remote Commands (Priority 2)
                val cmdMode = snapshot?.getString("command_mode") ?: snapshot?.getString("commandMode")
                val cmdInterval = snapshot?.getLong("interval_seconds") ?: snapshot?.getLong("intervalSeconds") ?: 900L
                val cmdAutoRevert = snapshot?.getLong("auto_revert_seconds") ?: snapshot?.getLong("autoRevertSeconds") ?: 1800L
                val cmdEmergency = snapshot?.getBoolean("is_emergency_mode") ?: snapshot?.getBoolean("isEmergencyMode") ?: false
                
                // Update alert settings from device document
                val alertThresholds = snapshot?.get("alertThresholds") as? Map<String, Any>
                lowBatteryThreshold = (alertThresholds?.get("lowBatteryPercent") as? Long)?.toInt() ?: 15
                
                if (cmdMode != null) {
                    processRemoteCommand(cmdMode, cmdInterval.toInt(), cmdAutoRevert.toInt(), cmdEmergency)
                }

                if (paired && trackingMode != "off") {
                    startLocationLoop()
                } else {
                    stopLocationLoop()
                }
            }

        // Listen for assigned Geofences
        fenceListener = firestore.collection("fences")
            .whereArrayContains("assignedDeviceIds", deviceId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                assignedFences = snapshot?.documents?.mapNotNull { it.toObject(com.beacon.shared.models.Fence::class.java)?.copy(id = it.id) } ?: emptyList()
                Log.d(TAG, "Synced ${assignedFences.size} assigned fences")
            }

        Log.d(TAG, "Service created")
    }

    private var trackingMode: String = "interval"
    private var liveModeExpiryTime: Long = 0
    private var isEmergency: Boolean = false

    private fun processRemoteCommand(mode: String, interval: Int, autoRevert: Int, emergency: Boolean) {
        if (trackingMode == mode && trackingIntervalSeconds == interval.toLong() && isEmergency == emergency) return
        
        Log.d(TAG, "Processing remote command: mode=$mode, interval=$interval, autoRevert=$autoRevert, emergency=$emergency")
        
        // Battery Safety Net (Only if NOT emergency mode)
        if (!emergency && lastBatteryLevel < lowBatteryThreshold && mode == "live") {
            Log.w(TAG, "Battery low (<$lowBatteryThreshold%) and not emergency, ignoring Live mode command")
            updateStatusInFirestore("interval", 1800, 0, false) // Force safer mode
            return
        }

        trackingMode = mode
        trackingIntervalSeconds = interval.toLong()
        isEmergency = emergency
        
        if (mode == "live") {
            if (autoRevert > 0) {
                liveModeExpiryTime = System.currentTimeMillis() + (autoRevert * 1000L)
            } else {
                liveModeExpiryTime = Long.MAX_VALUE // Auto-revert disabled
            }
        }

        updateNotification()
        
        // Restart loop with new interval
        stopLocationLoop()
        if (mode != "off") {
            startLocationLoop()
        }
        
        // Acknowledge command in Firestore
        updateStatusInFirestore(mode, interval, autoRevert, emergency)
    }

    private fun updateStatusInFirestore(mode: String, interval: Int, autoRevert: Int, emergency: Boolean) {
        val deviceId = deviceAuthManager.getDeviceId()
        val updates = mapOf(
            "tracking_mode" to mode,
            "trackingMode" to mode,
            "interval_seconds" to interval,
            "intervalSeconds" to interval,
            "auto_revert_seconds" to autoRevert,
            "autoRevertSeconds" to autoRevert,
            "is_emergency_mode" to emergency,
            "isEmergencyMode" to emergency,
            "command_mode" to null, // Clear the command once processed
            "commandMode" to null
        )
        FirebaseFirestore.getInstance().collection("devices").document(deviceId)
            .update(updates)
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            NotificationDefaults.TRACKING_NOTIFICATION_ID,
            createTrackingNotification()
        )
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

    private fun startLocationLoop() {
        if (isServiceDestroyed || !isDeviceAuthorized || isTrackingPaused) return
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

                // Check for Live Mode auto-revert
                if (trackingMode == "live" && System.currentTimeMillis() > liveModeExpiryTime) {
                    Log.d(TAG, "Live mode expired, reverting to interval mode")
                    processRemoteCommand("interval", 900, 1800, false)
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

        val locationRequest = LocationRequest.Builder(priority, 10000L)
            .setMinUpdateIntervalMillis(5000L)
            .setMaxUpdateDelayMillis(15000L)
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
                } else {
                    isRequestingLocation = false
                    sendStatusUpdate("GPS Failed: No Signal")
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

    private fun sendStatusUpdate(message: String) {
        val intent = Intent(ACTION_STATUS_UPDATE)
        intent.putExtra(EXTRA_STATUS_MESSAGE, message)
        sendBroadcast(intent)
    }

    private fun handleLocationUpdate(location: Location) {
        if (isServiceDestroyed || !isDeviceAuthorized || isTrackingPaused) return

        val deviceId = deviceAuthManager.getDeviceId()
        val batteryLevel = lastBatteryLevel
        val signalStrength = lastSignalStrength
        
        // Low Battery Check
        if (batteryLevel < lowBatteryThreshold) {
            triggerAlert("LOW_BATTERY", "Battery level is ${batteryLevel}% (Threshold: ${lowBatteryThreshold}%)", "WARNING", mapOf("battery_level" to batteryLevel.toDouble()))
        }
        
        // Geofence Check
        assignedFences.forEach { fence ->
            val results = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, fence.centerLat, fence.centerLng, results)
            val isInsideNow = results[0] < fence.radiusMeters
            val wasInside = insideFenceIds.contains(fence.id)
            
            if (isInsideNow && !wasInside) {
                // Enter / Checkpoint Crossing
                insideFenceIds.add(fence.id)
                updateInsideFencesInFirestore()
                
                if (fence.type == "zone") {
                    if (fence.alertOnEnter) {
                        triggerAlert("GEOFENCE_ENTER", "Entered zone: ${fence.name}", "INFO", emptyMap())
                    }
                } else {
                    // Checkpoint logic
                    // TODO: Implement alertFrequency logic (every_time, once_ever, once_per_day)
                    triggerAlert("CHECKPOINT_CROSSED", "Crossed checkpoint: ${fence.name}", "INFO", emptyMap())
                }
            } else if (!isInsideNow && wasInside) {
                // Exit
                insideFenceIds.remove(fence.id)
                updateInsideFencesInFirestore()
                
                if (fence.type == "zone" && fence.alertOnExit) {
                    triggerAlert("GEOFENCE_EXIT", "Exited zone: ${fence.name}", "INFO", emptyMap())
                }
            }
        }

        Log.d(TAG, "Location received: ${location.latitude}, ${location.longitude}, accuracy=${location.accuracy}m for device: $deviceId")

        serviceScope.launch {
            val locationData = BeaconLocation(
                deviceId = deviceId,
                timestamp = System.currentTimeMillis(),
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                provider = location.provider ?: "gps",
                speed = location.speed,
                heading = location.bearing,
                batteryLevel = batteryLevel,
                signalStrength = signalStrength,
                deviceMotionStatus = "idle"
            )

            try {
                // Check if device document exists and has a name before overwriting
                val doc = FirebaseFirestore.getInstance().collection("devices")
                    .document(deviceId).get().await()
                
                val currentName = doc.getString("deviceName") ?: doc.getString("device_name")
                val finalName = if (currentName.isNullOrEmpty() || currentName == "Tracker Device") {
                    "Tracker Device"
                } else {
                    currentName
                }

                val deviceMap = mapOf(
                    "deviceId" to deviceId,
                    "device_id" to deviceId,
                    "deviceName" to finalName,
                    "device_name" to finalName,
                    "status" to "online",
                    "last_seen" to System.currentTimeMillis()
                )
                FirebaseFirestore.getInstance().collection("devices")
                    .document(deviceId)
                    .set(deviceMap, com.google.firebase.firestore.SetOptions.merge())
                    .await()

                repository.uploadLocationToHistory(locationData)
                repository.updateDeviceStatus(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    batteryLevel = batteryLevel,
                    signalStrength = signalStrength,
                    deviceMotionStatus = "idle"
                )
                repository.updateLiveLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    batteryLevel = batteryLevel,
                    signalStrength = signalStrength,
                    deviceMotionStatus = "idle"
                )
                sendStatusUpdate("Update Success! ✅")
                Log.d(TAG, "Successfully updated Firestore and Realtime DB")
            } catch (e: Exception) {
                sendStatusUpdate("Upload Failed: ${e.message}")
                Log.e(TAG, "Failed to upload location update", e)
            }
        }
    }

    private fun updateInsideFencesInFirestore() {
        val deviceId = deviceAuthManager.getDeviceId()
        FirebaseFirestore.getInstance().collection("devices").document(deviceId)
            .update("insideFenceIds", insideFenceIds.toList())
    }

    private fun triggerAlert(type: String, message: String, severity: String, data: Map<String, Double>) {
        serviceScope.launch {
            val alert = com.beacon.shared.models.Alert(
                id = java.util.UUID.randomUUID().toString(),
                alert_type = type,
                device_id = deviceAuthManager.getDeviceId(),
                device_name = "Tracker Device",
                alert_severity = severity,
                message = message,
                created_at = System.currentTimeMillis()
            )
            try {
                FirebaseFirestore.getInstance()
                    .collection("devices")
                    .document(deviceAuthManager.getDeviceId())
                    .collection("alerts")
                    .document(alert.id)
                    .set(alert)
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to trigger alert", e)
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun registerReceiversSafely() {
        if (isReceiversRegistered) return
        try {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val filter = IntentFilter().apply {
                addAction(ACTION_UPDATE_TRACKING_STATE)
                addAction(ACTION_FORCE_UPDATE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(stateUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(stateUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(stateUpdateReceiver, filter)
            }
            }
            isReceiversRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register receivers", e)
        }
    }

    private fun unregisterReceiversSafely() {
        if (!isReceiversRegistered) return
        try {
            unregisterReceiver(batteryReceiver)
            unregisterReceiver(stateUpdateReceiver)
        } catch (e: Exception) {}
        isReceiversRegistered = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NotificationDefaults.TRACKING_NOTIFICATION_CHANNEL_ID,
                NotificationDefaults.TRACKING_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createTrackingNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val modeText = when(trackingMode) {
            "live" -> if (isEmergency) "Mode: Emergency Live" else "Mode: Live (High Frequency)"
            "interval" -> {
                val h = trackingIntervalSeconds / 3600
                val m = (trackingIntervalSeconds % 3600) / 60
                val s = trackingIntervalSeconds % 60
                val timeStr = if (h > 0) "${h}h ${m}m ${s}s" else if (m > 0) "${m}m ${s}s" else "${s}s"
                "Mode: Interval ($timeStr)"
            }
            else -> "Mode: Tracking Off"
        }

        return NotificationCompat.Builder(this, NotificationDefaults.TRACKING_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Beacon Tracker Running")
            .setContentText(modeText)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isServiceDestroyed = true
        pairingListener?.remove()
        fenceListener?.remove()
        stopLocationLoop()
        unregisterReceiversSafely()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
