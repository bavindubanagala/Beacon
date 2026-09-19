package com.beacon.admin.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.data.repository.DeviceRepository
import com.beacon.data.repository.LocationRepository
import com.beacon.shared.models.Device
import com.beacon.shared.models.Location
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.*

data class HistoryUiState(
    val deviceId: String = "",
    val devices: List<Device> = emptyList(),
    val isLoading: Boolean = false,
    val locations: List<Location> = emptyList(),
    val selectedDate: Calendar = Calendar.getInstance(),
    val errorMessage: String? = null,
    
    // Playback state
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1f,
    val playbackIndex: Int = 0,
    
    // Telemetry metrics
    val totalDistanceKm: Double = 0.0,
    val averageSpeedKph: Double = 0.0,
    val maxSpeedKph: Double = 0.0,
    val batteryDischarge: Int = 0,
    val durationMinutes: Long = 0
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val deviceRepository: DeviceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        HistoryUiState()
    )
    
    val availableDevices: StateFlow<List<Device>> = deviceRepository.devices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private var playbackJob: Job? = null

    init {
        val deviceId: String? = savedStateHandle["deviceId"]
        val timestamp: Long? = savedStateHandle["ts"]
        
        if (deviceId != null) {
            _uiState.update { state ->
                state.copy(
                    deviceId = deviceId,
                    selectedDate = if (timestamp != null && timestamp > 0) {
                        Calendar.getInstance().apply { timeInMillis = timestamp }
                    } else state.selectedDate
                )
            }
            loadHistory()
        }
    }

    fun onDeviceSelected(deviceId: String) {
        _uiState.update { it.copy(deviceId = deviceId) }
        loadHistory()
    }

    fun onDateSelected(calendar: Calendar) {
        _uiState.update { it.copy(selectedDate = calendar) }
        loadHistory()
    }

    fun togglePlayback() {
        if (_uiState.value.isPlaying) pausePlayback() else startPlayback()
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun seekTo(index: Int) {
        _uiState.update { it.copy(playbackIndex = index.coerceIn(0, it.locations.size - 1)) }
    }

    private fun startPlayback() {
        if (_uiState.value.locations.isEmpty()) return
        _uiState.update { it.copy(isPlaying = true) }
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var index = _uiState.value.playbackIndex
            if (index >= _uiState.value.locations.size - 1) index = 0
            while (index < _uiState.value.locations.size) {
                _uiState.update { it.copy(playbackIndex = index) }
                delay((1000 / _uiState.value.playbackSpeed).toLong())
                index++
            }
            _uiState.update { it.copy(isPlaying = false) }
        }
    }

    private fun pausePlayback() {
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun loadHistory() {
        val state = _uiState.value
        if (state.deviceId.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, playbackIndex = 0) }
            val start = (state.selectedDate.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val end = (state.selectedDate.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }

            val result = locationRepository.getLocationsInTimeRange(state.deviceId, start.timeInMillis, end.timeInMillis)
            if (result.isSuccess) {
                val locations = result.getOrDefault(emptyList()).sortedBy { it.timestamp }
                val metrics = calculateMetrics(locations)
                _uiState.update { it.copy(
                    locations = locations, isLoading = false,
                    totalDistanceKm = metrics.totalDistance, averageSpeedKph = metrics.avgSpeed,
                    maxSpeedKph = metrics.maxSpeed, batteryDischarge = metrics.batteryDischarge,
                    durationMinutes = metrics.durationMinutes
                ) }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = result.exceptionOrNull()?.message ?: "Error") }
            }
        }
    }

    private fun calculateMetrics(locations: List<Location>): Metrics {
        if (locations.size < 2) return Metrics()
        var totalDist = 0.0; var maxSpeed = 0f; var sumSpeed = 0f
        for (i in 0 until locations.size - 1) {
            totalDist += haversine(locations[i].latitude, locations[i].longitude, locations[i+1].latitude, locations[i+1].longitude)
            maxSpeed = max(maxSpeed, locations[i+1].speed); sumSpeed += locations[i+1].speed
        }
        return Metrics(
            totalDistance = totalDist, avgSpeed = (sumSpeed / (locations.size - 1)) * 3.6,
            maxSpeed = maxSpeed * 3.6, batteryDischarge = (locations.first().batteryLevel - locations.last().batteryLevel).coerceAtLeast(0),
            durationMinutes = (locations.last().timestamp - locations.first().timestamp) / 60000
        )
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0; val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private data class Metrics(val totalDistance: Double = 0.0, val avgSpeed: Double = 0.0, val maxSpeed: Double = 0.0, val batteryDischarge: Int = 0, val durationMinutes: Long = 0)
}
