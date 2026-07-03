package com.beacon.shared.models

data class Alert(
    val alert_id: String = "",
    val alert_type: String = "",
    val device_id: String = "",
    val alert_severity: String = "",
    val created_at: Long = 0L
)
