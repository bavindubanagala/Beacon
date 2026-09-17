package com.beacon.admin.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.shared.models.Device
import com.beacon.data.repository.DeviceRepository
import com.beacon.data.auth.AuthManager
import com.beacon.admin.ui.components.MapMarkerState
import com.beacon.admin.ui.components.GeofenceZoneState
import com.beacon.shared.models.DeviceStatus
import com.beacon.shared.repository.FirebaseGeofenceRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// UI Models
data class MapPinState(
    val id: String,
    val deviceName: String,
    val status: DeviceStatus,
    val latitude: Double,
    val longitude: Double,
    val lastPing: String,
    val speed: Float = 0f,
    val accuracy: Float = 0f
)

/**
 * 1. UI State model explicitly capturing security/auth errors via sealed interface variants.
 */
sealed interface MapUiState {
    data object Loading : MapUiState
    
    data class Success(
        val isGeofencesVisible: Boolean = true,
        val isCreationMode: Boolean = false,
        val draftType: com.beacon.shared.models.GeofenceType? = null,
        val draftCenter: org.osmdroid.util.GeoPoint? = null,
        val draftPointA: org.osmdroid.util.GeoPoint? = null,
        val draftPointB: org.osmdroid.util.GeoPoint? = null,
        val draftRadius: Double = 100.0,
        val selectedMapStyle: String = "Standard",
        val selectedPinId: String? = null,
        val pins: List<MapPinState> = emptyList(),
        val geofences: List<GeofenceZoneState> = emptyList(),
        val totalActiveCount: Int = 0,
        val centerOn: Pair<Double, Double>? = null,
        val targetZoom: Double? = null,
        val availableGroups: List<String> = emptyList(),
        val selectedGroups: Set<String> = emptySet(),
        val selectedDeviceIds: Set<String> = emptySet(),
        val allDevicesForFilter: List<Device> = emptyList(),
        val allGroups: List<com.beacon.shared.models.DeviceGroup> = emptyList()
    ) : MapUiState

