package com.beacon.admin.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.data.auth.AuthManager
import com.beacon.data.repository.AlertRepository
import com.beacon.data.repository.DeviceRepository
import com.beacon.shared.models.Alert
import com.beacon.shared.models.Device
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardMetrics(
    val totalDevices: Int = 0,
    val onlineDevices: Int = 0,
    val activeSos: Int = 0,
    val lowBatteryCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val alertRepository: AlertRepository,
    private val authManager: AuthManager
) : ViewModel() {

    // Auth-guaranteed devices stream
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

    // Auth-guaranteed alerts stream for the activity feed
    val activeAlerts: StateFlow<List<Alert>> = flow {
        authManager.ensureAuthenticated()
        emit(Unit)
    }.flatMapLatest {
        alertRepository.alerts
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Derived metrics from real-time data
    val metrics: StateFlow<DashboardMetrics> = devices.map { deviceList ->
        DashboardMetrics(
            totalDevices = deviceList.size,
            onlineDevices = deviceList.count { it.status.lowercase() in listOf("online", "live") },
            activeSos = deviceList.count { it.isEmergencyMode },
            lowBatteryCount = deviceList.count { it.batteryLevel < 20 }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())

    fun syncTelemetry() {
        // Implementation can trigger a repository refresh if needed
    }
}
