package com.beacon.admin.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.data.auth.AuthManager
import com.beacon.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    // Fleet Defaults
    val defaultTrackingInterval: String = "Balanced",
    val lowBatteryThreshold: Float = 20f,
    val autoEscalateSos: Boolean = true,
    
    // Account
    val adminEmail: String = "",
    val adminRole: String = "Super Admin",
    val lastLogin: String = "September 3, 2026",
    
    // System Status
    val firestoreConnected: Boolean = true,
    val offlineSyncQueueSize: Int = 0,
    
    // App State
    val isSigningOut: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        _uiState.update { it.copy(adminEmail = authManager.getAdminEmail()) }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.getSettings().onSuccess { data ->
                _uiState.update { state ->
                    state.copy(
                        defaultTrackingInterval = data["default_tracking_interval"] as? String ?: "Balanced",
                        lowBatteryThreshold = (data["low_battery_threshold"] as? Number)?.toFloat() ?: 20f,
                        autoEscalateSos = data["auto_escalate_sos"] as? Boolean ?: true
                    )
                }
            }
        }
    }

    fun updateDefaultTrackingInterval(interval: String) {
        _uiState.update { it.copy(defaultTrackingInterval = interval) }
        saveSettings()
    }

    fun updateLowBatteryThreshold(threshold: Float) {
        _uiState.update { it.copy(lowBatteryThreshold = threshold) }
        saveSettings()
    }

    fun toggleAutoEscalateSos(enabled: Boolean) {
        _uiState.update { it.copy(autoEscalateSos = enabled) }
        saveSettings()
    }

    private fun saveSettings() {
        viewModelScope.launch {
            val state = _uiState.value
            settingsRepository.updateSettings(
                mapOf(
                    "default_tracking_interval" to state.defaultTrackingInterval,
                    "low_battery_threshold" to state.lowBatteryThreshold,
                    "auto_escalate_sos" to state.autoEscalateSos
                )
            )
        }
    }

    fun clearMapCache() {
        // Mock implementation
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningOut = true) }
            try {
                authManager.signOut()
                onSignedOut()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSigningOut = false, error = e.localizedMessage ?: "Sign out failed") }
            }
        }
    }
}
