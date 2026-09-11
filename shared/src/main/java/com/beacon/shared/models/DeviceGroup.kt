package com.beacon.shared.models

data class DeviceGroup(
    val id: String = "",
    val name: String = "",
    val colorHex: String = "#000000",
    val description: String = "",
    val deviceIds: List<String> = emptyList()
)
