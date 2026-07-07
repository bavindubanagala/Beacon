package com.beacon.shared.models

data class Alert(
    val id: String = "",
    val alert_type: String = "", // LOW_BATTERY, OFFLINE, GEOFENCE
    val device_id: String = "",
    val device_name: String = "",
    val alert_severity: String = "INFO", // INFO, WARNING, CRITICAL
    val message: String = "",
    val created_at: Long = System.currentTimeMillis(),
    val is_read: Boolean = false
)
