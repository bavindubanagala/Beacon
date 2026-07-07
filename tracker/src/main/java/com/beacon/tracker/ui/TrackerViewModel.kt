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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.content.Intent

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val deviceAuthManager = DeviceAuthManager(application)
    private val db = FirebaseFirestore.getInstance()
    
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

    init {
        startPairingListener()
    }

    private fun startPairingListener() {
        val deviceId = _deviceId.value
        pairingListener = db.collection("devices").document(deviceId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val paired = snapshot.getBoolean("is_paired") ?: false
                    _isPaired.value = paired
                    deviceAuthManager.setPaired(paired)
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
                
                // 1. Reset paired status locally and in Firestore
                _isPaired.value = false
                try {
                    db.collection("devices").document(deviceId)
                        .update("is_paired", false)
                        .await()
                } catch (e: Exception) {
                    // Document might not exist yet, that's fine
                }

                // 2. Create pairing code with expiresAt
                val pairingData = mapOf(
                    "code" to code,
                    "deviceId" to deviceId,
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

    override fun onCleared() {
        super.onCleared()
        pairingListener?.remove()
    }
}
