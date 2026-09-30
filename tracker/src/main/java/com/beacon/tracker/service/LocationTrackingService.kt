package com.beacon.tracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.beacon.tracker.R
import com.beacon.tracker.auth.DeviceAuthManager
import com.beacon.tracker.data.LocationEntity
import com.beacon.tracker.data.TrackingConfig
import com.beacon.tracker.data.TrackingMode
import com.beacon.tracker.sync.LocationSyncManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject
    lateinit var locationSyncManager: LocationSyncManager

    @Inject
    lateinit var fusedLocationClient: FusedLocationProviderClient

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var trackingPaused: Boolean = false
    private val servicePrefs by lazy { getSharedPreferences("beacon_tracker_service", MODE_PRIVATE) }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var deviceListenerRegistration: ListenerRegistration? = null
    private var lastProcessedPingTimestamp: Long = 0L

    private val trackingConfigFlow = MutableStateFlow(TrackingConfig())
    private var trackingLoopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        ensureAnonymousAuth()
        startDeviceListener()
        trackingPaused = servicePrefs.getBoolean("tracking_paused", false)
        startTrackingLoop()
    }

    private fun startDeviceListener() {
        val deviceAuthManager = DeviceAuthManager(applicationContext)
        val deviceId = deviceAuthManager.getDeviceId()
        if (deviceId.isBlank()) return

        deviceListenerRegistration = firestore.collection("devices").document(deviceId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("TrackerService", "Device listener error", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val pingTimestamp = snapshot.getLong("forceSyncRequestedAt")
                        ?: snapshot.getLong("pingRequestedAt")
                        ?: 0L
                    if (pingTimestamp > 0 && pingTimestamp > lastProcessedPingTimestamp) {
                        lastProcessedPingTimestamp = pingTimestamp
                        triggerImmediateSync()
                    }

                    val modeString = snapshot.getString("trackingMode")
                    val scheduledInterval = (snapshot.getLong("scheduledIntervalMillis")
                        ?: TrackingConfig().scheduledIntervalMillis)
                        .coerceIn(TrackingConfig.MIN_SCHEDULED_INTERVAL_MILLIS, TrackingConfig.MAX_SCHEDULED_INTERVAL_MILLIS)
                    val liveInterval = (snapshot.getLong("liveIntervalMillis")
                        ?: TrackingConfig().liveIntervalMillis)
                        .coerceIn(TrackingConfig.MIN_LIVE_INTERVAL_MILLIS, TrackingConfig.MAX_LIVE_INTERVAL_MILLIS)

                    val updatedConfig = TrackingConfig(
                        mode = TrackingMode.fromString(modeString),
                        scheduledIntervalMillis = scheduledInterval,
                        liveIntervalMillis = liveInterval
                    )

                    val previousConfig = trackingConfigFlow.value
                    trackingConfigFlow.value = updatedConfig

                    if (updatedConfig != previousConfig) {
                        startTrackingLoop()
                        updateForegroundNotification(updatedConfig.mode)
                    }
                }
            }
    }

    private fun startTrackingLoop() {
        trackingLoopJob?.cancel()
        trackingLoopJob = serviceScope.launch {
            while (isActive) {
                val config = trackingConfigFlow.value
                val activeMode = if (trackingPaused) TrackingMode.ONLINE else config.mode
                when (activeMode) {
                    TrackingMode.SCHEDULED -> {
                        triggerImmediateSync(priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                        delay(config.scheduledIntervalMillis)
                    }
                    TrackingMode.LIVE -> {
                        triggerImmediateSync(priority = Priority.PRIORITY_HIGH_ACCURACY)
                        delay(config.liveIntervalMillis)
                    }
                    TrackingMode.ONLINE -> {
                        sendHeartbeat()
                        delay(config.onlineHeartbeatIntervalMillis)
                    }
                }
            }
        }
    }

    private fun sendHeartbeat() {
        val deviceAuthManager = DeviceAuthManager(applicationContext)
        val deviceId = deviceAuthManager.getDeviceId()
        if (deviceId.isBlank()) return

        val updates = mapOf(
            "lastSeenTimestamp" to System.currentTimeMillis()
        )
        firestore.collection("devices").document(deviceId)
            .update(updates)
            .addOnFailureListener { e ->
                Log.e("TrackerService", "Failed to send heartbeat", e)
            }
    }

    private fun triggerImmediateSync(priority: Int = Priority.PRIORITY_BALANCED_POWER_ACCURACY) {
        val cancellationSource = com.google.android.gms.tasks.CancellationTokenSource()
        serviceScope.launch {
            try {
                val location: android.location.Location? = kotlinx.coroutines.withTimeoutOrNull(7000L) {
                    fusedLocationClient.getCurrentLocation(
                        priority,
                        cancellationSource.token
                    ).await()
                }

                if (location == null) {
                    cancellationSource.cancel()
                    return@launch
                }

                val uid = ensureAuthenticatedAsync()
                if (uid.isNullOrBlank()) return@launch

                val deviceAuthManager = DeviceAuthManager(applicationContext)
                val deviceId = deviceAuthManager.getDeviceId()

                val entity = LocationEntity(
                    userId = uid,
                    deviceId = deviceId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = location.time,
                    speed = location.speed,
                    accuracy = location.accuracy,
                    batteryLevel = getBatteryLevel(),
                    signalStrength = 0,
                    isSynced = false
                )

                locationSyncManager.processLocationUpdate(uid, entity)
            } catch (e: Exception) {
                Log.e("TrackerService", "Error during immediate remote sync", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_FORCE_UPDATE -> {
                    triggerImmediateSync()
                }
                ACTION_UPDATE_TRACKING_STATE -> {
                    trackingPaused = intent.getBooleanExtra(EXTRA_TRACKING_PAUSED, false)
                    servicePrefs.edit().putBoolean("tracking_paused", trackingPaused).apply()
                    startTrackingLoop()
                }
            }
        }
        return START_STICKY
    }

    private fun ensureAnonymousAuth() {
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    Log.d("TrackerService", "Anonymous auth succeeded: ${result.user?.uid}")
                }
                .addOnFailureListener { e ->
                    Log.e("TrackerService", "Anonymous auth failed", e)
                }
        }
    }

    private suspend fun ensureAuthenticatedAsync(): String? {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            return currentUser.uid
        }
        return try {
            val result = auth.signInAnonymously().await()
            result.user?.uid
        } catch (e: Exception) {
            Log.e("TrackerService", "Failed to sign in anonymously", e)
            null
        }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = getSystemService(BATTERY_SERVICE) as? android.os.BatteryManager
            batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        } catch (e: Exception) {
            0
        }
    }


    private fun updateForegroundNotification(mode: TrackingMode) {
        val channelId = "location_tracking_channel"
        val contentText = when (mode) {
            TrackingMode.SCHEDULED -> "Checking in periodically..."
            TrackingMode.LIVE -> "Live tracking active..."
            TrackingMode.ONLINE -> "Online — not actively tracking"
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Beacon Location Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(1001, notification)
    }

    private fun startForegroundServiceNotification() {
        val channelId = "location_tracking_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Beacon Location Active")
            .setContentText("Tracking location updates...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        deviceListenerRegistration?.remove()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_UPDATE_TRACKING_STATE = "com.beacon.tracker.ACTION_UPDATE_TRACKING_STATE"
        const val ACTION_FORCE_UPDATE = "com.beacon.tracker.ACTION_FORCE_UPDATE"
        const val ACTION_STATUS_UPDATE = "com.beacon.tracker.ACTION_STATUS_UPDATE"
        const val EXTRA_STATUS_MESSAGE = "extra_status_message"
        const val EXTRA_TRACKING_PAUSED = "extra_tracking_paused"
        const val EXTRA_DEVICE_AUTHORIZED = "extra_device_authorized"
    }
}
