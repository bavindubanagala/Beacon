package com.beacon.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.data.repository.AdminLocationRepository
import com.beacon.shared.repository.FirebaseGeofenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import android.location.Location
import javax.inject.Inject

enum class AdminTab(val route: String, val title: String) {
    MAP("map", "Map"),
    DEVICES("devices", "Devices"),
    HOME("home", "Home"),
    NOTIFICATIONS("notifications", "History"),
    SETTINGS("settings", "Settings")
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val adminLocationRepository: AdminLocationRepository,
    private val geofenceRepository: FirebaseGeofenceRepository
) : ViewModel() {

    private val _lastSeenTimestamp = MutableStateFlow(System.currentTimeMillis())

    val unreadGeofenceAlertCount: StateFlow<Int> = combine(
        geofenceRepository.getAllGeofenceEvents(),
        _lastSeenTimestamp
    ) { events, lastSeen ->
        events.count { it.timestamp > lastSeen }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    fun markAlertsAsRead() {
        _lastSeenTimestamp.value = System.currentTimeMillis()
    }

    val adminLocation: StateFlow<Location?> = adminLocationRepository.observeAdminLocation()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _scrollToTopEvents = MutableSharedFlow<AdminTab>()
    val scrollToTopEvents: SharedFlow<AdminTab> = _scrollToTopEvents.asSharedFlow()

    fun triggerScrollToTop(tab: AdminTab) {
        viewModelScope.launch {
            _scrollToTopEvents.emit(tab)
        }
    }
}
