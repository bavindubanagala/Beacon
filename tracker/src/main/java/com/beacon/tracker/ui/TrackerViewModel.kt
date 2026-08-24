package com.beacon.tracker.ui

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.shared.models.PairingCode
import com.beacon.tracker.auth.DeviceAuthManager
import com.beacon.tracker.services.LocationTrackingService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.content.Intent
import android.util.Log

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val deviceAuthManager = DeviceAuthManager(application)
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _deviceId = mutableStateOf(deviceAuthManager.getDeviceId())
    val deviceId: State<String> = _deviceId

    private val _isUpdating = mutableStateOf(false)
    val isUpdating: State<Boolean> = _isUpdating

    private val _statusMessage = mutableStateOf("Ready to track")
    val statusMessage: State<String> = _statusMessage

    private val _isPaired = mutableStateOf(deviceAuthManager.isPaired())
    val isPaired: State<Boolean> = _isPaired

    private val _pairingCode = mutableStateOf<String?>(null)
    val pairingCode: State<String?> = _pairingCode

    private val _pairingExpiresAt = mutableStateOf(0L)
    val pairingExpiresAt: State<Long> = _pairingExpiresAt

    private val _isDarkMode = mutableStateOf(deviceAuthManager.isDarkMode())
    val isDarkMode: State<Boolean> = _isDarkMode

    private var pairingListener: ListenerRegistration? = null

    private val _isSosActive = mutableStateOf(false)
    val isSosActive: State<Boolean> = _isSosActive

    init {
        ensureAnonymousAuth()
        startDeviceListener()
    }

    private fun ensureAnonymousAuth() {
        if (auth.currentUser == null) {
            viewModelScope.launch {
                try {
                    auth.signInAnonymously().await()
                    Log.d("TrackerViewModel", "Anonymous auth success: ${auth.currentUser?.uid}")
                } catch (e: Exception) {
                    Log.e("TrackerViewModel", "Anonymous auth failed", e)
                    _statusMessage.value = "Setup Error: Enable 'Anonymous Auth' in Firebase Console"
                }
            }
        }
    }

    private fun startDeviceListener() {
        val deviceId = _deviceId.value
        pairingListener = db.collection("devices").document(deviceId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val paired = snapshot.getBoolean("is_paired") ?: false
                    _isPaired.value = paired
                    deviceAuthManager.setPaired(paired)
                    
                    val sos = snapshot.getBoolean("isEmergencyMode") ?: snapshot.getBoolean("is_emergency_mode") ?: false
                    _isSosActive.value = sos
                }
            }
    }

    fun generatePairingCode() {
        val code = (100000..999999).random().toString()
        val deviceId = _deviceId.value
        val ttlMs = 15 * 60 * 1000L // 15 minutes
        val expiresAt = System.currentTimeMillis() + ttlMs
        
        viewModelScope.launch {
            try {
                _statusMessage.value = "Generating code..."

                // 0. Ensure we have an anonymous identity first
                if (auth.currentUser == null) {
                    try {
                        auth.signInAnonymously().await()
                    } catch (e: Exception) {
                        _statusMessage.value = "Auth failed: Enable Anonymous Auth in Firebase"
                        return@launch
                    }
                }
                
                // 1. Reset paired status locally and in Firestore
                _isPaired.value = false
                try {
                    val deviceUpdates = mapOf(
                        "is_paired" to false,
                        "trackerAuthUid" to (auth.currentUser?.uid ?: "")
                    )
                    db.collection("devices").document(deviceId)
                        .set(deviceUpdates, com.google.firebase.firestore.SetOptions.merge())
                        .await()
                } catch (e: Exception) {
                    Log.e("TrackerViewModel", "Failed to reset device doc", e)
                }

                // 2. Create pairing code with expiresAt and tracker UID
                val pairingData = mapOf(
                    "code" to code,
                    "deviceId" to deviceId,
                    "trackerAuthUid" to (auth.currentUser?.uid ?: ""),
                    "createdAt" to System.currentTimeMillis(),
                    "expiresAt" to expiresAt
                )
                db.collection("pairing_codes").document(code).set(pairingData).await()
                
                _pairingCode.value = code
                _pairingExpiresAt.value = expiresAt
                _statusMessage.value = "Code generated: $code"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to generate code: ${e.message}"
            }
        }
    }

    fun forceUpdate() {
        if (_isUpdating.value) return
        
        viewModelScope.launch {
            _isUpdating.value = true
            _statusMessage.value = "Searching for GPS..."
            
            // Send broadcast to Service
            val intent = Intent(LocationTrackingService.ACTION_FORCE_UPDATE)
            getApplication<Application>().sendBroadcast(intent)
            
            // The service will take some time. 
            // We'll reset the button after a timeout or success signal
            delay(8000)
            if (_statusMessage.value == "Searching for GPS...") {
                _statusMessage.value = "GPS Timeout - Are you indoors?"
            }
            _isUpdating.value = false
        }
    }

    fun updateStatus(message: String) {
        _statusMessage.value = message
        if (message.contains("Success")) {
            _isUpdating.value = false
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        deviceAuthManager.setDarkMode(enabled)
    }

    fun triggerSos() {
        val deviceId = _deviceId.value
        viewModelScope.launch {
            try {
                val updates = mapOf(
                    "sosActive" to true,
                    "sosTimestamp" to System.currentTimeMillis(),
                    "commandMode" to "live",
                    "command_mode" to "live",
                    "is_emergency_mode" to true,
                    "isEmergencyMode" to true,
                    "command_timestamp" to System.currentTimeMillis(),
                    "commandTimestamp" to System.currentTimeMillis()
                )
                db.collection("devices").document(deviceId).update(updates).await()
                _statusMessage.value = "SOS Triggered!"
                
                // Trigger SOS Alert to Firestore
                val alert = com.beacon.shared.models.Alert(
                    id = java.util.UUID.randomUUID().toString(),
                    alert_type = "SOS_ACTIVE",
                    device_id = deviceId,
                    device_name = "Tracker Device",
                    alert_severity = "CRITICAL",
                    message = "SOS EMERGENCY ACTIVATED",
                    created_at = System.currentTimeMillis()
                )
                db.collection("devices").document(deviceId).collection("alerts").document(alert.id).set(alert).await()

            } catch (e: Exception) {
                _statusMessage.value = "SOS Failed: ${e.message}"
            }
        }
    }

    fun resetAndUnpair() {
        val id = _deviceId.value
        viewModelScope.launch {
            try {
                _statusMessage.value = "Unpairing..."
                // 1. Delete from Firestore (Removes from Admin)
                db.collection("devices").document(id).delete().await()
                
                // 2. Clean up any existing pairing codes for this device
                val codes = db.collection("pairing_codes")
                    .whereEqualTo("deviceId", id)
                    .get()
                    .await()
                for (doc in codes.documents) {
                    doc.reference.delete().await()
                }

                // 3. Reset local state
                deviceAuthManager.clearAuth()
                _deviceId.value = deviceAuthManager.getDeviceId()
                _isPaired.value = false
                _pairingCode.value = null
                _statusMessage.value = "Device reset successfully"
            } catch (e: Exception) {
                _statusMessage.value = "Reset failed: ${e.message}"
            }
        }
    }

    fun checkPairingStatus() {
        val id = _deviceId.value
        viewModelScope.launch {
            try {
                val doc = db.collection("devices").document(id).get().await()
                if (doc.exists()) {
                    val paired = doc.getBoolean("is_paired") ?: false
                    _isPaired.value = paired
                    deviceAuthManager.setPaired(paired)
                }
            } catch (e: Exception) {
                Log.e("TrackerViewModel", "Failed to check pairing", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pairingListener?.remove()
    }
}
