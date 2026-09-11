package com.beacon.tracker.services

import android.content.Intent
import android.util.Log
import com.beacon.tracker.auth.DeviceAuthManager
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

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d("FCMTracker", "Message received: ${remoteMessage.data}")
        
        val action = remoteMessage.data["action"]
        val trackingMode = remoteMessage.data["trackingMode"]
        
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
                if (trackingMode != null) {
                    val intent = Intent(this, LocationTrackingService::class.java).apply {
                        this.action = LocationTrackingService.ACTION_UPDATE_TRACKING_STATE
                        putExtra("trackingMode", trackingMode)
                    }
                    startService(intent)
                }
            }
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
