package com.beacon.admin.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.admin.repository.FirebaseGroupRepository
import com.beacon.data.repository.DeviceRepository
import com.beacon.shared.models.Device
import com.beacon.shared.models.DeviceGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceUiModel(
    val device: Device,
    val groupName: String = "Assign Group",
    val groupColor: String = "#808080"
)

data class DeviceGroupsUiState(
    val selectedGroupId: String? = null,
    val groups: List<DeviceGroup> = emptyList(),
    val filteredDevices: List<DeviceUiModel> = emptyList(),
    val isCreatingGroup: Boolean = false
)

@HiltViewModel
class DeviceGroupsViewModel @Inject constructor(
    private val groupRepository: FirebaseGroupRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    private val _isCreatingGroup = MutableStateFlow(false)

    val uiState: StateFlow<DeviceGroupsUiState> = combine(
        _selectedGroupId,
        groupRepository.getGroups(),
        deviceRepository.devices,
        _isCreatingGroup
    ) { selectedId, groups, devices, isCreating ->
        val filteredDevices = devices.filter { device ->
            selectedId == null || groups.find { it.id == selectedId }?.deviceIds?.contains(device.deviceId) == true
        }.map { device ->
            val group = groups.find { it.deviceIds.contains(device.deviceId) }
            DeviceUiModel(
                device = device,
                groupName = group?.name ?: "Assign Group",
                groupColor = group?.colorHex ?: "#808080"
            )
        }

        DeviceGroupsUiState(
            selectedGroupId = selectedId,
            groups = groups,
            filteredDevices = filteredDevices,
            isCreatingGroup = isCreating
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceGroupsUiState()
    )

    fun selectGroup(groupId: String?) {
        _selectedGroupId.value = groupId
    }

    fun createGroup(name: String, colorHex: String, description: String) {
        viewModelScope.launch {
            _isCreatingGroup.value = true
            groupRepository.createGroup(
                DeviceGroup(name = name, colorHex = colorHex, description = description)
            )
            _isCreatingGroup.value = false
        }
    }

    fun updateGroup(group: DeviceGroup) {
        viewModelScope.launch {
            groupRepository.updateGroup(group)
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            groupRepository.deleteGroup(groupId)
        }
    }

    fun assignDeviceToGroup(deviceId: String, groupId: String) {
        viewModelScope.launch {
            // Remove from current group first if any (simplified for this task)
            val currentGroup = uiState.value.groups.find { it.deviceIds.contains(deviceId) }
            if (currentGroup != null && currentGroup.id != groupId) {
                groupRepository.removeDeviceFromGroup(deviceId, currentGroup.id)
            }
            groupRepository.assignDeviceToGroup(deviceId, groupId)
        }
    }

    fun unassignDevice(deviceId: String) {
        viewModelScope.launch {
            val currentGroup = uiState.value.groups.find { it.deviceIds.contains(deviceId) }
            if (currentGroup != null) {
                groupRepository.removeDeviceFromGroup(deviceId, currentGroup.id)
            }
        }
    }
}
