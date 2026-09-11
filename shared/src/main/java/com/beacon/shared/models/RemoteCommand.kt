package com.beacon.shared.models

enum class CommandType {
    FORCE_PING,
    SET_INTERVAL,
    TOGGLE_EMERGENCY_MODE,
    RING_DEVICE
}

data class RemoteCommand(
    val id: String = "",
    val deviceId: String = "",
    val commandType: CommandType = CommandType.FORCE_PING,
    val payload: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING"
)
