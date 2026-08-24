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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

import android.os.BatteryManager
import android.telephony.SmsManager
import androidx.work.*
import com.beacon.tracker.database.LocationDatabase
import com.beacon.tracker.database.PendingLocation
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
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
    
    @Inject lateinit var repository: FirebaseTrackerRepository
    @Inject lateinit var firestore: FirebaseFirestore
    @Inject lateinit var database: FirebaseDatabase

    private lateinit var locationManager: LocationManager
    private lateinit var db: LocationDatabase

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
    private var speedLimitKmH: Int = 0
    private var sosFallbackPhone: String = ""
    private var sosStartTime: Long = 0
    private var isSmsSent: Boolean = false
    
    private var isBatterySavingEnabled: Boolean = true
    private var stationaryIntervalMinutes: Int = 45
    private var isResting: Boolean = false
    private var lastMotionTime: Long = System.currentTimeMillis()
    private var sensorManager: android.hardware.SensorManager? = null
    private var significantMotionSensor: android.hardware.Sensor? = null
    private var significantMotionTriggerListener: android.hardware.TriggerEventListener? = null
    
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
        db = LocationDatabase.getDatabase(this)
        
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            serviceScope.launch {
                try {
                    auth.signInAnonymously().await()
                } catch (e: Exception) {
                    Log.e(TAG, "Anonymous auth failed in Service", e)
                }
            }
        }

        createNotificationChannel()
        startForeground(
            NotificationDefaults.TRACKING_NOTIFICATION_ID,
            createTrackingNotification()
        )

        registerReceiversSafely()
        
        val deviceId = deviceAuthManager.getDeviceId()
        pairingListener = firestore.collection("devices").document(deviceId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                val paired = snapshot?.getBoolean("is_paired") ?: false
                isDeviceAuthorized = paired
                
                val remoteInsideIds = snapshot?.get("insideFenceIds") as? List<String>
                if (remoteInsideIds != null) {
                    insideFenceIds.clear()
                    insideFenceIds.addAll(remoteInsideIds)
                }
                
                val cmdMode = snapshot?.getString("command_mode") ?: snapshot?.getString("commandMode")
                val cmdInterval = snapshot?.getLong("interval_seconds") ?: snapshot?.getLong("intervalSeconds") ?: 900L
                val cmdAutoRevert = snapshot?.getLong("auto_revert_seconds") ?: snapshot?.getLong("autoRevertSeconds") ?: 1800L
                val cmdEmergency = snapshot?.getBoolean("is_emergency_mode") ?: snapshot?.getBoolean("isEmergencyMode") ?: false
                
                isBatterySavingEnabled = snapshot?.getBoolean("battery_saving_enabled") ?: snapshot?.getBoolean("batterySavingEnabled") ?: true
                stationaryIntervalMinutes = (snapshot?.getLong("stationary_interval_minutes") ?: snapshot?.getLong("stationaryIntervalMinutes") ?: 45L).toInt()
                
                val alertThresholds = snapshot?.get("alertThresholds") as? Map<String, Any>
                lowBatteryThreshold = (alertThresholds?.get("lowBatteryPercent") as? Long)?.toInt() ?: 15
                speedLimitKmH = (alertThresholds?.get("speedLimitKmH") as? Long)?.toInt() ?: 0
                sosFallbackPhone = snapshot?.getString("sosFallbackPhone") ?: ""
                
                if (cmdMode != null) {
                    processRemoteCommand(cmdMode, cmdInterval.toInt(), cmdAutoRevert.toInt(), cmdEmergency)
                }

                if (isEmergency) {
                    if (sosStartTime == 0L) {
                        sosStartTime = System.currentTimeMillis()
                        isSmsSent = false
                    }
                    checkSosSmsFallback()
                } else {
                    sosStartTime = 0L
                }
                
                if (sensorManager == null) {
                    initMotionSensors()
                }

                if (paired && trackingMode != "off") {
                    startLocationLoop()
                } else {
                    stopLocationLoop()
                }
            }

        fenceListener = firestore.collection("fences")
            .whereArrayContains("assignedDeviceIds", deviceId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                assignedFences = snapshot?.documents?.mapNotNull { it.toObject(com.beacon.shared.models.Fence::class.java)?.copy(id = it.id) } ?: emptyList()
            }
    }

    private var trackingMode: String = "interval"
    private var liveModeExpiryTime: Long = 0
    private var isEmergency: Boolean = false

    private fun processRemoteCommand(mode: String, interval: Int, autoRevert: Int, emergency: Boolean) {
        if (trackingMode == mode && trackingIntervalSeconds == interval.toLong() && isEmergency == emergency) return
        
        if (!emergency && lastBatteryLevel < lowBatteryThreshold && mode == "live") {
            updateStatusInFirestore("interval", 1800, 0, false)
            return
        }

        trackingMode = mode
        trackingIntervalSeconds = interval.toLong()
        isEmergency = emergency
        
        if (mode == "live") {
            liveModeExpiryTime = if (autoRevert > 0) System.currentTimeMillis() + (autoRevert * 1000L) else Long.MAX_VALUE
        }

        updateNotification()
        stopLocationLoop()
        if (mode != "off") startLocationLoop()
        updateStatusInFirestore(mode, interval, autoRevert, emergency)
    }

    private fun updateStatusInFirestore(mode: String, interval: Int, autoRevert: Int, emergency: Boolean) {
        val deviceId = deviceAuthManager.getDeviceId()
        val updates = mapOf(
            "tracking_mode" to mode, "trackingMode" to mode,
            "interval_seconds" to interval, "intervalSeconds" to interval,
            "auto_revert_seconds" to autoRevert, "autoRevertSeconds" to autoRevert,
            "is_emergency_mode" to emergency, "isEmergencyMode" to emergency,
            "command_mode" to null, "commandMode" to null
        )
        firestore.collection("devices").document(deviceId)
            .set(updates, com.google.firebase.firestore.SetOptions.merge())
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NotificationDefaults.TRACKING_NOTIFICATION_ID, createTrackingNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            isTrackingPaused = intent.getBooleanExtra(EXTRA_TRACKING_PAUSED, isTrackingPaused)
            isDeviceAuthorized = intent.getBooleanExtra(EXTRA_DEVICE_AUTHORIZED, isDeviceAuthorized)
        }
        if (!isDeviceAuthorized) enterIdleState() else if (!isTrackingPaused) startLocationLoop()
        return START_STICKY
    }

    private fun startLocationLoop() {
        if (isServiceDestroyed || !isDeviceAuthorized || isTrackingPaused) return
        if (locationRunnable != null) return

        locationRunnable = object : Runnable {
            override fun run() {
                if (isServiceDestroyed) return
                if (!isDeviceAuthorized) { enterIdleState(); return }
                if (isTrackingPaused) { handler.postDelayed(this, trackingIntervalSeconds * 1000L); return }
                if (trackingMode == "live" && System.currentTimeMillis() > liveModeExpiryTime) {
                    processRemoteCommand("interval", 900, 1800, false)
                    return
                }
                if (isBatterySavingEnabled && trackingMode == "interval" && !isEmergency) {
                    val timeSinceMotion = System.currentTimeMillis() - lastMotionTime
                    isResting = timeSinceMotion > (10 * 60 * 1000L)
                }
                val effectiveIntervalSeconds = if (isBatterySavingEnabled && trackingMode == "interval" && !isEmergency && isResting) {
                    stationaryIntervalMinutes * 60L
                } else {
                    trackingIntervalSeconds
                }
                requestSingleLocationUpdate()
                handler.postDelayed(this, effectiveIntervalSeconds * 1000L)
            }
        }
        handler.post(locationRunnable!!)
    }

    private fun stopLocationLoop() {
        locationRunnable?.let { handler.removeCallbacks(it) }
        locationRunnable = null
        removeLocationCallback()
    }

    private fun enterIdleState() {
        isDeviceAuthorized = false
        isTrackingPaused = false
        stopLocationLoop()
    }

    private fun requestSingleLocationUpdate() {
        if (isRequestingLocation || !hasLocationPermission()) return
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
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val location = result.lastLocation
                removeLocationCallback()
                if (location != null) handleLocationUpdate(location) else sendStatusUpdate("GPS Failed: No Signal")
            }
        }
        currentLocationCallback = callback
        try { fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper()) }
        catch (e: SecurityException) { isRequestingLocation = false }
    }

    private fun removeLocationCallback() {
        currentLocationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
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
        lastLat = location.latitude
        lastLon = location.longitude
        val batteryLevel = (getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        lastBatteryLevel = batteryLevel
        
        serviceScope.launch {
            val locationData = BeaconLocation(
                deviceId = deviceId, timestamp = System.currentTimeMillis(), latitude = location.latitude,
                longitude = location.longitude, accuracy = location.accuracy, provider = location.provider ?: "gps",
                speed = location.speed, heading = location.bearing, batteryLevel = batteryLevel,
                signalStrength = lastSignalStrength, deviceMotionStatus = if (isResting) "resting" else "moving"
            )

            try {
                val doc = firestore.collection("devices").document(deviceId).get().await()
                val currentName = doc.getString("deviceName") ?: "Tracker Device"
                val deviceMap = mapOf("status" to "online", "batteryLevel" to batteryLevel, "last_seen" to System.currentTimeMillis())
                firestore.collection("devices").document(deviceId).set(deviceMap, com.google.firebase.firestore.SetOptions.merge()).await()
                repository.uploadLocationToHistory(locationData)
                repository.updateLiveLocation(location.latitude, location.longitude, location.accuracy, batteryLevel, lastSignalStrength, if (isResting) "resting" else "moving")
            } catch (e: Exception) {
                db.locationDao().insert(PendingLocation(deviceId = deviceId, timestamp = locationData.timestamp, latitude = locationData.latitude, longitude = locationData.longitude, accuracy = locationData.accuracy, provider = locationData.provider, speed = locationData.speed, heading = locationData.heading, batteryLevel = locationData.batteryLevel, signalStrength = locationData.signalStrength, deviceMotionStatus = locationData.deviceMotionStatus))
                scheduleSync()
            }
        }
    }

    private fun scheduleSync() {
        WorkManager.getInstance(this).enqueue(com.beacon.tracker.services.SyncWorker.createSyncWorkRequest())
    }

    private fun checkSosSmsFallback() {
        if (isEmergency && !isSmsSent && sosStartTime > 0 && sosFallbackPhone.isNotEmpty()) {
            if (System.currentTimeMillis() - sosStartTime > 2 * 60 * 1000L) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) getSystemService(SmsManager::class.java) else SmsManager.getDefault()
                    smsManager.sendTextMessage(sosFallbackPhone, null, "BEACON SOS: Emergency active.", null, null)
                    isSmsSent = true
                }
            }
        }
    }
    
    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0

    private fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun registerReceiversSafely() {
        if (isReceiversRegistered) return
        val filter = IntentFilter().apply { addAction(ACTION_UPDATE_TRACKING_STATE); addAction(ACTION_FORCE_UPDATE) }
        ContextCompat.registerReceiver(this, stateUpdateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        isReceiversRegistered = true
    }

    private fun unregisterReceiversSafely() { if (isReceiversRegistered) { unregisterReceiver(stateUpdateReceiver); isReceiversRegistered = false } }

    private fun initMotionSensors() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) return
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        significantMotionSensor = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_SIGNIFICANT_MOTION)
        significantMotionTriggerListener = object : android.hardware.TriggerEventListener() {
            override fun onTrigger(event: android.hardware.TriggerEvent?) {
                isResting = false
                lastMotionTime = System.currentTimeMillis()
                significantMotionSensor?.let { sensorManager?.requestTriggerSensor(this, it) }
            }
        }
        significantMotionSensor?.let { sensorManager?.requestTriggerSensor(significantMotionTriggerListener, it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NotificationDefaults.TRACKING_NOTIFICATION_CHANNEL_ID, NotificationDefaults.TRACKING_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createTrackingNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NotificationDefaults.TRACKING_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Beacon Tracker Running").setContentText("Mode: $trackingMode").setSmallIcon(android.R.drawable.ic_dialog_map).setContentIntent(pendingIntent).setOngoing(true).build()
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
