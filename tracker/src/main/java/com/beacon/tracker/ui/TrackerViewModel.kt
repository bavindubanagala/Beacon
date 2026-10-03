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
import com.beacon.tracker.geofence.TrackerGeofenceManager
import com.beacon.tracker.repository.FirebaseTrackerRepository
import com.beacon.tracker.service.LocationTrackingService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
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
    private var listenerRetryCount = 0

    init {
        viewModelScope.launch {
            ensureAnonymousAuth()
            ensureTrackerLink()
            startDeviceListener()
        }
    }

    private suspend fun ensureAnonymousAuth() {
        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously().await()
                Log.d("TrackerViewModel", "Anonymous sign-in completed")
            } catch (e: Exception) {
                Log.e("TrackerViewModel", "Anonymous auth failed", e)
                _statusMessage.value = "Setup Error: Enable 'Anonymous Auth' in Firebase"
            }
        }
    }

    private suspend fun ensureTrackerLink() {
        val uid = auth.currentUser?.uid ?: return
        val id = _deviceId.value
        if (id.isEmpty()) return
        try {
            firestore.collection("tracker_links").document(uid)
                .set(mapOf("deviceId" to id))
                .await()
        } catch (e: Exception) {
            Log.e("TrackerViewModel", "Could not write tracker link", e)
        }
    }

    private fun startDeviceListener() {
        deviceListener?.remove()
        deviceListener = null

        val id = _deviceId.value
        if (id.isEmpty()) return

        deviceListener = firestore.collection("devices").document(id)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("TrackerViewModel", "Device listener error", e)
                    if (listenerRetryCount < 5) {
                        listenerRetryCount++
                        viewModelScope.launch {
                            delay(3000)
                            startDeviceListener()
                        }
                    }
                    return@addSnapshotListener
                }

                listenerRetryCount = 0

                if (snapshot != null && snapshot.exists()) {
                    val paired = snapshot.getBoolean("is_paired") ?: false
                    val wasPaired = _isPaired.value
                    _isPaired.value = paired
                    deviceAuthManager.setPaired(paired)
                    if (wasPaired && !paired) {
                        stopTrackingService()
                    }

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
                listenerRetryCount = 0
                startDeviceListener()
                viewModelScope.launch { ensureTrackerLink() }
            }.onFailure { e ->
                _statusMessage.value = "Failed to generate code: ${e.message}"
            }
        }
    }

    fun forceUpdate() {
        if (_isUpdating.value) return

        val requestId = UUID.randomUUID().toString()

        viewModelScope.launch {
            _isUpdating.value = true
            _statusMessage.value = "Searching for GPS..."
            
            val context = getApplication<Application>()
            val intent = Intent(context, com.beacon.tracker.service.LocationTrackingService::class.java).apply {
                action = com.beacon.tracker.service.LocationTrackingService.ACTION_FORCE_UPDATE
                putExtra("requestId", requestId)
            }
            context.startService(intent)

            val result = withTimeoutOrNull(8000L) {
                com.beacon.tracker.service.ManualSyncBus.results.first { it.requestId == requestId }
            }

            _statusMessage.value = when {
                result == null -> "GPS Timeout - Are you indoors?"
                result.success -> "Location synced"
                else -> result.message
            }
            _isUpdating.value = false
        }
    }

    fun toggleTracking(enabled: Boolean) {
        val context = getApplication<Application>()
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_UPDATE_TRACKING_STATE
            putExtra(LocationTrackingService.EXTRA_TRACKING_PAUSED, !enabled)
        }
        context.startService(intent)
        _statusMessage.value = if (enabled) "Tracking resumed" else "Tracking paused"
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

    private fun stopTrackingService() {
        val context = getApplication<Application>()
        androidx.work.WorkManager.getInstance(context)
            .cancelUniqueWork(com.beacon.tracker.worker.ServiceWatchdogWorker.WORK_NAME)
        val stopIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_SERVICE
        }
        context.startService(stopIntent)
    }

    fun resetAndUnpair() {
        val id = _deviceId.value
        viewModelScope.launch {
            _statusMessage.value = "Unpairing..."
            try {
                try {
                    firestore.collection("devices").document(id)
                        .update(
                            mapOf(
                                "is_paired" to false,
                                "status" to "unpaired",
                                "unpairRequested" to true,
                                "unpairRequestedAt" to System.currentTimeMillis()
                            )
                        )
                        .await()
                } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                    if (e.code != com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND) throw e
                }
                stopTrackingService()
                TrackerGeofenceManager(getApplication()).clearAll()
                deviceAuthManager.clearAuth()
                deviceListener?.remove()
                deviceListener = null
                _deviceId.value = deviceAuthManager.getDeviceId() ?: ""
                _isPaired.value = false
                _pairingCode.value = null
                _statusMessage.value = "Phone unpaired. The Admin must delete the device record."
            } catch (e: Exception) {
                _statusMessage.value = "Unpair failed: ${e.message}"
            }
        }
    }

    fun checkPairingStatus() {
        val id = _deviceId.value
        if (id.isEmpty()) return

        viewModelScope.launch {
            try {
                val doc = firestore.collection("devices").document(id).get().await()
                if (doc.exists() && doc.getBoolean("is_paired") == true) {
                    _isPaired.value = true
                    deviceAuthManager.setPaired(true)
                }
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
