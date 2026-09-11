package com.beacon.tracker.location

import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class TrackingProfile {
    HIGH_ACCURACY,
    BALANCED,
    BATTERY_SAVER,
    EMERGENCY_SOS
}

@Singleton
class TrackingProfileManager @Inject constructor() {

    private val _currentProfile = MutableStateFlow(TrackingProfile.BALANCED)
    val currentProfile: StateFlow<TrackingProfile> = _currentProfile.asStateFlow()

    private val _customConfig = MutableStateFlow<LocationRequestConfig?>(null)

    fun setProfile(profile: TrackingProfile) {
        _currentProfile.value = profile
        _customConfig.value = null
    }

    fun setCustomConfig(config: LocationRequestConfig) {
        _customConfig.value = config
    }

    fun getConfig(): LocationRequestConfig {
        _customConfig.value?.let { return it }
        
        return when (_currentProfile.value) {
            TrackingProfile.HIGH_ACCURACY -> LocationRequestConfig(
                intervalMillis = 5000L,
                minUpdateDistanceMeters = 0f,
                priority = Priority.PRIORITY_HIGH_ACCURACY
            )
            TrackingProfile.BALANCED -> LocationRequestConfig(
                intervalMillis = 30000L,
                minUpdateDistanceMeters = 5f,
                priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY
            )
            TrackingProfile.BATTERY_SAVER -> LocationRequestConfig(
                intervalMillis = 300000L,
                minUpdateDistanceMeters = 20f,
                priority = Priority.PRIORITY_LOW_POWER
            )
            TrackingProfile.EMERGENCY_SOS -> LocationRequestConfig(
                intervalMillis = 2000L,
                minUpdateDistanceMeters = 0f,
                priority = Priority.PRIORITY_HIGH_ACCURACY
            )
        }
    }
    
    fun updateProfileFromCommand(mode: String, isEmergency: Boolean) {
        if (isEmergency) {
            setProfile(TrackingProfile.EMERGENCY_SOS)
            return
        }
        
        when (mode.lowercase()) {
            "live", "high_accuracy" -> setProfile(TrackingProfile.HIGH_ACCURACY)
            "balanced" -> setProfile(TrackingProfile.BALANCED)
            "battery_saver", "interval" -> setProfile(TrackingProfile.BATTERY_SAVER)
        }
    }
}
