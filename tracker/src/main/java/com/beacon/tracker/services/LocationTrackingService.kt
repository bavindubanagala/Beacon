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

    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled tracking service coroutine failure", throwable)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + serviceExceptionHandler)

    private var trackingIntervalSeconds: Long = TrackingDefaults.DEFAULT_INTERVAL_SECONDS.toLong()
    private var locationAccuracy: String = "high"
    private var isTrackingPaused: Boolean = false
    private var isDeviceAuthorized: Boolean = true
    private var isRequestingLocation: Boolean = false
    private var isReceiversRegistered: Boolean = false
    private var isServiceDestroyed: Boolean = false
    
    private var activeGenerationId = 0
    private var lastRemoteCommandKey: String? = null
    private var lastRemoteCommandAt: Long = 0L

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
                            // No-op: locationEngine flow respects paused state
                        } else {
                            // No-op: locationEngine flow respects paused state
                        }
                    }
                }
                ACTION_FORCE_UPDATE -> {
                    Log.d(TAG, "Force update requested via receiver")
                    requestForceLocationUpdate()
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
                
                val cmdMode = (snapshot?.getString("command_mode") ?: snapshot?.getString("commandMode"))
                    ?.trim()?.lowercase()
                val cmdInterval = snapshot?.getLong("interval_seconds") ?: snapshot?.getLong("intervalSeconds") ?: 900L
                val cmdAutoRevert = snapshot?.getLong("auto_revert_seconds") ?: snapshot?.getLong("autoRevertSeconds") ?: 1800L
                val cmdEmergency = snapshot?.getBoolean("is_emergency_mode") ?: snapshot?.getBoolean("isEmergencyMode") ?: false
                
                isBatterySavingEnabled = snapshot?.getBoolean("battery_saving_enabled") ?: snapshot?.getBoolean("batterySavingEnabled") ?: true
                stationaryIntervalMinutes = (snapshot?.getLong("stationary_interval_minutes") ?: snapshot?.getLong("stationaryIntervalMinutes") ?: 45L).toInt()
                
                val alertThresholds = snapshot?.get("alertThresholds") as? Map<String, Any>
                lowBatteryThreshold = (alertThresholds?.get("lowBatteryPercent") as? Long)?.toInt() ?: 15
                speedLimitKmH = (alertThresholds?.get("speedLimitKmH") as? Long)?.toInt() ?: 0
                sosFallbackPhone = snapshot?.getString("sosFallbackPhone") ?: ""
                
                if (cmdMode != null && cmdMode in setOf("live", "interval", "off") &&
                    cmdInterval in 15..86_400 && cmdAutoRevert in 0..86_400
                ) {
                    val commandKey = "$cmdMode:$cmdInterval:$cmdAutoRevert:$cmdEmergency"
                    val commandTimestamp = snapshot?.getLong("command_timestamp")
                        ?: snapshot?.getLong("commandTimestamp") ?: 0L
                    val duplicate = commandKey == lastRemoteCommandKey &&
                        (commandTimestamp == 0L || commandTimestamp <= lastRemoteCommandAt)
                    if (duplicate) {
                        Log.d(TAG, "Ignoring duplicate remote command")
                    } else {
                        lastRemoteCommandKey = commandKey
                        lastRemoteCommandAt = commandTimestamp
                        processRemoteCommand(cmdMode, cmdInterval.toInt(), cmdAutoRevert.toInt(), cmdEmergency)
                    }
                } else if (cmdMode != null) {
                    Log.w(TAG, "Ignoring malformed remote command")
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
                    // Location updates are handled by locationEngine flow
                } else {
                    enterIdleState()
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
        serviceScope.launch(SupervisorJob() + serviceExceptionHandler) {
            try {
                locationEngine.getLocationUpdates(policyManager.locationConfigFlow)
                    .collect { location ->
                        if (isServiceDestroyed || !isDeviceAuthorized || isTrackingPaused) return@collect
                        locationSyncManager.dispatchLocation(location, deviceAuthManager.getDeviceId())
                        updateTrackingNotification(location)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Location updates collection failed", e)
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

    private var trackingMode: String = "interval"
    private var liveModeExpiryTime: Long = 0
    private var isEmergency: Boolean = false

    private fun processRemoteCommand(mode: String, interval: Int, autoRevert: Int, emergency: Boolean) {
        if (mode !in setOf("live", "interval", "off") ||
            interval !in 15..86_400 || autoRevert !in 0..86_400
        ) {
            Log.w(TAG, "Ignoring invalid command parameters")
            return
        }
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
                // Stop foreground service
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.d(TAG, "Switched to INTERVAL mode: Service stopped, Worker scheduled")
            }
            "live" -> {
                // Cancel WorkManager and keep foreground service running
                cancelIntervalTracking()
                Log.d(TAG, "Switched to LIVE mode: Worker cancelled, Continuous tracking active")
            }
            "off" -> {
                cancelIntervalTracking()
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
                    requestForceLocationUpdate()
                }
                ACTION_UPDATE_TRACKING_STATE -> {
                    isTrackingPaused = intent.getBooleanExtra(EXTRA_TRACKING_PAUSED, isTrackingPaused)
                    isDeviceAuthorized = intent.getBooleanExtra(EXTRA_DEVICE_AUTHORIZED, isDeviceAuthorized)
                    
                    val remoteMode = intent.getStringExtra("trackingMode")
                    if (remoteMode != null) {
                        Log.d(TAG, "Mode change requested via FCM: $remoteMode")
                        processRemoteCommand(
                            mode = remoteMode.lowercase(),
                            interval = 900,
                            autoRevert = 1800,
                            emergency = false
                        )
                    }
                }
            }
            
            if (intent.action == null) {
                isTrackingPaused = intent.getBooleanExtra(EXTRA_TRACKING_PAUSED, isTrackingPaused)
                isDeviceAuthorized = intent.getBooleanExtra(EXTRA_DEVICE_AUTHORIZED, isDeviceAuthorized)
            }
        }

        if (!isDeviceAuthorized) enterIdleState()
        return START_STICKY
    }

    private fun enterIdleState() {
        isDeviceAuthorized = false
        isTrackingPaused = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun requestForceLocationUpdate() {
        if (isRequestingLocation || !hasLocationPermission()) return
        isRequestingLocation = true

        val currentTask = fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            null
        )

        currentTask.addOnSuccessListener { location: Location? ->
            isRequestingLocation = false
            if (location != null) {
                handleLocationUpdate(location)
            } else {
                Log.w(TAG, "Force location update returned null")
            }
        }.addOnFailureListener {
            isRequestingLocation = false
            Log.e(TAG, "Force location update failed", it)
        }
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
        unregisterReceiversSafely()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
