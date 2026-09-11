package com.beacon.admin.ui.geofence

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.shared.models.GeofenceZone
import com.beacon.shared.repository.FirebaseGeofenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GeofenceUiState(
    val zones: List<GeofenceZone> = emptyList(),
    val sosAlerts: List<Map<String, Any>> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val targetDeviceId: String? = null,
    val isCreationMode: Boolean = false
)

@HiltViewModel
class GeofenceViewModel @Inject constructor(
    private val repository: FirebaseGeofenceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeofenceUiState())
    val uiState = _uiState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GeofenceUiState())

    init {
        val deviceId: String? = savedStateHandle["deviceId"]
        if (deviceId != null) {
            _uiState.update { it.copy(targetDeviceId = deviceId, isCreationMode = deviceId == "create") }
        }
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Observe repository streams
        }
    }

    fun saveZone(zone: GeofenceZone) {
        viewModelScope.launch {
            repository.saveGeofence(zone)
        }
    }

    fun deleteZone(zoneId: String) {
        viewModelScope.launch {
            repository.deleteGeofence(zoneId)
        }
    }

    fun resolveSos(sosId: String) {
        viewModelScope.launch {
            // Resolve SOS
        }
    }
}
