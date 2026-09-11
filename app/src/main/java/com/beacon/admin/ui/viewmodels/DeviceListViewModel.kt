package com.beacon.admin.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.data.auth.AuthManager
import com.beacon.data.repository.DeviceRepository
import com.beacon.shared.models.DeviceStatusLight
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceItemState(
    val id: String,
    val name: String,
    val status: DeviceStatusLight,
    val batteryPercentage: Int,
    val isPowerSaveMode: Boolean,
    val lastPing: String,
    val activePreset: String,
    val isSelected: Boolean = false
)

/**
 * 1. UI State model explicitly capturing security/auth errors via sealed interface.
 */
sealed interface DeviceListUiState {
    data object Loading : DeviceListUiState
    
    data class Success(
        val searchQuery: String = "",
        val selectedFilterTab: Int = 0, // 0: All, 1: Active, 2: Standby, 3: Offline
        val devices: List<DeviceItemState> = emptyList(),
        val filteredDevices: List<DeviceItemState> = emptyList()
    ) : DeviceListUiState

    data class Error(
        val message: String,
        val isAuthError: Boolean = false,
        val isPermissionDenied: Boolean = false
    ) : DeviceListUiState
}

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<DeviceListUiState>(DeviceListUiState.Loading)
    val uiState: StateFlow<DeviceListUiState> = _uiState.asStateFlow()

    init {
        observeDevices()
    }

    private fun observeDevices() {
        viewModelScope.launch {
            // Repositories are auth-guarded. We collect and map security/exception states.
            deviceRepository.devices
                .catch { e ->
                    // 2. Map Flow errors directly to specific UI state
                    val isAuth = e is FirebaseFirestoreException && 
                        e.code == FirebaseFirestoreException.Code.UNAUTHENTICATED
                    
                    val isPermission = e is FirebaseFirestoreException && 
                        e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    
                    _uiState.value = DeviceListUiState.Error(
                        message = e.localizedMessage ?: "Unknown monitoring error",
                        isAuthError = isAuth,
                        isPermissionDenied = isPermission
                    )
                }
                .collectLatest { deviceList ->
                    val deviceStates = deviceList.map { device ->
                        DeviceItemState(
                            id = device.deviceId,
                            name = device.deviceName,
                            status = device.statusLight,
                            batteryPercentage = device.batteryLevel,
                            isPowerSaveMode = device.batterySavingEnabled,
                            lastPing = "Just now",
                            activePreset = device.trackingMode
                        )
                    }
                    
                    _uiState.update { currentState ->
                        val currentSuccess = currentState as? DeviceListUiState.Success ?: DeviceListUiState.Success()
                        currentSuccess.copy(
                            devices = deviceStates,
                            filteredDevices = filterDevices(deviceStates, currentSuccess.searchQuery, currentSuccess.selectedFilterTab)
                        )
                    }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { currentState ->
            val currentSuccess = currentState as? DeviceListUiState.Success ?: return@update currentState
            val updated = currentSuccess.copy(searchQuery = query)
            updated.copy(filteredDevices = filterDevices(updated.devices, query, updated.selectedFilterTab))
        }
    }

    fun onFilterTabSelected(index: Int) {
        _uiState.update { currentState ->
            val currentSuccess = currentState as? DeviceListUiState.Success ?: return@update currentState
            val updated = currentSuccess.copy(selectedFilterTab = index)
            updated.copy(filteredDevices = filterDevices(updated.devices, updated.searchQuery, index))
        }
    }

    private fun filterDevices(
        list: List<DeviceItemState>,
        query: String,
        tabIndex: Int
    ): List<DeviceItemState> {
        return list.filter { device ->
            val matchesQuery = device.name.contains(query, ignoreCase = true)
            val matchesTab = when (tabIndex) {
                1 -> device.status == DeviceStatusLight.GREEN_LIVE || device.status == DeviceStatusLight.BLUE_INTERVAL
                2 -> device.status == DeviceStatusLight.YELLOW_IDLE
                3 -> device.status == DeviceStatusLight.RED_OFFLINE
                else -> true // All
            }
            matchesQuery && matchesTab
        }
    }
}
