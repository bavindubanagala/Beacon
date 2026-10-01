package com.beacon.tracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.beacon.tracker.R
import com.beacon.tracker.worker.ServiceWatchdogWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit
import com.beacon.shared.models.GeofenceZone
import com.beacon.tracker.auth.DeviceAuthManager
import com.beacon.tracker.data.LocationEntity
import com.beacon.tracker.data.TrackingConfig
import com.beacon.tracker.data.TrackingMode
import com.beacon.tracker.geofence.TrackerGeofenceManager
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

    private val geofenceManager by lazy { TrackerGeofenceManager(applicationContext) }
    private var deviceZonesListener: ListenerRegistration? = null
    private var groupZonesListener: ListenerRegistration? = null
    private var deviceZones: List<GeofenceZone> = emptyList()
    private var groupZones: List<GeofenceZone> = emptyList()
    private var currentGroupId: String? = null

    private var lastFixLat: Double? = null
    private var lastFixLng: Double? = null
    private var lastFixTime: Long = 0L
    private var lastFixAccuracy: Float = 0f
    private val fixLock = Any()

    private fun applyMergedZones() {
        geofenceManager.applyZones((deviceZones + groupZones).distinctBy { it.id })
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        ServiceWatchdogWorker.schedule(applicationContext)
        ensureAnonymousAuth()
        startDeviceListener()
        trackingPaused = servicePrefs.getBoolean("tracking_paused", false)
        startTrackingLoop()
    }

    private fun startDeviceListener() {
        val deviceAuthManager = DeviceAuthManager(applicationContext)
        val deviceId = deviceAuthManager.getDeviceId()
        if (deviceId.isBlank()) return

        deviceZonesListener = firestore.collection("geofences")
            .whereArrayContains("assignedDeviceIds", deviceId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("TrackerService", "Device zones listener error", error)
                    return@addSnapshotListener
                }
                deviceZones = snapshot?.documents?.mapNotNull { it.toObject(GeofenceZone::class.java)?.copy(id = it.id) } ?: emptyList()
                applyMergedZones()
            }

        deviceListenerRegistration = firestore.collection("devices").document(deviceId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("TrackerService", "Device listener error", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val groupId = snapshot.getString("groupId")?.takeIf { it.isNotBlank() }
                    if (groupId != currentGroupId) {
                        currentGroupId = groupId
                        groupZonesListener?.remove()
                        groupZonesListener = null
                        groupZones = emptyList()
                        if (groupId != null) {
                            groupZonesListener = firestore.collection("geofences")
                                .whereArrayContains("assignedGroupIds", groupId)
                                .addSnapshotListener { groupSnap, groupErr ->
                                    if (groupErr != null) {
                                        Log.e("TrackerService", "Group zones listener error", groupErr)
                                        return@addSnapshotListener
                                    }
                                    groupZones = groupSnap?.documents?.mapNotNull { it.toObject(GeofenceZone::class.java)?.copy(id = it.id) } ?: emptyList()
                                    applyMergedZones()
                                }
                        } else {
                            applyMergedZones()
                        }
                    }
                    val pingTimestamp = snapshot.getLong("forceSyncRequestedAt")
                        ?: snapshot.getLong("pingRequestedAt")
                        ?: 0L
                    if (pingTimestamp > 0 && pingTimestamp > lastProcessedPingTimestamp) {
                        lastProcessedPingTimestamp = pingTimestamp
                        triggerImmediateSync(force = true)
                    }

                    val modeString = snapshot.getString("trackingMode")
                    val scheduledInterval = (snapshot.getLong("scheduledIntervalMillis")
                        ?: TrackingConfig().scheduledIntervalMillis)
                        .coerceIn(TrackingConfig.MIN_SCHEDULED_INTERVAL_MILLIS, TrackingConfig.MAX_SCHEDULED_INTERVAL_MILLIS)
                    val liveInterval = (snapshot.getLong("liveIntervalMillis")
                        ?: TrackingConfig().liveIntervalMillis)
                        .coerceIn(TrackingConfig.MIN_LIVE_INTERVAL_MILLIS, TrackingConfig.MAX_LIVE_INTERVAL_MILLIS)

                    val rawLiveRevert = if (snapshot.contains("liveRevertAfterMillis")) {
                        snapshot.getLong("liveRevertAfterMillis") ?: 1_800_000L
                    } else {
                        1_800_000L
                    }
                    val liveRevertAfter = if (rawLiveRevert < 0L) 0L else rawLiveRevert

                    val parsedRevertMode = TrackingMode.fromString(snapshot.getString("revertToMode"))
                    val revertMode = if (parsedRevertMode == TrackingMode.LIVE) TrackingMode.SCHEDULED else parsedRevertMode

                    val trackingChangedAt = snapshot.getLong("trackingChangedAt") ?: 0L

                    val updatedConfig = TrackingConfig(
                        mode = TrackingMode.fromString(modeString),
                        scheduledIntervalMillis = scheduledInterval,
                        liveIntervalMillis = liveInterval,
                        liveRevertAfterMillis = liveRevertAfter,
                        revertToMode = revertMode,
                        trackingChangedAt = trackingChangedAt
                    )

                    updateLiveDeadline(updatedConfig)

                    val previousConfig = trackingConfigFlow.value
                    trackingConfigFlow.value = updatedConfig

                    if (updatedConfig != previousConfig) {
                        startTrackingLoop()
                        updateForegroundNotification(updatedConfig.mode)
                    }
                }
            }
    }

    private fun updateLiveDeadline(config: TrackingConfig) {
        if (config.mode != TrackingMode.LIVE || config.liveRevertAfterMillis <= 0L) {
            servicePrefs.edit().putLong("live_deadline_at", 0L).apply()
            return
        }

        val storedSeenChangedAt = servicePrefs.getLong("live_seen_changed_at", -1L)
        val storedDeadlineAt = servicePrefs.getLong("live_deadline_at", 0L)

        if (config.trackingChangedAt != storedSeenChangedAt || storedDeadlineAt == 0L) {
            val newDeadline = System.currentTimeMillis() + config.liveRevertAfterMillis
            servicePrefs.edit()
                .putLong("live_deadline_at", newDeadline)
                .putLong("live_seen_changed_at", config.trackingChangedAt)
                .apply()
        }
    }

    private fun revertFromLive(config: TrackingConfig) {
        servicePrefs.edit().putLong("live_deadline_at", 0L).apply()

        val deviceAuthManager = DeviceAuthManager(applicationContext)
        val deviceId = deviceAuthManager.getDeviceId()
        if (deviceId.isBlank()) return

        Log.d("TrackerService", "Live timer ended, reverting mode to ${config.revertToMode.name}")

        val now = System.currentTimeMillis()
        val deviceRef = firestore.collection("devices").document(deviceId)
        val batch = firestore.batch()
        batch.update(
            deviceRef,
            mapOf(
                "trackingMode" to config.revertToMode.name,
                "trackingChangedAt" to now,
                "revertToMode" to ""
            )
        )
        batch.set(
            deviceRef.collection("mode_history").document(),
            mapOf(
                "mode" to config.revertToMode.name,
                "intervalMillis" to if (config.revertToMode == TrackingMode.SCHEDULED) config.scheduledIntervalMillis else 0L,
                "changedBy" to "Auto-revert",
                "timestamp" to now
            )
        )
        batch.commit()
            .addOnFailureListener { e ->
                Log.e("TrackerService", "Failed to revert from live", e)
            }
    }

    private fun capDelayWithDeadline(config: TrackingConfig, baseDelayMillis: Long): Long {
        if (config.mode == TrackingMode.LIVE) {
            val deadline = servicePrefs.getLong("live_deadline_at", 0L)
            if (deadline > 0L) {
                val remaining = deadline - System.currentTimeMillis()
                val capped = maxOf(1000L, remaining)
                return minOf(baseDelayMillis, capped)
            }
        }
        return baseDelayMillis
    }

    private fun startTrackingLoop() {
        trackingLoopJob?.cancel()
        trackingLoopJob = serviceScope.launch {
            delay(3000)
            while (isActive) {
                val config = trackingConfigFlow.value

                if (config.mode == TrackingMode.LIVE) {
                    val deadline = servicePrefs.getLong("live_deadline_at", 0L)
                    if (deadline > 0L && System.currentTimeMillis() >= deadline) {
                        revertFromLive(config)
                        delay(5000)
                        continue
                    }
                }

                val activeMode = if (trackingPaused) TrackingMode.ONLINE else config.mode
                when (activeMode) {
                    TrackingMode.SCHEDULED -> {
                        triggerImmediateSync(priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                        delay(capDelayWithDeadline(config, config.scheduledIntervalMillis))
                    }
                    TrackingMode.LIVE -> {
                        triggerImmediateSync(priority = Priority.PRIORITY_HIGH_ACCURACY)
                        delay(capDelayWithDeadline(config, config.liveIntervalMillis))
                    }
                    TrackingMode.ONLINE -> {
                        sendHeartbeat()
                        delay(capDelayWithDeadline(config, config.onlineHeartbeatIntervalMillis))
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

    private fun triggerImmediateSync(priority: Int = Priority.PRIORITY_BALANCED_POWER_ACCURACY, force: Boolean = false) {
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

                locationSyncManager.processLocationUpdate(uid, entity, force)
                try {
                    checkTripwires(deviceId, location)
                } catch (e: Exception) {
                    Log.e("TrackerTripwire", "Error checking tripwires", e)
                }
            } catch (e: Exception) {
                Log.e("TrackerService", "Error during immediate remote sync", e)
            }
        }
    }

    private suspend fun checkTripwires(deviceId: String, location: Location) {
        val p0Lat: Double?
        val p0Lng: Double?
        val p0Time: Long
        val p0Acc: Float

        synchronized(fixLock) {
            p0Lat = lastFixLat
            p0Lng = lastFixLng
            p0Time = lastFixTime
            p0Acc = lastFixAccuracy

            lastFixLat = location.latitude
            lastFixLng = location.longitude
            lastFixTime = location.time
            lastFixAccuracy = location.accuracy
        }

        if (p0Lat == null || p0Lng == null || p0Time <= 0L) return
        if (location.time <= p0Time) return

        if (location.time - p0Time > 600_000L) return
        if (location.accuracy > 50f || p0Acc > 50f) return

        val results = FloatArray(1)
        Location.distanceBetween(p0Lat, p0Lng, location.latitude, location.longitude, results)
        if (results[0] < 10f) return

        val tripwires = TrackerGeofenceManager(applicationContext).loadTripwires()
        if (tripwires.isEmpty()) return

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        val todayNumber = ((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1

        for (tripwire in tripwires) {
            if (tripwire.activeUntil > 0L && now > tripwire.activeUntil) continue
            if (tripwire.activeDaysOfWeek.isNotEmpty() && todayNumber !in tripwire.activeDaysOfWeek) continue

            val aLat = tripwire.aLat
            val aLng = tripwire.aLng
            val bLat = tripwire.bLat
            val bLng = tripwire.bLng

            val cosLat = Math.cos(Math.toRadians(aLat))
            val scaleX = 111320.0 * cosLat
            val scaleY = 110540.0

            val bx = (bLng - aLng) * scaleX
            val by = (bLat - aLat) * scaleY

            val p0x = (p0Lng - aLng) * scaleX
            val p0y = (p0Lat - aLat) * scaleY

            val p1x = (location.longitude - aLng) * scaleX
            val p1y = (location.latitude - aLat) * scaleY

            val d1 = bx * p0y - by * p0x
            val d2 = bx * p1y - by * p1x

            if (!((d1 > 0.0 && d2 < 0.0) || (d1 < 0.0 && d2 > 0.0))) continue

            val denom = d1 - d2
            if (denom == 0.0) continue
            val t = d1 / denom

            val xX = p0x + t * (p1x - p0x)
            val yX = p0y + t * (p1y - p0y)

            val dotABAB = bx * bx + by * by
            if (dotABAB == 0.0) continue

            val dotXAB = xX * bx + yX * by
            val u = dotXAB / dotABAB

            if (u !in 0.0..1.0) continue

            val crossingType = if (d1 > 0.0 && d2 < 0.0) "A_TO_B" else "B_TO_A"
            val dir = tripwire.direction.trim().uppercase()
            if (dir != "BOTH" && dir != crossingType) continue

            val prefs = applicationContext.getSharedPreferences(TrackerGeofenceManager.PREFS_NAME, Context.MODE_PRIVATE)
            val cooldownKey = "tripwire_last_${tripwire.id}"
            val lastEventTime = prefs.getLong(cooldownKey, 0L)
            if (now - lastEventTime < 60_000L) continue

            val manager = TrackerGeofenceManager(applicationContext)
            if (manager.shouldSendEvent(tripwire.id, tripwire.alertFrequency, now)) {
                prefs.edit().putLong(cooldownKey, now).apply()
                locationSyncManager.processGeofenceEvent(
                    deviceId = deviceId,
                    geofenceId = tripwire.id,
                    geofenceName = tripwire.name,
                    eventType = crossingType,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = now
                )
                Log.d("TrackerTripwire", "Tripwire crossing detected: ${tripwire.name} ($crossingType)")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_FORCE_UPDATE -> {
                    triggerImmediateSync(force = true)
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
        deviceZonesListener?.remove()
        groupZonesListener?.remove()
        serviceScope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        WorkManager.getInstance(applicationContext).enqueue(OneTimeWorkRequestBuilder<ServiceWatchdogWorker>().setInitialDelay(3, TimeUnit.SECONDS).build())
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
