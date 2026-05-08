package com.beacon.shared.models

import java.util.Date

data class Alert(
    val alertId: String = "",
    val deviceId: String = "",
    val alertType: AlertType = AlertType.LOW_BATTERY, // "low_battery", "offline", "paused", "weak_signal", "stale_location"
    val severity: AlertSeverity = AlertSeverity.WARNING, // "info", "warning", "critical"
    val message: String = "",
    val createdAt: Date = Date(),
    val resolvedAt: Date? = null,
    val resolvedByAdmin: Boolean = false,
    val data: Map<String, Any?> = emptyMap()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "alert_id" to alertId,
        "device_id" to deviceId,
        "alert_type" to alertType.value,
        "severity" to severity.value,
        "message" to message,
        "created_at" to createdAt,
        "resolved_at" to resolvedAt,
        "resolved_by_admin" to resolvedByAdmin,
        "data" to data
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): Alert {
            return Alert(
                alertId = map["alert_id"] as? String ?: "",
                deviceId = map["device_id"] as? String ?: "",
                alertType = AlertType.fromValue(map["alert_type"] as? String ?: "low_battery"),
                severity = AlertSeverity.fromValue(map["severity"] as? String ?: "warning"),
                message = map["message"] as? String ?: "",
                createdAt = (map["created_at"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                resolvedAt = (map["resolved_at"] as? com.google.firebase.Timestamp)?.toDate(),
                resolvedByAdmin = map["resolved_by_admin"] as? Boolean ?: false,
                data = (map["data"] as? Map<String, Any?>) ?: emptyMap()
            )
        }
    }
}

enum class AlertType(val value: String) {
    LOW_BATTERY("low_battery"),
    OFFLINE("offline"),
    PAUSED("paused"),
    WEAK_SIGNAL("weak_signal"),
    STALE_LOCATION("stale_location");

    companion object {
        fun fromValue(value: String): AlertType {
            return when (value) {
                "low_battery" -> LOW_BATTERY
                "offline" -> OFFLINE
                "paused" -> PAUSED
                "weak_signal" -> WEAK_SIGNAL
                "stale_location" -> STALE_LOCATION
                else -> LOW_BATTERY
            }
        }
    }
}

enum class AlertSeverity(val value: String) {
    INFO("info"),
    WARNING("warning"),
    CRITICAL("critical");

    companion object {
        fun fromValue(value: String): AlertSeverity {
            return when (value) {
                "info" -> INFO
                "warning" -> WARNING
                "critical" -> CRITICAL
                else -> WARNING
            }
        }
    }
}
