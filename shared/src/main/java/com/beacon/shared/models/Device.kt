package com.beacon.shared.models

data class Device(
    val deviceId: String = "",
    val deviceName: String = "",
    val batteryLevel: Int = 0,
    val status: String = "offline",
    val groupId: String? = null
)
