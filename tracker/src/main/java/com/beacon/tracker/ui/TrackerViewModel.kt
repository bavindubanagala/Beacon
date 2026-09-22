package com.beacon.tracker.ui

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.tracker.auth.DeviceAuthManager
import com.beacon.tracker.data.LocationEntity
import com.beacon.tracker.data.LocationRepository
import com.beacon.tracker.repository.FirebaseTrackerRepository
import com.beacon.tracker.service.LocationTrackingService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class TrackerViewModel @Inject constructor(
    application: Application,
    locationRepository: LocationRepository,
    private val trackerRepository: FirebaseTrackerRepository,
    private val deviceAuthManager: DeviceAuthManager,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : AndroidViewModel(application) {

    val uiState: StateFlow<TrackerUiState> = locationRepository.getLatestLocation()
        .map<LocationEntity?, TrackerUiState> { location ->
            TrackerUiState.Success(location)
        }
        .catch { e ->
            emit(TrackerUiState.Error(e.localizedMessage ?: "Unknown error occurred"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TrackerUiState.Loading
        )

    private val _deviceId = mutableStateOf(deviceAuthManager.getDeviceId() ?: "")
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

    private val _isSosActive = mutableStateOf(false)
    val isSosActive: State<Boolean> = _isSosActive

    private var deviceListener: ListenerRegistration? = null

    init {
        ensureAnonymousAuth()
        startDeviceListener()
    }

    private fun ensureAnonymousAuth() {
        if (auth.currentUser == null) {
            viewModelScope.launch {
                try {
                    // Sign-in handled by AuthManager or Repository in production, 
                    // but keeping logic here for UI feedback during init
                    auth.signInAnonymously()
                    Log.d("TrackerViewModel", "Anonymous auth check triggered")
                } catch (e: Exception) {
                    Log.e("TrackerViewModel", "Anonymous auth failed", e)
                    _statusMessage.value = "Setup Error: Enable 'Anonymous Auth' in Firebase"
                }
            }
        }
    }

    private fun startDeviceListener() {
        val id = _deviceId.value
        if (id.isEmpty()) return
        
        deviceListener = firestore.collection("devices").document(id)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("TrackerViewModel", "Device listener error", e)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    val paired = snapshot.getBoolean("is_paired") ?: false
                    _isPaired.value = paired
                    deviceAuthManager.setPaired(paired)

                    val sos = snapshot.getBoolean("isEmergencyMode")
                        ?: snapshot.getBoolean("is_emergency_mode")
                        ?: false
                    _isSosActive.value = sos
                }
            }
    }

    fun generatePairingCode() {
        val code = (100000..999999).random().toString()
        viewModelScope.launch {
            _statusMessage.value = "Generating code..."
            
            val result = trackerRepository.pairDevice(code)
            result.onSuccess {
                _pairingCode.value = code
                _pairingExpiresAt.value = System.currentTimeMillis() + 15 * 60 * 1000L
                _statusMessage.value = "Code generated: $code"
                _isPaired.value = false
            }.onFailure { e ->
                _statusMessage.value = "Failed to generate code: ${e.message}"
            }
        }
    }

    fun forceUpdate() {
        if (_isUpdating.value) return

        viewModelScope.launch {
            _isUpdating.value = true
            _statusMessage.value = "Searching for GPS..."
            getApplication<Application>().sendBroadcast(
                Intent(LocationTrackingService.ACTION_FORCE_UPDATE)
            )
            delay(8000)
            if (_statusMessage.value == "Searching for GPS...") {
                _statusMessage.value = "GPS Timeout - Are you indoors?"
            }
            _isUpdating.value = false
        }
    }

    fun toggleTracking(enabled: Boolean) {
        val intent = Intent(LocationTrackingService.ACTION_UPDATE_TRACKING_STATE)
            .putExtra(LocationTrackingService.EXTRA_TRACKING_PAUSED, !enabled)
        getApplication<Application>().sendBroadcast(intent)
        _statusMessage.value = if (enabled) "Tracking started" else "Tracking stopped"
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
        val id = _deviceId.value
        viewModelScope.launch {
            _statusMessage.value = "Triggering SOS..."
            val result = trackerRepository.triggerSos(id, true)
            result.onSuccess {
                _statusMessage.value = "SOS Triggered!"
                _isSosActive.value = true
            }.onFailure { e ->
                _statusMessage.value = "SOS Failed: ${e.message}"
            }
        }
    }

    fun resetAndUnpair() {
        val id = _deviceId.value
        viewModelScope.launch {
            _statusMessage.value = "Unpairing..."
            // Repository should handle full cleanup in production, 
            // but keeping this for immediate local auth clearing
            try {
                firestore.collection("devices").document(id).delete()
                deviceAuthManager.clearAuth()
                _deviceId.value = deviceAuthManager.getDeviceId() ?: ""
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
        if (id.isEmpty()) return
        
        viewModelScope.launch {
            try {
                val doc = firestore.collection("devices").document(id).get()
                // Synchronous check if possible or handled via listener
                _statusMessage.value = "Checking pairing..."
            } catch (e: Exception) {
                Log.e("TrackerViewModel", "Failed to check pairing", e)
            }
        }
    }

    override fun onCleared() {
        deviceListener?.remove()
        super.onCleared()
    }
}