    data class Error(
        val message: String,
        val isAuthError: Boolean = false,
        val isPermissionDenied: Boolean = false
    ) : MapUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val geofenceRepository: FirebaseGeofenceRepository,
    private val groupRepository: com.beacon.data.repository.GroupRepository,
    private val authManager: AuthManager
) : ViewModel() {

    // Internal reactive settings
    private val _uiSettings = MutableStateFlow(MapSettings())
    
    data class MapSettings(
        val isGeofencesVisible: Boolean = true,
        val selectedMapStyle: String = "Standard",
        val selectedPinId: String? = null,
        val centerOn: Pair<Double, Double>? = null,
        val targetZoom: Double? = null,
        val selectedGroups: Set<String> = emptySet(),
        val selectedDeviceIds: Set<String> = emptySet()
    )

    // Reactive devices stream
    val devices: StateFlow<List<Device>> = flow {
        authManager.ensureAuthenticated()
        emit(Unit)
    }.flatMapLatest {
        deviceRepository.devices
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Aggregate Geofences
    private val _geofencesStream = devices.flatMapLatest { devicesList ->
        val deviceIds = devicesList.map { it.deviceId }
        val groupIds = devicesList.mapNotNull { it.groupId }.distinct()
        
        if (deviceIds.isEmpty() && groupIds.isEmpty()) {
            return@flatMapLatest flow { emit(emptyList<com.beacon.shared.models.GeofenceZone>()) }
        }

        val deviceFencesFlows = deviceIds.map { geofenceRepository.getGeofencesForDevice(it) }
        val groupFencesFlows = groupIds.map { geofenceRepository.getGeofencesForGroup(it) }
        
        combine(deviceFencesFlows + groupFencesFlows) { arrays ->
            arrays.flatMap { it as List<com.beacon.shared.models.GeofenceZone> }
                .distinctBy { it.id }
        }
    }

    // Interactive creation state
    private val _isCreationMode = MutableStateFlow(false)
    private val _draftType = MutableStateFlow<com.beacon.shared.models.GeofenceType?>(null)
    private val _draftCenter = MutableStateFlow<org.osmdroid.util.GeoPoint?>(null)
    private val _draftPointA = MutableStateFlow<org.osmdroid.util.GeoPoint?>(null)
    private val _draftPointB = MutableStateFlow<org.osmdroid.util.GeoPoint?>(null)
    private val _draftRadius = MutableStateFlow(100.0)

    /**
     * Main UI State collection with error mapping.
     */
    val uiState: StateFlow<MapUiState> = combine(
        devices,
        _geofencesStream,
        _uiSettings,
        _isCreationMode,
        _draftType,
        _draftCenter,
        _draftPointA,
        _draftPointB,
        _draftRadius,
        flow { emit(groupRepository.getAllGroups().getOrDefault(emptyList())) }
    ) { flows ->
        val devicesList = flows[0] as List<Device>
        val allGeofences = flows[1] as List<com.beacon.shared.models.GeofenceZone>
        val settings = flows[2] as MapSettings
        val creationMode = flows[3] as Boolean
        val dType = flows[4] as com.beacon.shared.models.GeofenceType?
        val dCenter = flows[5] as org.osmdroid.util.GeoPoint?
        val dPointA = flows[6] as org.osmdroid.util.GeoPoint?
        val dPointB = flows[7] as org.osmdroid.util.GeoPoint?
        val dRadius = flows[8] as Double
        val groupsData = flows[9] as List<com.beacon.shared.models.DeviceGroup>

        val groups = devicesList.mapNotNull { it.groupId }.distinct().sorted()
        
        val filteredDevices = devicesList.filter { device ->
            val matchesGroup = settings.selectedGroups.isEmpty() || device.groupId in settings.selectedGroups
            val matchesDevice = settings.selectedDeviceIds.isEmpty() || device.deviceId in settings.selectedDeviceIds
            matchesGroup && matchesDevice
        }

        val pins = filteredDevices.filter { device ->
            device.latitude != 0.0 && device.longitude != 0.0
        }.map { device ->
            MapPinState(
                id = device.deviceId,
                deviceName = device.deviceName,
                status = device.statusLight,
                latitude = device.latitude,
                longitude = device.longitude,
                lastPing = "Just now",
                speed = 0f,
                accuracy = device.accuracy
            )
        }

        val geofenceStates = if (settings.isGeofencesVisible) {
            allGeofences.map {
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
            }
        } else {
            emptyList()
        }

        // Force return as base interface to allow Error emission in catch block
        MapUiState.Success(
            isGeofencesVisible = settings.isGeofencesVisible,
            isCreationMode = creationMode,
            draftType = dType,
            draftCenter = dCenter,
            draftPointA = dPointA,
            draftPointB = dPointB,
            draftRadius = dRadius,
            selectedMapStyle = settings.selectedMapStyle,
            selectedPinId = settings.selectedPinId,
            centerOn = settings.centerOn,
            targetZoom = settings.targetZoom,
            availableGroups = groups,
            selectedGroups = settings.selectedGroups,
            selectedDeviceIds = settings.selectedDeviceIds,
            allDevicesForFilter = devicesList,
            allGroups = groupsData,
            totalActiveCount = filteredDevices.size,
            pins = pins,
            geofences = geofenceStates
        ) as MapUiState
    }.catch { e ->
        // 2. Map Firestore/Auth errors directly to specific UI state
        val isAuth = e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAUTHENTICATED
        val isPermission = e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        
        Log.e("MapViewModel", "Monitoring error: ${e.message}", e)
        emit(MapUiState.Error(
            message = e.localizedMessage ?: "Unknown monitoring error",
            isAuthError = isAuth,
            isPermissionDenied = isPermission
        ))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MapUiState.Loading
    )

    companion object {
        val DEFAULT_SRI_LANKA_CENTER = Pair(7.8731, 80.7718)
        const val DEFAULT_ZOOM = 7.0
        const val DETAIL_ZOOM = 12.0
    }

    val mapMarkers: StateFlow<List<MapMarkerState>> = uiState.map { state ->
        if (state is MapUiState.Success) {
            state.pins.map { pin ->
                MapMarkerState(
                    id = pin.id,
                    title = pin.deviceName,
                    latitude = pin.latitude,
                    longitude = pin.longitude,
                    status = pin.status,
                    accuracy = pin.accuracy
                )
            }
        } else {
            emptyList()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun toggleGeofences() {
        _uiSettings.update { it.copy(isGeofencesVisible = !it.isGeofencesVisible) }
    }

    fun updateMapStyle(style: String) {
        _uiSettings.update { it.copy(selectedMapStyle = style) }
    }

    fun selectPin(pinId: String?) {
        _uiSettings.update { settings ->
            val currentState = uiState.value
            val pins = if (currentState is MapUiState.Success) currentState.pins else emptyList()
            val selectedPin = pins.find { it.id == pinId }
            settings.copy(
                selectedPinId = pinId,
                centerOn = selectedPin?.let { it.latitude to it.longitude },
                targetZoom = if (selectedPin != null) DETAIL_ZOOM else settings.targetZoom
            )
        }
    }

    fun recenterOnSelected() {
        _uiSettings.update { settings ->
            val currentState = uiState.value
            val pins = if (currentState is MapUiState.Success) currentState.pins else emptyList()
            val selectedId = settings.selectedPinId
            
            val (targetCenter, targetZoom) = when {
                selectedId != null -> {
                    val pin = pins.find { it.id == selectedId }
                    if (pin != null) (pin.latitude to pin.longitude) to DETAIL_ZOOM
                    else DEFAULT_SRI_LANKA_CENTER to DEFAULT_ZOOM
                }
                pins.isEmpty() -> DEFAULT_SRI_LANKA_CENTER to DEFAULT_ZOOM
                pins.size == 1 -> (pins[0].latitude to pins[0].longitude) to DETAIL_ZOOM
                else -> DEFAULT_SRI_LANKA_CENTER to DEFAULT_ZOOM
            }

            settings.copy(
                centerOn = targetCenter,
                targetZoom = targetZoom
            )
        }
    }

    fun clearCentering() {
        _uiSettings.update { it.copy(centerOn = null, targetZoom = null) }
    }

    fun toggleGroupFilter(groupName: String) {
        _uiSettings.update { settings ->
            val current = settings.selectedGroups
            val updated = if (current.contains(groupName)) {
                current - groupName
            } else {
                current + groupName
            }
            settings.copy(selectedGroups = updated)
        }
    }

    fun toggleDeviceFilter(deviceId: String) {
        _uiSettings.update { settings ->
            val current = settings.selectedDeviceIds
            val updated = if (current.contains(deviceId)) {
                current - deviceId
            } else {
                current + deviceId
            }
            settings.copy(selectedDeviceIds = updated)
        }
    }

    fun startGeofenceCreation(type: com.beacon.shared.models.GeofenceType) {
        _draftType.value = type
        _isCreationMode.value = true
        val currentState = uiState.value
        val center = if (currentState is MapUiState.Success) currentState.centerOn ?: DEFAULT_SRI_LANKA_CENTER else DEFAULT_SRI_LANKA_CENTER
        _draftCenter.value = org.osmdroid.util.GeoPoint(center.first, center.second)
        _draftPointA.value = org.osmdroid.util.GeoPoint(center.first - 0.001, center.second - 0.001)
        _draftPointB.value = org.osmdroid.util.GeoPoint(center.first + 0.001, center.second + 0.001)
        _draftRadius.value = 100.0
    }

    fun cancelCreation() {
        _isCreationMode.value = false
        _draftType.value = null
    }

    fun setDraftCenter(point: org.osmdroid.util.GeoPoint) { _draftCenter.value = point }
    fun setDraftPointA(point: org.osmdroid.util.GeoPoint) { _draftPointA.value = point }
    fun setDraftPointB(point: org.osmdroid.util.GeoPoint) { _draftPointB.value = point }

    fun saveGeofence(geofence: com.beacon.shared.models.GeofenceZone) {
        viewModelScope.launch {
            geofenceRepository.saveGeofence(geofence)
            cancelCreation()
        }
    }

    fun deleteGeofence(id: String) {
        viewModelScope.launch {
            geofenceRepository.deleteGeofence(id)
        }
    }
}
