package com.beacon.tracker.services

import android.content.Intent
import android.util.Log
import com.beacon.tracker.auth.DeviceAuthManager
import com.beacon.tracker.service.LocationTrackingService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BeaconFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var deviceAuthManager: DeviceAuthManager
    @Inject lateinit var firestore: FirebaseFirestore

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastCommandId: String? = null
    private var lastCommandAt: Long = 0L

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d("FCMTracker", "Message received: ${remoteMessage.data}")
        
        val action = remoteMessage.data["action"]
        val trackingMode = remoteMessage.data["trackingMode"]?.trim()?.lowercase()
        val commandId = remoteMessage.data["commandId"] ?: remoteMessage.messageId
        val commandTimestamp = remoteMessage.data["command_timestamp"]?.toLongOrNull()
            ?: remoteMessage.data["commandTimestamp"]?.toLongOrNull() ?: 0L

        if (commandId != null && commandId == lastCommandId &&
            (commandTimestamp == 0L || commandTimestamp <= lastCommandAt)
        ) {
            Log.d("FCMTracker", "Ignoring duplicate command: $commandId")
            return
        }
        if (commandId != null) {
            lastCommandId = commandId
            lastCommandAt = commandTimestamp
        }
        
        when (action) {
            "PING_REQUEST" -> {
                Log.d("FCMTracker", "Handling PING_REQUEST")
                val intent = Intent(this, LocationTrackingService::class.java).apply {
                    this.action = LocationTrackingService.ACTION_FORCE_UPDATE
                }
                startService(intent)
            }
            "MODE_CHANGE" -> {
                Log.d("FCMTracker", "Handling MODE_CHANGE: $trackingMode")
                if (trackingMode in setOf("live", "interval", "off")) {
                    val intent = Intent(this, LocationTrackingService::class.java).apply {
                        this.action = LocationTrackingService.ACTION_UPDATE_TRACKING_STATE
                        putExtra("trackingMode", trackingMode)
                    }
                    startService(intent)
                } else {
                    Log.w("FCMTracker", "Ignoring MODE_CHANGE with invalid tracking mode")
                }
            }
            else -> Log.w("FCMTracker", "Ignoring unsupported or malformed command")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCMTracker", "New token generated: $token")
        
        val deviceId = deviceAuthManager.getDeviceId()
        if (deviceId.isNotEmpty()) {
            serviceScope.launch {
                try {
                    firestore.collection("devices").document(deviceId)
                        .update("fcmToken", token)
                    Log.d("FCMTracker", "FCM token synced to Firestore for device: $deviceId")
                } catch (e: Exception) {
                    Log.e("FCMTracker", "Failed to sync FCM token", e)
                }
            }
        }
    }
}
