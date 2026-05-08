package com.beacon.shared.models

import java.util.Date

data class Group(
    val group_id: String = "",
    val name: String = "",
    val device_ids: List<String> = emptyList(),
    val created_at: Date = Date(),
    val icon_color: String = "#2196F3",
    val order: Int = 0
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "group_id" to group_id,
        "name" to name,
        "device_ids" to device_ids,
        "created_at" to created_at,
        "icon_color" to icon_color,
        "order" to order
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): Group {
            return Group(
                group_id = map["group_id"] as? String ?: "",
                name = map["name"] as? String ?: "",
                device_ids = (map["device_ids"] as? List<String>) ?: emptyList(),
                created_at = (map["created_at"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                icon_color = map["icon_color"] as? String ?: "#2196F3",
                order = (map["order"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

data class AdminSettings(
    val email: String = "",
    val created_at: Date = Date(),
    val default_tracking_interval: Long = 60,
    val alert_thresholds: AlertThresholds = AlertThresholds()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "email" to email,
        "created_at" to created_at,
        "default_tracking_interval" to default_tracking_interval,
        "alert_thresholds" to alert_thresholds.toMap()
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): AdminSettings {
            return AdminSettings(
                email = map["email"] as? String ?: "",
                created_at = (map["created_at"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                default_tracking_interval = (map["default_tracking_interval"] as? Number)?.toLong() ?: 60,
                alert_thresholds = AlertThresholds.fromMap(map["alert_thresholds"] as? Map<String, Any?> ?: emptyMap())
            )
        }
    }
}

