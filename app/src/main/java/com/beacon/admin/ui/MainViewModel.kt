package com.beacon.admin.ui

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.admin.repository.AdminLocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val adminLocationRepository: AdminLocationRepository
) : ViewModel() {

    // Expose location as a StateFlow, shared while subscribed, caching the last emission
    val adminLocation: StateFlow<Location?> = adminLocationRepository.observeAdminLocation()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}
