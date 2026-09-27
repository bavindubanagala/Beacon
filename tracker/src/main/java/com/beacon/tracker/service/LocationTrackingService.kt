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
import com.beacon.tracker.sync.LocationSyncManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private lateinit var locationCallback: LocationCallback
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var deviceListenerRegistration: ListenerRegistration? = null
    private var lastProcessedPingTimestamp: Long = 0L

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        ensureAnonymousAuth()
        setupLocationCallback()
        requestLocationUpdates()
        startRemotePingListener()
    }

    private fun startRemotePingListener() {
        val deviceAuthManager = DeviceAuthManager(applicationContext)
        val deviceId = deviceAuthManager.getDeviceId()
        if (deviceId.isBlank()) return

        deviceListenerRegistration = firestore.collection("devices").document(deviceId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("TrackerService", "Remote ping listener error", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val pingTimestamp = snapshot.getLong("forceSyncRequestedAt")
                        ?: snapshot.getLong("pingRequestedAt")
                        ?: 0L

                    Log.d("TrackerService", "Snapshot received, pingTimestamp=$pingTimestamp, lastProcessed=$lastProcessedPingTimestamp")

                    if (pingTimestamp > 0 && pingTimestamp > lastProcessedPingTimestamp) {
                        Log.d("TrackerService", "New ping detected, triggering immediate sync")
                        lastProcessedPingTimestamp = pingTimestamp
                        triggerImmediateSync()
                    }
                }
            }
    }

    private fun triggerImmediateSync() {
        val cancellationSource = com.google.android.gms.tasks.CancellationTokenSource()
        serviceScope.launch {
            try {
                Log.d("TrackerService", "triggerImmediateSync started, requesting current location")
                val location: android.location.Location? = kotlinx.coroutines.withTimeoutOrNull(7000L) {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cancellationSource.token
                    ).await()
                }

                if (location == null) {
                    Log.w("TrackerService", "triggerImmediateSync: location was null after 7 second timeout, aborting")
                    cancellationSource.cancel()
                    return@launch
                }

                Log.d("TrackerService", "triggerImmediateSync: got location lat=${location.latitude} lng=${location.longitude}")

                val uid = ensureAuthenticatedAsync()
                if (uid.isNullOrBlank()) {
                    Log.w("TrackerService", "triggerImmediateSync: uid is null or blank, aborting")
                    return@launch
                }

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

                Log.d("TrackerService", "triggerImmediateSync: calling processLocationUpdate")
                locationSyncManager.processLocationUpdate(uid, entity)
                Log.d("TrackerService", "triggerImmediateSync: processLocationUpdate returned")
            } catch (e: SecurityException) {
                Log.e("TrackerService", "Location permission missing for immediate sync", e)
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
                    val paused = intent.getBooleanExtra(EXTRA_TRACKING_PAUSED, false)
                    if (paused) {
                        fusedLocationClient.removeLocationUpdates(locationCallback)
                    } else {
                        requestLocationUpdates()
                    }
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

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return

                serviceScope.launch {
                    val uid = ensureAuthenticatedAsync()
                    if (uid.isNullOrBlank()) {
                        Log.w("TrackerService", "Skipping Firestore sync: User not authenticated")
                        return@launch
                    }

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
                }
            }
        }
    }

    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            5000L
        ).setMinUpdateIntervalMillis(2000L).build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        } catch (e: SecurityException) {
            // Missing location permissions fallback
        }
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
        fusedLocationClient.removeLocationUpdates(locationCallback)
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
