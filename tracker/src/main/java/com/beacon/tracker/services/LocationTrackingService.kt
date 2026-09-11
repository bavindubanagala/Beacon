package com.beacon.tracker.services

import android.annotation.SuppressLint
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
import com.beacon.shared.constants.FirestoreCollections
import com.beacon.shared.constants.NotificationDefaults
import com.beacon.shared.constants.TrackingDefaults
import com.beacon.shared.models.GeofenceZone
import com.beacon.shared.models.Location as BeaconLocation
import com.beacon.tracker.MainActivity
import com.beacon.tracker.auth.DeviceAuthManager
import com.beacon.tracker.db.OfflineBufferDao
import com.beacon.tracker.db.OfflineGeofenceEventEntity
import com.beacon.tracker.db.OfflineLocationEntity
import com.beacon.tracker.location.AdaptiveLocationPolicyManager
import com.beacon.tracker.location.GeofenceEvaluator
import com.beacon.tracker.location.LocationRequestConfig
import com.beacon.tracker.repository.FirebaseTrackerRepository
import com.beacon.tracker.sync.OfflineSyncManager
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
import com.beacon.tracker.worker.LocationWorker
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject

import android.content.pm.ServiceInfo
import com.beacon.tracker.service.TrackingNotificationHelper

@AndroidEntryPoint
class LocationTrackingService : Service() {

    private lateinit var notificationHelper: TrackingNotificationHelper

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
    @Inject lateinit var geofenceEvaluator: GeofenceEvaluator
    @Inject lateinit var policyManager: AdaptiveLocationPolicyManager
    @Inject lateinit var syncManager: OfflineSyncManager
    @Inject lateinit var bufferDao: OfflineBufferDao

    @Inject lateinit var locationEngine: com.beacon.tracker.location.LocationEngine
    @Inject lateinit var profileManager: com.beacon.tracker.location.TrackingProfileManager

    @Inject lateinit var locationSyncManager: com.beacon.tracker.sync.LocationSyncManager

    private lateinit var locationManager: LocationManager
    private lateinit var db: LocationDatabase

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private var trackingIntervalSeconds: Long = TrackingDefaults.DEFAULT_INTERVAL_SECONDS.toLong()
    private var locationAccuracy: String = "high"
    private var isTrackingPaused: Boolean = false
    private var isDeviceAuthorized: Boolean = true
    private var isRequestingLocation: Boolean = false
    private var isReceiversRegistered: Boolean = false
    private var isServiceDestroyed: Boolean = false
    
    private var activeGenerationId = 0

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

        notificationHelper = TrackingNotificationHelper(this)
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

