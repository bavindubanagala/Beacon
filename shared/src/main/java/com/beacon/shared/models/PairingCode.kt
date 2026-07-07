package com.beacon.shared.models

data class PairingCode(
    val code: String = "",
    val deviceId: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "code" to code,
        "deviceId" to deviceId,
        "createdAt" to createdAt
    )
}