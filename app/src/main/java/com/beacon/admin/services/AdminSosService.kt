package com.beacon.admin.services

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.beacon.admin.MainActivity
import com.beacon.admin.data.auth.AuthSessionCleanupRegistry
import com.beacon.admin.notifications.GeofenceNotificationManager
import com.beacon.shared.models.GeofenceEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration

class AdminSosService : Service() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var sosListenerRegistration: ListenerRegistration? = null
    private var geofenceListenerRegistration: ListenerRegistration? = null
    private val geofencePrefs by lazy { getSharedPreferences("beacon_admin_geofence_alerts",
        MODE_PRIVATE
    ) }
    private val deviceInfoCache = mutableMapOf<String, Pair<Boolean, String>>()
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var unregisterCleanup: (() -> Unit)? = null
    private val notifiedDeviceIds = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        setupAuthStateListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceNotification()
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "sos_watch_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SOS Monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Beacon SOS monitoring")
            .setContentText("Watching for SOS alerts from your devices")
            .setSmallIcon(R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                2001,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(2001, notification)
        }
    }

    private fun setupAuthStateListener() {
        if (authStateListener != null) return
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val currentUser = firebaseAuth.currentUser
            if (currentUser != null) {
                if (sosListenerRegistration == null) {
                    startMonitoring(currentUser)
                }
            } else {
                stopMonitoring()
            }
        }
        auth.addAuthStateListener(authStateListener!!)
        auth.currentUser?.let { user ->
            if (sosListenerRegistration == null) {
                startMonitoring(user)
            }
        }
    }

    private fun startMonitoring(currentUser: FirebaseUser) {
        if (sosListenerRegistration != null) {
            Log.d(TAG, "SOS monitoring is already active.")
            return
        }

        Log.d(TAG, "Starting SOS monitoring for authenticated user: ${currentUser.uid}")

        sosListenerRegistration = db.collection("devices")
            .whereEqualTo("ownerId", currentUser.uid)
            .whereEqualTo("isEmergencyMode", true)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore error during SOS monitoring", error)
                    
                    if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                        error.code == FirebaseFirestoreException.Code.UNAUTHENTICATED
                    ) {
                        Log.e(TAG, "Terminal security exception encountered. Removing listener.")
                        stopMonitoring()
                    }
                    return@addSnapshotListener
                }

                val currentSnapshotIds = mutableSetOf<String>()
                if (snapshots != null) {
                    for (doc in snapshots.documents) {
                        val deviceId = doc.id
                        currentSnapshotIds.add(deviceId)

                        if (!notifiedDeviceIds.contains(deviceId)) {
                            notifiedDeviceIds.add(deviceId)
                            showSosNotification(doc)
                        }
                    }
                }

                notifiedDeviceIds.retainAll(currentSnapshotIds)
            }
        unregisterCleanup = AuthSessionCleanupRegistry.register { stopMonitoring() }
        startGeofenceMonitoring(currentUser)
    }

    private fun startGeofenceMonitoring(currentUser: FirebaseUser) {
        if (geofenceListenerRegistration != null) return

        var lastTs = geofencePrefs.getLong("last_event_ts", 0L)
        if (lastTs == 0L) {
            lastTs = System.currentTimeMillis()
            geofencePrefs.edit().putLong("last_event_ts", lastTs).apply()
        }

        geofenceListenerRegistration = db.collection("geofence_events")
            .whereGreaterThan("timestamp", lastTs)
            .orderBy("timestamp")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore error during geofence events monitoring", error)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    for (change in snapshots.documentChanges) {
                        if (change.type == DocumentChange.Type.ADDED) {
                            val event = change.document.toObject(GeofenceEvent::class.java)?.copy(id = change.document.id)
                            if (event != null) {
                                handleGeofenceEvent(currentUser.uid, event)
                            }
                        }
                    }
                }
            }
    }

    private fun handleGeofenceEvent(uid: String, event: GeofenceEvent) {
        val currentLastTs = geofencePrefs.getLong("last_event_ts", 0L)
        if (event.timestamp > currentLastTs) {
            geofencePrefs.edit().putLong("last_event_ts", event.timestamp).apply()
        }

        if (deviceInfoCache.containsKey(event.deviceId)) {
            val (owned, name) = deviceInfoCache[event.deviceId]!!
            if (owned) {
                GeofenceNotificationManager(applicationContext).sendGeofenceAlert(event, name)
            }
        } else {
            db.collection("devices").document(event.deviceId).get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        val ownerId = doc.getString("ownerId") ?: doc.getString("owner_id") ?: ""
                        val owned = (ownerId == uid)
                        val name = doc.getString("deviceName") ?: doc.getString("device_name") ?: "A device"
                        deviceInfoCache[event.deviceId] = Pair(owned, name)
                        if (owned) {
                            GeofenceNotificationManager(applicationContext).sendGeofenceAlert(event, name)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to fetch device info for geofence event", e)
                }
        }
    }

    private fun showSosNotification(doc: DocumentSnapshot) {
        val deviceId = doc.id
        val deviceName = doc.getString("deviceName") ?: doc.getString("device_name") ?: "A device"
        val message = "$deviceName needs help"

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            deviceId.hashCode(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "sos_notification_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_dialog_alert)
            .setContentTitle("SOS Emergency")
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Emergency SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(deviceId.hashCode(), notificationBuilder.build())
    }

    private fun stopMonitoring() {
        sosListenerRegistration?.remove()
        sosListenerRegistration = null
        geofenceListenerRegistration?.remove()
        geofenceListenerRegistration = null
        notifiedDeviceIds.clear()
        deviceInfoCache.clear()
        unregisterCleanup?.invoke()
        unregisterCleanup = null
        Log.d(TAG, "SOS monitoring listener removed.")
    }

    override fun onDestroy() {
        authStateListener?.let { auth.removeAuthStateListener(it) }
        authStateListener = null
        stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AdminSosService"
    }
}
