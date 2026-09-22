package com.beacon.tracker.ui

import com.beacon.tracker.data.LocationEntity

sealed interface TrackerUiState {
    data object Loading : TrackerUiState
    data class Success(val location: LocationEntity? = null) : TrackerUiState
    data class Error(val message: String) : TrackerUiState
}
