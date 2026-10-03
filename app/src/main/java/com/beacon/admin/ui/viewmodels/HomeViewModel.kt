package com.beacon.admin.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.data.auth.AuthManager
import com.beacon.data.repository.AlertRepository
import com.beacon.admin.ui.utils.tickerFlow
import com.beacon.data.repository.DeviceRepository
import com.beacon.shared.models.Alert
import com.beacon.shared.models.Device
import com.beacon.shared.models.GeofenceEvent
import com.beacon.shared.models.GeofenceEventType
import com.beacon.shared.repository.FirebaseGeofenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ActivityItem(
    val id: String,
    val deviceId: String,
    val deviceName: String,
    val message: String,
    val timestamp: Long,
    val severity: String,
    val type: String
)

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
    private val geofenceRepository: FirebaseGeofenceRepository,
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

    private val geofenceEvents: Flow<List<GeofenceEvent>> = flow {
        authManager.ensureAuthenticated()
        emit(Unit)
    }.flatMapLatest {
        geofenceRepository.getAllGeofenceEvents()
    }

    val recentActivity: StateFlow<List<ActivityItem>> = combine(
        devices,
        activeAlerts,
        geofenceEvents
    ) { deviceList, alertsList, eventsList ->
        val deviceMap = deviceList.associateBy { it.deviceId }

        val alertItems = alertsList
            .filterNot { it.alert_type.contains("GEOFENCE", ignoreCase = true) }
            .map { alert ->
                ActivityItem(
                    id = alert.id,
                    deviceId = alert.device_id,
                    deviceName = alert.device_name,
                    message = alert.message,
                    timestamp = alert.created_at,
                    severity = alert.alert_severity,
                    type = alert.alert_type
                )
            }

        val eventItems = eventsList.map { event ->
            val devName = deviceMap[event.deviceId]?.deviceName ?: "A device"
            val msg = when (event.eventType) {
                GeofenceEventType.ENTER -> "$devName entered ${event.geofenceName}"
                GeofenceEventType.EXIT -> "$devName left ${event.geofenceName}"
                GeofenceEventType.CROSS_A_TO_B -> "$devName crossed ${event.geofenceName} (A to B)"
                GeofenceEventType.CROSS_B_TO_A -> "$devName crossed ${event.geofenceName} (B to A)"
            }
            ActivityItem(
                id = event.id,
                deviceId = event.deviceId,
                deviceName = devName,
                message = msg,
                timestamp = event.timestamp,
                severity = "INFO",
                type = event.eventType.name
            )
        }

        (alertItems + eventItems)
            .sortedByDescending { it.timestamp }
            .take(15)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Derived metrics from real-time data
    val metrics: StateFlow<DashboardMetrics> = combine(devices, tickerFlow(30_000L)) { deviceList, now ->
        DashboardMetrics(
            totalDevices = deviceList.size,
            onlineDevices = deviceList.count { it.isOnlineAt(now) },
            activeSos = deviceList.count { it.isEmergencyMode },
            lowBatteryCount = deviceList.count { it.batteryLevel < 20 }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())

    fun syncTelemetry() {
        // Implementation can trigger a repository refresh if needed
    }
}
