package com.beacon.shared.models

data class Fence(
    val id: String = "",
    val name: String = "",
    val type: String = "zone", // "zone" or "checkpoint"
    val centerLat: Double = 0.0,
    val centerLng: Double = 0.0,
    val radiusMeters: Double = 100.0,
    val assignedDeviceIds: List<String> = emptyList(),
    // Zone specific
    val alertOnEnter: Boolean = true,
    val alertOnExit: Boolean = true,
    // Checkpoint specific
    val alertFrequency: String = "every_time" // "every_time", "once_ever", "once_per_day"
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "type" to type,
        "centerLat" to centerLat,
        "centerLng" to centerLng,
        "radiusMeters" to radiusMeters,
        "assignedDeviceIds" to assignedDeviceIds,
        "alertOnEnter" to alertOnEnter,
        "alertOnExit" to alertOnExit,
        "alertFrequency" to alertFrequency
    )
}
