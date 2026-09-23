package com.example.beaconadmin.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beaconadmin.data.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class UnpairUiState {
    object Idle : UnpairUiState()
    object Loading : UnpairUiState()
    object Success : UnpairUiState()
    data class Error(val message: String) : UnpairUiState()
}

class DeviceDetailViewModel(
    private val repository: DeviceRepository = DeviceRepository()
) : ViewModel() {

    private val _unpairState = MutableStateFlow<UnpairUiState>(UnpairUiState.Idle)
    val unpairState: StateFlow<UnpairUiState> = _unpairState.asStateFlow()

    fun unpairDevice(deviceId: String) {
        viewModelScope.launch {
            _unpairState.value = UnpairUiState.Loading
            val result = repository.unpairDevice(deviceId)
            result.fold(
                onSuccess = { _unpairState.value = UnpairUiState.Success },
                onFailure = { error ->
                    _unpairState.value = UnpairUiState.Error(error.message ?: "Failed to unpair device.")
                }
            )
        }
    }

    fun resetUnpairState() {
        _unpairState.value = UnpairUiState.Idle
    }
}
