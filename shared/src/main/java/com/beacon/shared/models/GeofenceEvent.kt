package com.beacon.shared.models

enum class GeofenceEventType {
    ENTER,
    EXIT,
    CROSS_A_TO_B,
    CROSS_B_TO_A
}

data class GeofenceEvent(
    val id: String = "",
    val geofenceId: String = "",
    val geofenceName: String = "",
    val deviceId: String = "",
    val eventType: GeofenceEventType = GeofenceEventType.ENTER,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)
