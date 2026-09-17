package com.beacon.admin.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.admin.ui.components.GeofenceZoneState
import com.beacon.admin.ui.components.MapMarkerState
import com.beacon.data.auth.AuthManager
import com.beacon.data.repository.DeviceRepository
import com.beacon.data.repository.LocationRepository
import com.beacon.shared.models.Device
import com.beacon.shared.models.Location
import com.beacon.shared.repository.FirebaseGeofenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TimeRange {
    TODAY,
    LAST_24H,
    LAST_7D
}

data class TelemetryAnalytics(
    val peakSpeed: Float,
    val averageSpeed: Float,
    val startingBattery: Int,
    val currentBattery: Int,
    val estimatedRemainingMinutes: Int
)

data class DeviceDetailsUiState(
    val device: Device? = null,
    val isLoading: Boolean = true,
    val markerState: MapMarkerState? = null,
    val geofences: List<GeofenceZoneState> = emptyList(),
    val historicalLocations: List<Location> = emptyList(),
    val playbackIndex: Int = 0,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val selectedTimeRange: TimeRange = TimeRange.TODAY,
    val analytics: TelemetryAnalytics? = null,
    val isFullScreenMap: Boolean = false,
    val error: String? = null,
    val isPingInFlight: Boolean = false,
    val showRenameDialog: Boolean = false,
    val isRenaming: Boolean = false,
    val isUpdatingThresholds: Boolean = false,
    val showUnpairDialog: Boolean = false,
    val isUnpairing: Boolean = false,
    val isSavingGeofence: Boolean = false,
    val availableGroups: List<com.beacon.shared.models.DeviceGroup> = emptyList(),
    val initialLat: Double? = null,
    val initialLng: Double? = null,
    val isSendingCommand: Boolean = false,
    val lastCommandStatus: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DeviceDetailsViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val locationRepository: LocationRepository,
    private val geofenceRepository: FirebaseGeofenceRepository,
    private val groupRepository: com.beacon.data.repository.GroupRepository,
    private val commandRepository: com.beacon.admin.repository.FirebaseCommandRepository,
    private val authManager: AuthManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val deviceId: String = checkNotNull(savedStateHandle["deviceId"])
    private val initialLat: Double? = savedStateHandle.get<String>("lat")?.toDoubleOrNull()
    private val initialLng: Double? = savedStateHandle.get<String>("lng")?.toDoubleOrNull()

    private val _isPingInFlight = MutableStateFlow(false)
    private val _showRenameDialog = MutableStateFlow(false)
    private val _isRenaming = MutableStateFlow(false)
    private val _isUpdatingThresholds = MutableStateFlow(false)
    private val _showUnpairDialog = MutableStateFlow(false)
    private val _isUnpairing = MutableStateFlow(false)
    private val _isFullScreenMap = MutableStateFlow(false)
    private val _isSavingGeofence = MutableStateFlow(false)
    private val _isSendingCommand = MutableStateFlow(false)

    // Playback State
    private val _selectedTimeRange = MutableStateFlow(TimeRange.TODAY)
    private val _playbackIndex = MutableStateFlow(0)
    private val _isPlaying = MutableStateFlow(false)
    private val _playbackSpeed = MutableStateFlow(1.0f)
    private val _historicalLocations = MutableStateFlow<List<Location>>(emptyList())

    private var playbackJob: Job? = null

    private val _unpairSuccessEvents = MutableSharedFlow<Unit>()
    val unpairSuccessEvents: SharedFlow<Unit> = _unpairSuccessEvents.asSharedFlow()

    init {
        fetchHistoricalData()
    }

    val uiState: StateFlow<DeviceDetailsUiState> = combine(
        flow {
            authManager.ensureAuthenticated()
            emit(Unit)
        }.flatMapLatest {
            val userId = authManager.currentUserId ?: ""
            deviceRepository.getDevicesStream(userId)
        }.map { devices -> devices.find { it.deviceId == deviceId } },
        geofenceRepository.getGeofencesForDevice(deviceId),
        flow {
            emit(groupRepository.getAllGroups().getOrDefault(emptyList()))
        },
        _historicalLocations,
        _playbackIndex,
        _isPlaying,
        _playbackSpeed,
        _selectedTimeRange,
        _isPingInFlight,
        _showRenameDialog,
        _isRenaming,
        _isUpdatingThresholds,
        _showUnpairDialog,
        _isUnpairing,
        _isFullScreenMap,
        _isSavingGeofence,
        _isSendingCommand,
        commandRepository.getLatestCommandStatus(deviceId)
    ) { flows ->
        val device = flows[0] as Device?
        val fences = flows[1] as List<com.beacon.shared.models.GeofenceZone>
        val groups = flows[2] as List<com.beacon.shared.models.DeviceGroup>
        val history = flows[3] as List<Location>
        val pIndex = flows[4] as Int
        val playing = flows[5] as Boolean
        val pSpeed = flows[6] as Float
        val tRange = flows[7] as TimeRange
        val inFlight = flows[8] as Boolean
        val showRename = flows[9] as Boolean
        val renaming = flows[10] as Boolean
        val updatingThresholds = flows[11] as Boolean
        val showUnpair = flows[12] as Boolean
        val unpairing = flows[13] as Boolean
        val fullScreenMap = flows[14] as Boolean
        val savingGeofence = flows[15] as Boolean
        val sendingCommand = flows[16] as Boolean
        val latestCommand = flows[17] as com.beacon.shared.models.RemoteCommand?

        DeviceDetailsUiState(
            device = device,
            isLoading = false,
            availableGroups = groups,
            historicalLocations = history,
            playbackIndex = pIndex,
            isPlaying = playing,
            playbackSpeed = pSpeed,
            selectedTimeRange = tRange,
            analytics = if (history.isNotEmpty()) computeAnalytics(history) else null,
            markerState = device?.let {
                MapMarkerState(
                    id = it.deviceId,
                    title = it.deviceName,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    status = it.statusLight,
                    accuracy = it.accuracy
                )
            },
            geofences = fences.map {
                GeofenceZoneState(
                    id = it.id,
                    name = it.name,
                    type = it.type,
                    centerLat = it.centerLat,
                    centerLng = it.centerLng,
                    radiusMeters = it.radiusMeters,
                    pointALat = it.pointALat,
                    pointALng = it.pointALng,
                    pointBLat = it.pointBLat,
                    pointBLng = it.pointBLng
                )
            },
            isFullScreenMap = fullScreenMap,
            error = if (device == null) "Device not found" else null,
            isPingInFlight = inFlight,
            showRenameDialog = showRename,
            isRenaming = renaming,
            isUpdatingThresholds = updatingThresholds,
            showUnpairDialog = showUnpair,
            isUnpairing = unpairing,
            isSavingGeofence = savingGeofence,
            initialLat = initialLat,
            initialLng = initialLng,
            isSendingCommand = sendingCommand,
            lastCommandStatus = latestCommand?.status
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceDetailsUiState()
    )

    fun setTimeRange(range: TimeRange) {
        _selectedTimeRange.value = range
        fetchHistoricalData()
    }

    fun togglePlayback() {
        if (_isPlaying.value) {
            playbackJob?.cancel()
            _isPlaying.value = false
        } else {
            _isPlaying.value = true
            startPlaybackLoop()
        }
    }

    fun setPlaybackPosition(index: Int) {
        _playbackIndex.value = index.coerceIn(0, _historicalLocations.value.size - 1)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    private fun fetchHistoricalData() {
        viewModelScope.launch {
            val endTime = System.currentTimeMillis()
            val startTime = when (_selectedTimeRange.value) {
                TimeRange.TODAY -> {
                    val calendar = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                    }
                    calendar.timeInMillis
                }
                TimeRange.LAST_24H -> endTime - 86400000L
                TimeRange.LAST_7D -> endTime - 604800000L
            }

            val result = locationRepository.getLocationsInTimeRange(deviceId, startTime, endTime)
            _historicalLocations.value = result.getOrDefault(emptyList())
            _playbackIndex.value = 0
        }
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (isActive && _isPlaying.value) {
                val currentList = _historicalLocations.value
                if (currentList.isEmpty()) break
                
                val nextIndex = (_playbackIndex.value + 1)
                if (nextIndex >= currentList.size) {
                    _isPlaying.value = false
                    break
                }
                
                _playbackIndex.value = nextIndex
                val delayMs = (500 / _playbackSpeed.value).toLong()
                kotlinx.coroutines.delay(delayMs)
            }
        }
    }

    private fun computeAnalytics(locations: List<Location>): TelemetryAnalytics {
        val peakSpeed = locations.maxOfOrNull { it.speed } ?: 0f
        val avgSpeed = if (locations.isNotEmpty()) locations.sumOf { it.speed.toDouble() }.toFloat() / locations.size else 0f
        val startBattery = locations.first().batteryLevel
        val currentBattery = locations.last().batteryLevel
        
        // Simple linear estimation
        val batteryDrop = startBattery - currentBattery
        val timeSpanMs = locations.last().timestamp - locations.first().timestamp
        val estimatedRemaining = if (batteryDrop > 0 && timeSpanMs > 0) {
            val msPerPercent = timeSpanMs / batteryDrop
            ((currentBattery * msPerPercent) / 60000).toInt()
        } else {
            0
        }

        return TelemetryAnalytics(
            peakSpeed = peakSpeed * 3.6f, // Convert m/s to km/h if needed, assuming m/s from GPS
            averageSpeed = avgSpeed * 3.6f,
            startingBattery = startBattery,
            currentBattery = currentBattery,
            estimatedRemainingMinutes = estimatedRemaining
        )
    }

    fun toggleFullScreenMap() {
        _isFullScreenMap.value = !_isFullScreenMap.value
    }

    fun updateTrackingMode(mode: String) {
        val currentDevice = uiState.value.device ?: return
        viewModelScope.launch {
            deviceRepository.updateDeviceSettings(
                deviceId = currentDevice.deviceId,
                mode = mode,
                intervalSeconds = currentDevice.intervalSeconds,
                autoRevertSeconds = currentDevice.autoRevertSeconds,
                isEmergency = currentDevice.isEmergencyMode,
                batterySavingEnabled = currentDevice.batterySavingEnabled,
                stationaryIntervalMinutes = currentDevice.stationaryIntervalMinutes,
                lowBatteryPercent = currentDevice.alertThresholds.lowBatteryPercent,
                offlineThresholdMinutes = currentDevice.alertThresholds.offlineThresholdMinutes,
                sosFallbackPhone = currentDevice.sosFallbackPhone
            )
        }
    }

    fun updatePingFrequency(seconds: Int) {
        val currentDevice = uiState.value.device ?: return
        viewModelScope.launch {
            deviceRepository.updateDeviceSettings(
                deviceId = currentDevice.deviceId,
                mode = currentDevice.trackingMode,
                intervalSeconds = seconds,
                autoRevertSeconds = currentDevice.autoRevertSeconds,
                isEmergency = currentDevice.isEmergencyMode,
                batterySavingEnabled = currentDevice.batterySavingEnabled,
                stationaryIntervalMinutes = currentDevice.stationaryIntervalMinutes,
                lowBatteryPercent = currentDevice.alertThresholds.lowBatteryPercent,
                offlineThresholdMinutes = currentDevice.alertThresholds.offlineThresholdMinutes,
                sosFallbackPhone = currentDevice.sosFallbackPhone
            )
        }
    }

    fun requestManualPing() {
        if (_isPingInFlight.value) return
        
        viewModelScope.launch {
            _isPingInFlight.value = true
            deviceRepository.requestManualPing(deviceId)
            // Keep the loading state visible for at least 2 seconds for feedback
            kotlinx.coroutines.delay(2000)
            _isPingInFlight.value = false
        }
    }

    fun setRenameDialogVisible(visible: Boolean) {
        _showRenameDialog.value = visible
    }

    fun updateDeviceName(newName: String) {
        if (newName.isBlank() || _isRenaming.value) return
        
        viewModelScope.launch {
            _isRenaming.value = true
            val result = deviceRepository.renameDevice(deviceId, newName)
            if (result.isSuccess) {
                _showRenameDialog.value = false
            }
            _isRenaming.value = false
        }
    }

    fun updateAlertThresholds(battery: Int? = null, crash: Boolean? = null, shock: Boolean? = null) {
        val current = uiState.value.device?.alertThresholds ?: return
        if (_isUpdatingThresholds.value) return

        viewModelScope.launch {
            _isUpdatingThresholds.value = true
            deviceRepository.updateAlertThresholds(
                deviceId = deviceId,
                lowBatteryThreshold = battery ?: current.lowBatteryPercent,
                crashDetectionEnabled = crash ?: current.isCrashDetectionEnabled,
                shockAlertEnabled = shock ?: current.isShockAlertEnabled
            )
            _isUpdatingThresholds.value = false
        }
    }

    fun setUnpairDialogVisible(visible: Boolean) {
        _showUnpairDialog.value = visible
    }

    fun confirmUnpairing() {
        if (_isUnpairing.value) return

        viewModelScope.launch {
            _isUnpairing.value = true
            val result = deviceRepository.unpairDevice(deviceId)
            if (result.isSuccess) {
                _showUnpairDialog.value = false
                _unpairSuccessEvents.emit(Unit)
            }
            _isUnpairing.value = false
        }
    }

    fun saveGeofence(geofence: com.beacon.shared.models.GeofenceZone) {
        if (_isSavingGeofence.value) return
        viewModelScope.launch {
            _isSavingGeofence.value = true
            geofenceRepository.saveGeofence(geofence)
            _isSavingGeofence.value = false
        }
    }

    fun deleteGeofence(geofenceId: String) {
        viewModelScope.launch {
            geofenceRepository.deleteGeofence(geofenceId)
        }
    }

    fun sendForcePingCommand() {
        sendCommand(com.beacon.shared.models.RemoteCommand(deviceId = deviceId, commandType = com.beacon.shared.models.CommandType.FORCE_PING))
    }

    fun sendIntervalOverrideCommand(intervalSeconds: Long) {
        sendCommand(com.beacon.shared.models.RemoteCommand(
            deviceId = deviceId,
            commandType = com.beacon.shared.models.CommandType.SET_INTERVAL,
            payload = mapOf("interval" to intervalSeconds.toString())
        ))
    }

    fun sendEmergencyToggleCommand(enable: Boolean) {
        sendCommand(com.beacon.shared.models.RemoteCommand(
            deviceId = deviceId,
            commandType = com.beacon.shared.models.CommandType.TOGGLE_EMERGENCY_MODE,
            payload = mapOf("enable" to enable.toString())
        ))
    }

    fun sendRingDeviceCommand() {
        sendCommand(com.beacon.shared.models.RemoteCommand(deviceId = deviceId, commandType = com.beacon.shared.models.CommandType.RING_DEVICE))
    }

    private fun sendCommand(command: com.beacon.shared.models.RemoteCommand) {
        if (_isSendingCommand.value) return
        viewModelScope.launch {
            _isSendingCommand.value = true
            commandRepository.sendCommand(command)
            _isSendingCommand.value = false
        }
    }
}
