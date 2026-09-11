package com.beacon.admin.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.data.repository.AlertRepository
import com.beacon.data.repository.DeviceRepository
import com.beacon.data.auth.AuthManager
import com.beacon.shared.models.Alert
import com.beacon.shared.models.GeofenceEvent
import com.beacon.shared.models.GeofenceEventType
import com.beacon.shared.repository.FirebaseGeofenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GeofenceEventUiModel(
    val event: GeofenceEvent,
    val deviceName: String
)

data class AlertsUiState(
    val alerts: List<Alert> = emptyList(),
    val geofenceEvents: List<GeofenceEventUiModel> = emptyList(),
    val eventTypeFilter: GeofenceEventType? = null,
    val deviceIdFilter: String? = null,
    val isLoading: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
    private val geofenceRepository: FirebaseGeofenceRepository,
    private val deviceRepository: DeviceRepository,
    private val authManager: AuthManager
) : ViewModel() {

    private val _eventTypeFilter = MutableStateFlow<GeofenceEventType?>(null)
    private val _deviceIdFilter = MutableStateFlow<String?>(null)

    val alerts: StateFlow<List<Alert>> = flow {
        authManager.ensureAuthenticated()
        emit(Unit)
    }.flatMapLatest {
        alertRepository.alerts
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val uiState: StateFlow<AlertsUiState> = combine(
        alerts,
        geofenceRepository.getAllGeofenceEvents(),
        deviceRepository.devices,
        _eventTypeFilter,
        _deviceIdFilter
    ) { alerts, events, devices, typeFilter, deviceFilter ->
        val filteredEvents = events.filter { event ->
            (typeFilter == null || event.eventType == typeFilter) &&
            (deviceFilter == null || event.deviceId == deviceFilter)
        }.map { event ->
            val device = devices.find { it.deviceId == event.deviceId }
            GeofenceEventUiModel(event, device?.deviceName ?: "Unknown Device")
        }

        AlertsUiState(
            alerts = alerts,
            geofenceEvents = filteredEvents,
            eventTypeFilter = typeFilter,
            deviceIdFilter = deviceFilter,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AlertsUiState(isLoading = true)
    )

    fun setEventTypeFilter(type: GeofenceEventType?) {
        _eventTypeFilter.value = type
    }

    fun setDeviceFilter(deviceId: String?) {
        _deviceIdFilter.value = deviceId
    }

    fun clearFilters() {
        _eventTypeFilter.value = null
        _deviceIdFilter.value = null
    }

    fun resolveAlert(alertId: String) {
        viewModelScope.launch {
            // Implementation for resolving general alerts if needed
        }
    }
}
