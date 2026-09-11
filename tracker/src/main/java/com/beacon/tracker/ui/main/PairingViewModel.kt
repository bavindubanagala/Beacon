package com.beacon.tracker.ui.main

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.shared.constants.FirestoreCollections
import com.beacon.tracker.auth.DeviceAuthManager
import com.beacon.tracker.services.LocationTrackingService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class PairingUiState(
    val isPaired: Boolean = false,
    val deviceName: String = "",
    val deviceId: String = "",
    val isPairing: Boolean = false,
    val error: String? = null,
    val isTrackingActive: Boolean = false,
    val currentProfile: String = "Balanced",
    val isSosActive: Boolean = false,
    val offlineCacheCount: Int = 0,
    val isBatteryOptimized: Boolean = false
)

class PairingViewModel(application: Application) : AndroidViewModel(application) {
    private val deviceAuthManager = DeviceAuthManager(application)
    private val firestore = FirebaseFirestore.getInstance()
    
    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    private var deviceListener: ListenerRegistration? = null

    init {
        val paired = deviceAuthManager.isPaired()
        val id = deviceAuthManager.getDeviceId()
        val batteryOptimized = !com.beacon.tracker.util.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(application)
        _uiState.update { it.copy(isPaired = paired, deviceId = id, isBatteryOptimized = batteryOptimized) }
        
        if (paired && id.isNotBlank()) {
            startDeviceObserver(id)
        }
    }

    fun updateBatteryOptimizationState() {
        val context = getApplication<Application>()
        val batteryOptimized = !com.beacon.tracker.util.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        _uiState.update { it.copy(isBatteryOptimized = batteryOptimized) }
    }

    fun pairDevice(pairingCode: String, displayName: String) {
        val cleanCode = pairingCode.trim()
        val cleanName = displayName.trim().ifEmpty { "Tracker Device" }

        if (cleanCode.isEmpty()) {
            _uiState.update { it.copy(error = "Please enter a valid pairing code") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPairing = true, error = null) }
            try {
                val query = firestore.collection("pairing_codes")
                    .whereEqualTo("code", cleanCode)
                    .get()
                    .await()

                if (query.isEmpty) {
                    _uiState.update { it.copy(isPairing = false, error = "Invalid Pairing Code") }
                    return@launch
                }

                val doc = query.documents.first()
                val deviceId = doc.getString("deviceId") ?: ""
                
                if (deviceId.isBlank()) {
                    _uiState.update { it.copy(isPairing = false, error = "Invalid device metadata in pairing code") }
                    return@launch
                }

                val deviceUpdates = mapOf(
                    "deviceName" to cleanName,
                    "is_paired" to true,
                    "pairedAt" to System.currentTimeMillis()
                )
                firestore.collection(FirestoreCollections.DEVICES).document(deviceId)
                    .set(deviceUpdates, com.google.firebase.firestore.SetOptions.merge())
                    .await()

                deviceAuthManager.setPaired(true)
                deviceAuthManager.saveDeviceId(deviceId)
                
                _uiState.update { 
                    it.copy(
                        isPaired = true, 
                        deviceId = deviceId, 
                        deviceName = cleanName, 
                        isPairing = false
                    ) 
                }
                startDeviceObserver(deviceId)
                doc.reference.delete()
            } catch (e: Exception) {
                _uiState.update { it.copy(isPairing = false, error = e.localizedMessage ?: "Pairing failed") }
            }
        }
    }

    private fun startDeviceObserver(deviceId: String) {
        deviceListener?.remove()
        deviceListener = firestore.collection(FirestoreCollections.DEVICES).document(deviceId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                snapshot?.let { doc ->
                    _uiState.update { it.copy(
                        deviceName = doc.getString("deviceName") ?: "",
                        currentProfile = doc.getString("trackingMode") ?: "Balanced",
                        isSosActive = doc.getBoolean("isEmergencyMode") ?: false
                    ) }
                }
            }
    }

    fun toggleTracking(enabled: Boolean) {
        val context = getApplication<Application>()
        val intent = Intent(context, LocationTrackingService::class.java)
        if (enabled) {
            context.startForegroundService(intent)
        } else {
            context.stopService(intent)
        }
        _uiState.update { it.copy(isTrackingActive = enabled) }
    }

    fun triggerSos() {
        val deviceId = _uiState.value.deviceId
        if (deviceId.isBlank()) return
        viewModelScope.launch {
            try {
                firestore.collection(FirestoreCollections.DEVICES).document(deviceId)
                    .update(mapOf(
                        "isEmergencyMode" to true,
                        "trackingMode" to "live",
                        "sosTimestamp" to System.currentTimeMillis()
                    )).await()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "SOS Trigger Failed") }
            }
        }
    }

    fun unpair() {
        viewModelScope.launch {
            val deviceId = _uiState.value.deviceId
            try {
                if (deviceId.isNotBlank()) {
                    firestore.collection(FirestoreCollections.DEVICES).document(deviceId)
                        .update("is_paired", false).await()
                }
                deviceAuthManager.setPaired(false)
                toggleTracking(false)
                _uiState.update { it.copy(isPaired = false) }
                deviceListener?.remove()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Unpair Failed") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        deviceListener?.remove()
    }
}
