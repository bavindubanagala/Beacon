package com.beacon.admin.ui.devices

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.shared.models.Device
import com.beacon.data.repository.DeviceRepository
import com.beacon.data.auth.AuthManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.beacon.admin.ui.utils.formatRelativeSyncTime

sealed class PairResult {
    object Idle : PairResult()
    object Loading : PairResult()
    data class Success(val deviceId: String) : PairResult()
    data class Error(val message: String, val cause: Throwable? = null) : PairResult()
}

data class DeviceUiModel(
    val id: String,
    val name: String,
    val isOnline: Boolean,
    val hasActiveSos: Boolean,
    val batteryLevel: Int,
    val lastSeenAgo: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speedKmh: Float,
    val signalDbm: Int,
    val trackingProfile: String,
    val speedFormatted: String,
    val signalFormatted: String
)

data class DevicesListState(
    val devices: List<DeviceUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val authManager: AuthManager,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()
    
    private val _pairResult = MutableStateFlow<PairResult>(PairResult.Idle)
    val pairResult: StateFlow<PairResult> = _pairResult.asStateFlow()

    init {
        val filter = savedStateHandle.get<String>("filter")
        if (filter != null) {
            when (filter) {
                "pair" -> _uiState.update { it.copy(isPairing = true) }
            }
        }
    }

    val devicesState: StateFlow<DevicesListState> = flow {
        authManager.ensureAuthenticated()
        emit(Unit)
    }.flatMapLatest {
        deviceRepository.devices.map { deviceList ->
            DevicesListState(
                devices = deviceList.map { device ->
                    val speedKmh = device.speed * 3.6f
                    DeviceUiModel(
                        id = device.deviceId,
                        name = device.deviceName,
                        isOnline = device.status.lowercase() in listOf("online", "live"),
                        hasActiveSos = device.isEmergencyMode,
                        batteryLevel = device.batteryLevel,
                        lastSeenAgo = formatRelativeSyncTime(device.lastSeenTimestamp),
                        latitude = device.latitude,
                        longitude = device.longitude,
                        accuracy = device.accuracy,
                        speedKmh = speedKmh,
                        signalDbm = device.signalStrength,
                        trackingProfile = device.trackingMode.replaceFirstChar { it.uppercase() },
                        speedFormatted = String.format("%.1f km/h", speedKmh),
                        signalFormatted = "${device.signalStrength} dBm"
                    )
                },
                isLoading = false
            )
        }.catch { e ->
            emit(DevicesListState(error = e.localizedMessage, isLoading = false))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DevicesListState(isLoading = true))

    fun onSearchQueryChanged(query: String) {
        // Handled by UI filtering in the screen
    }

    fun resetPairResult() {
        _pairResult.value = PairResult.Idle
    }

    fun pairDevice(code: String) {
        Log.d("PairDebug", "pairDevice() ENTERED with code=$code")
        _pairResult.value = PairResult.Loading

        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    _pairResult.value = PairResult.Error("Not signed in")
                    return@launch
                }

                val querySnapshot = firestore.collection("pairing_codes")
                    .whereEqualTo("code", code)
                    .get()
                    .await()

                if (querySnapshot.isEmpty) {
                    _pairResult.value = PairResult.Error("Invalid or expired code")
                    return@launch
                }

                val doc = querySnapshot.documents.first()
                val deviceId = doc.getString("deviceId")
                    ?: run {
                        _pairResult.value = PairResult.Error("Corrupted pairing record")
                        return@launch
                    }

                firestore.collection("devices").document(deviceId)
                    .set(mapOf(
                        "ownerId" to currentUser.uid, 
                        "owner_id" to currentUser.uid,
                        "is_paired" to true,
                        "status" to "online"
                    ), SetOptions.merge())
                    .await()

                doc.reference.delete().await()
                _pairResult.value = PairResult.Success(deviceId)

            } catch (e: Exception) {
                _pairResult.value = PairResult.Error(e.localizedMessage ?: "Pairing failed")
            }
        }
    }

    fun unpairDevice(deviceId: String) {
        viewModelScope.launch {
            try { deviceRepository.unpairDevice(deviceId) }
            catch (e: Exception) { }
        }
    }

    fun resetPairingState() { 
        _uiState.update { it.copy(isPairing = false, pairingSuccess = false, error = null) } 
        resetPairResult()
    }
    
    fun sendLocationPing(id: String) {
        viewModelScope.launch {
            deviceRepository.requestManualPing(id)
        }
    }

    fun updateTrackingProfile(id: String, profile: String) {
        viewModelScope.launch {
            // Mapping UI profile name to repository mode if needed, or just passing it
            // For now, assuming profile name matches expected mode string or similar
            // deviceRepository.updateDeviceSettings(...)
        }
    }

    fun assignGeofence(id: String, gId: String?) { /* logic */ }
}

data class DevicesUiState(
    val isPairing: Boolean = false,
    val pairingSuccess: Boolean = false,
    val error: String? = null,
    val pendingPairingCode: String? = null
)
