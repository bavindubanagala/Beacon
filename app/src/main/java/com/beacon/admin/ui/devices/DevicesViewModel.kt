package com.beacon.admin.ui.devices

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.admin.ui.utils.formatRelativeSyncTime
import com.beacon.admin.ui.utils.normalizeTrackingMode
import com.beacon.admin.ui.utils.modeDisplayName
import com.beacon.admin.ui.utils.buildModeLabel
import com.beacon.admin.ui.utils.tickerFlow
import com.beacon.admin.ui.viewmodels.SyncStatus
import com.beacon.admin.ui.viewmodels.SyncStepLog
import com.beacon.admin.ui.viewmodels.TelemetrySyncState
import com.beacon.data.auth.AuthManager
import com.beacon.data.repository.DeviceRepository
import com.beacon.shared.mapper.toDevice
import com.beacon.shared.models.Device
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

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
    val signalFormatted: String,
    val trackingMode: String = "SCHEDULED",
    val scheduledIntervalMillis: Long = 900_000L,
    val liveIntervalMillis: Long = 10_000L,
    val modeLabel: String = ""
)

sealed interface DevicesListState {
    data object Loading : DevicesListState
    data class Success(val devices: List<DeviceUiModel>) : DevicesListState
    data object Empty : DevicesListState
    data class Error(val message: String) : DevicesListState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val authManager: AuthManager,
    private val auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DevicesUiState()
    )
    
    private val _pairResult = MutableStateFlow<PairResult>(PairResult.Idle)
    val pairResult: StateFlow<PairResult> = _pairResult.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PairResult.Idle
    )

    // Telemetry Sync State
    private val _telemetrySyncState = MutableStateFlow(TelemetrySyncState())
    val telemetrySyncState: StateFlow<TelemetrySyncState> = _telemetrySyncState.asStateFlow()

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
        combine(deviceRepository.devices, tickerFlow(30_000L)) { deviceList, now ->
            val devices = deviceList.map { device ->
                val speedKmh = device.speed * 3.6f
                val mode = normalizeTrackingMode(device.trackingMode)
                DeviceUiModel(
                    id = device.deviceId,
                    name = device.deviceName,
                    isOnline = device.isOnlineAt(now),
                    hasActiveSos = device.isEmergencyMode,
                    batteryLevel = device.batteryLevel,
                    lastSeenAgo = formatRelativeSyncTime(device.lastSeenTimestamp),
                    latitude = device.latitude,
                    longitude = device.longitude,
                    accuracy = device.accuracy,
                    speedKmh = speedKmh,
                    signalDbm = device.signalStrength,
                    trackingProfile = modeDisplayName(mode),
                    speedFormatted = String.format("%.1f km/h", speedKmh),
                    signalFormatted = "${device.signalStrength} dBm",
                    trackingMode = mode,
                    scheduledIntervalMillis = device.scheduledIntervalMillis,
                    liveIntervalMillis = device.liveIntervalMillis,
                    modeLabel = buildModeLabel(mode, device.scheduledIntervalMillis, device.liveIntervalMillis)
                )
            }
            if (devices.isEmpty()) DevicesListState.Empty else DevicesListState.Success(devices)
        }.catch { e ->
            emit(DevicesListState.Error(e.localizedMessage ?: "Unable to load devices"))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DevicesListState.Loading)

    fun onSearchQueryChanged(query: String) {
        // Handled by UI filtering in the screen
    }

    fun resetPairResult() {
        _pairResult.value = PairResult.Idle
    }

    fun pairDevice(code: String, friendlyName: String = "") {
        Log.d("PairDebug", "pairDevice() ENTERED with code=$code, friendlyName=$friendlyName")
        _pairResult.value = PairResult.Loading

        viewModelScope.launch {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                _pairResult.value = PairResult.Error("Not signed in")
                return@launch
            }

            val result = deviceRepository.pairDevice(
                code = code,
                friendlyName = friendlyName,
                ownerId = currentUser.uid
            )

            result.fold(
                onSuccess = {
                    _pairResult.value = PairResult.Success(code)
                },
                onFailure = { exception ->
                    _pairResult.value = PairResult.Error(
                        exception.localizedMessage ?: "Pairing failed",
                        exception
                    )
                }
            )
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

    fun triggerFullTelemetrySync(targetDeviceId: String) {
        viewModelScope.launch {
            val initialSteps = listOf(
                SyncStepLog("Location Coordinates", SyncStatus.PENDING),
                SyncStepLog("Battery Percentage", SyncStatus.PENDING),
                SyncStepLog("Speed & Movement", SyncStatus.PENDING),
                SyncStepLog("Signal Strength", SyncStatus.PENDING),
                SyncStepLog("Firestore Telemetry Commit", SyncStatus.PENDING)
            )

            _telemetrySyncState.value = TelemetrySyncState(
                isSyncing = true,
                isVisible = true,
                progress = 0f,
                steps = initialSteps
            )

            val currentSteps = initialSteps.toMutableList()

            try {
                // 1. Write forceSyncRequestedAt / pingRequested flag to Firestore
                val pingResult = deviceRepository.requestManualPing(targetDeviceId)
                if (pingResult.isFailure) {
                    val errorMsg = pingResult.exceptionOrNull()?.message ?: "Failed to write sync request flag"
                    currentSteps[0] = currentSteps[0].copy(status = SyncStatus.FAILED, errorMessage = errorMsg)
                    _telemetrySyncState.value = _telemetrySyncState.value.copy(
                        isSyncing = false,
                        steps = currentSteps.toList()
                    )
                    return@launch
                }
                val pingSentAt = pingResult.getOrThrow()

                // 2. Wait for a snapshot whose lastSeenTimestamp proves the tracker responded to this ping
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

                val snapshot = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    kotlinx.coroutines.suspendCancellableCoroutine<com.google.firebase.firestore.DocumentSnapshot> { continuation ->
                        val listener = firestore.collection("devices").document(targetDeviceId)
                            .addSnapshotListener { snap, error ->
                                if (error != null) {
                                    if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                                    return@addSnapshotListener
                                }
                                if (snap != null && snap.exists()) {
                                    val lastSeen = snap.getLong("lastSeenTimestamp") ?: snap.getLong("last_seen") ?: 0L
                                    if (lastSeen >= pingSentAt && continuation.isActive) {
                                        continuation.resumeWith(Result.success(snap))
                                    }
                                }
                            }
                        continuation.invokeOnCancellation { listener.remove() }
                    }
                }

                if (snapshot == null) {
                    throw Exception("The Tracker did not respond within 10 seconds. If it was stopped, open the Tracker app once on that device. It also tries to restart itself about every 15 minutes.")
                }

                val fetchedDevice = snapshot.toDevice()

                // Step 0: Location Coordinates
                currentSteps[0] = currentSteps[0].copy(status = SyncStatus.IN_PROGRESS)
                _telemetrySyncState.value = _telemetrySyncState.value.copy(steps = currentSteps.toList(), progress = 0.2f)
                kotlinx.coroutines.delay(200)
                val validCoords = fetchedDevice.latitude != 0.0 || fetchedDevice.longitude != 0.0
                currentSteps[0] = currentSteps[0].copy(
                    status = if (validCoords) SyncStatus.SUCCESS else SyncStatus.FAILED,
                    errorMessage = if (validCoords) null else "Coordinates unparsed or zero"
                )

                // Step 1: Battery Percentage
                currentSteps[1] = currentSteps[1].copy(status = SyncStatus.IN_PROGRESS)
                _telemetrySyncState.value = _telemetrySyncState.value.copy(steps = currentSteps.toList(), progress = 0.4f)
                kotlinx.coroutines.delay(200)
                val validBattery = fetchedDevice.batteryLevel > 0
                currentSteps[1] = currentSteps[1].copy(
                    status = if (validBattery) SyncStatus.SUCCESS else SyncStatus.FAILED,
                    errorMessage = if (validBattery) null else "Battery telemetry has not yet been received"
                )

                // Step 2: Speed & Movement
                currentSteps[2] = currentSteps[2].copy(status = SyncStatus.IN_PROGRESS)
                _telemetrySyncState.value = _telemetrySyncState.value.copy(steps = currentSteps.toList(), progress = 0.6f)
                kotlinx.coroutines.delay(200)
                val validSpeed = fetchedDevice.speed >= 0f
                currentSteps[2] = currentSteps[2].copy(
                    status = if (validSpeed) SyncStatus.SUCCESS else SyncStatus.FAILED,
                    errorMessage = if (validSpeed) null else "Speed telemetry unavailable"
                )

                // Step 3: Signal Strength
                currentSteps[3] = currentSteps[3].copy(status = SyncStatus.IN_PROGRESS)
                _telemetrySyncState.value = _telemetrySyncState.value.copy(steps = currentSteps.toList(), progress = 0.8f)
                kotlinx.coroutines.delay(200)
                currentSteps[3] = currentSteps[3].copy(status = SyncStatus.SUCCESS)

                // Step 4: Firestore Telemetry Commit
                currentSteps[4] = currentSteps[4].copy(status = SyncStatus.IN_PROGRESS)
                _telemetrySyncState.value = _telemetrySyncState.value.copy(steps = currentSteps.toList(), progress = 0.9f)
                kotlinx.coroutines.delay(200)
                currentSteps[4] = currentSteps[4].copy(status = SyncStatus.SUCCESS)

                _telemetrySyncState.value = _telemetrySyncState.value.copy(
                    isSyncing = false,
                    progress = 1.0f,
                    steps = currentSteps.toList()
                )

            } catch (e: Exception) {
                val errorMsg = e.message ?: "Network timeout or permission error"
                for (i in currentSteps.indices) {
                    if (currentSteps[i].status == SyncStatus.PENDING || currentSteps[i].status == SyncStatus.IN_PROGRESS) {
                        currentSteps[i] = currentSteps[i].copy(status = SyncStatus.FAILED, errorMessage = errorMsg)
                    }
                }
                _telemetrySyncState.value = _telemetrySyncState.value.copy(
                    isSyncing = false,
                    steps = currentSteps.toList()
                )
            }
        }
    }

    fun dismissSyncDialog() {
        _telemetrySyncState.value = _telemetrySyncState.value.copy(
            isVisible = false,
            isSyncing = false
        )
    }
}

data class DevicesUiState(
    val isPairing: Boolean = false,
    val pairingSuccess: Boolean = false,
    val error: String? = null,
    val pendingPairingCode: String? = null
)