        notificationHelper.createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationDefaults.TRACKING_NOTIFICATION_ID,
                notificationHelper.buildTrackingNotification(trackingMode),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(
                NotificationDefaults.TRACKING_NOTIFICATION_ID,
                notificationHelper.buildTrackingNotification(trackingMode)
            )
        }

        startLocationUpdates()

        registerReceiversSafely()
        
        val deviceId = deviceAuthManager.getDeviceId()
        pairingListener = firestore.collection("devices").document(deviceId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                val paired = snapshot?.getBoolean("is_paired") ?: false
                isDeviceAuthorized = paired
                
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
                    policyManager.setSosActive(true)
                    checkSosSmsFallback()
                } else {
                    sosStartTime = 0L
                    policyManager.setSosActive(false)
                }
                
                if (sensorManager == null) {
                    initMotionSensors()
                }

                if (paired && trackingMode != "off") {
                    startLocationLoop()
                    observeLocationPolicy()
                } else {
                    stopLocationLoop()
                }
            }

        fenceListener = firestore.collection(FirestoreCollections.GEOFENCES)
            .whereArrayContains("assignedDeviceIds", deviceId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error listening for geofences", e)
                    return@addSnapshotListener
                }
                val zones = snapshot?.documents?.mapNotNull { it.toObject(GeofenceZone::class.java)?.copy(id = it.id) } ?: emptyList()
                geofenceEvaluator.setFences(zones)
            }
    }

    private fun startLocationUpdates() {
        serviceScope.launch {
            locationEngine.getLocationUpdates(policyManager.locationConfigFlow)
                .collect { location ->
                    locationSyncManager.dispatchLocation(location, deviceAuthManager.getDeviceId())
                    updateTrackingNotification(location)
                }
        }
    }

    private fun updateTrackingNotification(location: android.location.Location) {
        val accuracy = location.accuracy
        val lat = String.format("%.5f", location.latitude)
        val lon = String.format("%.5f", location.longitude)
        
        val notification = notificationHelper.buildTrackingNotification(trackingMode).let {
            NotificationCompat.Builder(this, NotificationDefaults.TRACKING_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Beacon Tracking: $trackingMode")
                .setContentText("Lat: $lat, Lon: $lon (±${accuracy.toInt()}m)")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build()
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NotificationDefaults.TRACKING_NOTIFICATION_ID, notification)
    }

    private fun observeLocationPolicy() {
        serviceScope.launch {
            policyManager.locationConfigFlow.collect { config ->
                if (trackingMode == "live") {
                    reconfigureLocationUpdates(config)
                }
            }
        }
    }

    private suspend fun reconfigureLocationUpdates(config: LocationRequestConfig) {
        if (!isDeviceAuthorized || isTrackingPaused || isServiceDestroyed) return
        
        Log.d(TAG, "Reconfiguring Location Updates: $config")
        
        // Wait for removal to complete to ensure zero overlapping ticks
        currentLocationCallback?.let { 
            fusedLocationClient.removeLocationUpdates(it).await()
        }
        currentLocationCallback = null
        
        val generation = ++activeGenerationId
        val priority = config.priority
        val interval = config.intervalMillis
        
        val locationRequest = LocationRequest.Builder(priority, interval)
            .setMinUpdateIntervalMillis(interval / 2)
            .setMaxUpdateDelayMillis(interval * 2)
            .setMinUpdateDistanceMeters(config.minUpdateDistanceMeters)
            .build()

        val callback = object : LocationCallback() {
            private val myGeneration = generation

            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                if (myGeneration != activeGenerationId) {
                    Log.d(TAG, "Discarding stale location result (Gen $myGeneration vs Active $activeGenerationId)")
                    return
                }
                val location = result.lastLocation
                if (location != null) handleLocationUpdate(location)
            }
        }
        currentLocationCallback = callback
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper()).await()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during reconfigure", e)
        }
    }

    private var trackingMode: String = "interval"
    private var liveModeExpiryTime: Long = 0
    private var isEmergency: Boolean = false

    private fun processRemoteCommand(mode: String, interval: Int, autoRevert: Int, emergency: Boolean) {
        if (trackingMode == mode && trackingIntervalSeconds == interval.toLong() && isEmergency == emergency) return
        
        policyManager.setRemoteCommandMode(mode)

        if (!emergency && lastBatteryLevel < lowBatteryThreshold && mode == "live") {
            updateStatusInFirestore("interval", 1800, 0, false)
            return
        }

        val oldMode = trackingMode
        trackingMode = mode
        trackingIntervalSeconds = interval.toLong()
        isEmergency = emergency
        
        if (mode == "live") {
            liveModeExpiryTime = if (autoRevert > 0) System.currentTimeMillis() + (autoRevert * 1000L) else Long.MAX_VALUE
        }

        updateNotification()
        
        // Orchestrate transitions based on mode
        when (mode) {
            "interval" -> {
                // Schedule WorkManager for periodic updates
                scheduleIntervalTracking(interval)
                // Stop high-frequency loop and foreground service
                stopLocationLoop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.d(TAG, "Switched to INTERVAL mode: Service stopped, Worker scheduled")
            }
            "live" -> {
                // Cancel WorkManager and restart high-frequency foreground service
                cancelIntervalTracking()
                startLocationLoop()
                Log.d(TAG, "Switched to LIVE mode: Worker cancelled, Continuous tracking active")
            }
            "off" -> {
                cancelIntervalTracking()
                stopLocationLoop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.d(TAG, "Switched to OFF mode: All tracking terminated")
            }
        }

        updateStatusInFirestore(mode, interval, autoRevert, emergency)
    }

    private fun scheduleIntervalTracking(intervalSeconds: Int) {
        val workRequest = PeriodicWorkRequestBuilder<LocationWorker>(
            intervalSeconds.toLong().coerceAtLeast(15 * 60L), // Min 15 mins for periodic
            TimeUnit.SECONDS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "interval_tracking",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        Log.d(TAG, "Scheduled interval tracking every $intervalSeconds seconds")
    }

    private fun cancelIntervalTracking() {
        WorkManager.getInstance(this).cancelUniqueWork("interval_tracking")
        Log.d(TAG, "Cancelled interval tracking")
    }

    private fun updateStatusInFirestore(mode: String, interval: Int, autoRevert: Int, emergency: Boolean) {
        val deviceId = deviceAuthManager.getDeviceId()
        val updates = mapOf(
            "trackingMode" to mode,
            "intervalSeconds" to interval,
            "autoRevertSeconds" to autoRevert,
            "isEmergencyMode" to emergency,
            "commandMode" to null
        )
        firestore.collection("devices").document(deviceId)
            .set(updates, com.google.firebase.firestore.SetOptions.merge())
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            NotificationDefaults.TRACKING_NOTIFICATION_ID,
            notificationHelper.buildTrackingNotification(trackingMode)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_FORCE_UPDATE -> {
                    Log.d(TAG, "Force update requested via startService")
                    requestSingleLocationUpdate()
                }
                ACTION_UPDATE_TRACKING_STATE -> {
                    isTrackingPaused = intent.getBooleanExtra(EXTRA_TRACKING_PAUSED, isTrackingPaused)
                    isDeviceAuthorized = intent.getBooleanExtra(EXTRA_DEVICE_AUTHORIZED, isDeviceAuthorized)
                    
                    val remoteMode = intent.getStringExtra("trackingMode")
                    if (remoteMode != null) {
                        Log.d(TAG, "Mode change requested via FCM: $remoteMode")
                        // Map the remote mode to our internal command logic
                        // In a real app, you'd parse interval/emergency here too if payload provided them
                        processRemoteCommand(
                            mode = remoteMode.lowercase(),
                            interval = 900, // Fallback defaults
                            autoRevert = 1800,
                            emergency = false
                        )
                    }
                }
            }
            
            // Legacy handling for non-action based updates if any
            if (intent.action == null) {
                isTrackingPaused = intent.getBooleanExtra(EXTRA_TRACKING_PAUSED, isTrackingPaused)
                isDeviceAuthorized = intent.getBooleanExtra(EXTRA_DEVICE_AUTHORIZED, isDeviceAuthorized)
            }
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
                    policyManager.setStationary(isResting)
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

    @SuppressLint("MissingPermission")
    private fun requestSingleLocationUpdate() {
        if (isRequestingLocation || !hasLocationPermission()) return
        isRequestingLocation = true
        removeLocationCallback()

        // Use high-priority one-shot fix for FCM/Manual pings
        val currentTask = fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            null
        )

        currentTask.addOnSuccessListener { location: Location? ->
            isRequestingLocation = false
            if (location != null) {
                handleLocationUpdate(location)
            } else {
                // Fallback to standard request if one-shot fails
                requestStandardLocationUpdate()
            }
        }.addOnFailureListener {
            isRequestingLocation = false
            requestStandardLocationUpdate()
        }
    }

    private fun requestStandardLocationUpdate() {
        if (isRequestingLocation || !hasLocationPermission()) return
        isRequestingLocation = true

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
            val isNearFence = geofenceEvaluator.processLocationUpdate(location, deviceId)
            policyManager.setNearGeofence(isNearFence)

            if (syncManager.isOnline.value) {
                val locationData = BeaconLocation(
                    deviceId = deviceId, timestamp = System.currentTimeMillis(), latitude = location.latitude,
                    longitude = location.longitude, accuracy = location.accuracy, provider = location.provider ?: "gps",
                    speed = location.speed, heading = location.bearing, batteryLevel = batteryLevel,
                    signalStrength = lastSignalStrength, deviceMotionStatus = if (isResting) "resting" else "moving"
                )

                try {
                    repository.updateDeviceStatus(
                        location.latitude,
                        location.longitude,
                        location.accuracy,
                        batteryLevel,
                        lastSignalStrength,
                        if (isResting) "resting" else "moving"
                    )
                    repository.uploadLocationToHistory(locationData)
                    repository.updateLiveLocation(
                        location.latitude,
                        location.longitude,
                        location.accuracy,
                        batteryLevel,
                        lastSignalStrength,
                        if (isResting) "resting" else "moving"
                    )
                } catch (e: Exception) {
                    bufferLocationLocally(location, deviceId, batteryLevel)
                }
            } else {
                bufferLocationLocally(location, deviceId, batteryLevel)
            }
        }
    }

    private suspend fun bufferLocationLocally(location: Location, deviceId: String, batteryLevel: Int) {
        val entity = OfflineLocationEntity(
            deviceId = deviceId,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            speed = location.speed,
            bearing = location.bearing,
            accuracy = location.accuracy,
            batteryLevel = batteryLevel,
            timestamp = System.currentTimeMillis()
        )
        bufferDao.insertLocation(entity)
        Log.d(TAG, "Location buffered locally (Offline)")
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

    private fun createNotificationChannel() {}

    private fun createTrackingNotification(): Notification {
        return notificationHelper.buildTrackingNotification(trackingMode)
    }

    override fun onDestroy() {
        isServiceDestroyed = true
        pairingListener?.remove()
        fenceListener?.remove()
        policyManager.cleanup()
        syncManager.cleanup()
        stopLocationLoop()
        unregisterReceiversSafely()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
