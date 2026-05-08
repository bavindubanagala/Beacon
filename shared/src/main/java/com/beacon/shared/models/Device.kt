package com.beacon.shared.models

import java.util.Date

data class Device(
    val deviceId: String = "",
    val groupId: String = "",
    val iconId: String = "phone",
    val createdAt: Date = Date(),
    val lastLocation: LocationData = LocationData(),
    val batteryLevel: Int = 0,
    val batteryIsCharging: Boolean = false,
    val signalStrength: Int = 0,
    val lastSeen: Date = Date(),
    val status: DeviceStatus = DeviceStatus.ONLINE, // "online", "offline", "paused"
    val trackingEnabled: Boolean = true,
    val trackingInterval: Int = 60, // seconds
    val deviceMotionStatus: String = "idle", // "moving", "idle", "stationary"
    val perDeviceSettings: PerDeviceSettings = PerDeviceSettings(),
    val metadata: DeviceMetadata = DeviceMetadata()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "device_id" to deviceId,
        "group_id" to groupId,
        "icon_id" to iconId,
        "created_at" to createdAt,
        "last_location" to lastLocation.toMap(),
        "battery_level" to batteryLevel,
        "battery_is_charging" to batteryIsCharging,
        "signal_strength" to signalStrength,
        "last_seen" to lastSeen,
        "status" to status.value,
        "tracking_enabled" to trackingEnabled,
        "tracking_interval" to trackingInterval,
        "device_motion_status" to deviceMotionStatus,
        "per_device_settings" to perDeviceSettings.toMap(),
        "metadata" to metadata.toMap()
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): Device {
            return Device(
                deviceId = map["device_id"] as? String ?: "",
                groupId = map["group_id"] as? String ?: "",
                iconId = map["icon_id"] as? String ?: "phone",
                createdAt = (map["created_at"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                lastLocation = LocationData.fromMap(map["last_location"] as? Map<String, Any?> ?: emptyMap()),
                batteryLevel = (map["battery_level"] as? Number)?.toInt() ?: 0,
                batteryIsCharging = map["battery_is_charging"] as? Boolean ?: false,
                signalStrength = (map["signal_strength"] as? Number)?.toInt() ?: 0,
                lastSeen = (map["last_seen"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                status = DeviceStatus.fromValue(map["status"] as? String ?: "online"),
                trackingEnabled = map["tracking_enabled"] as? Boolean ?: true,
                trackingInterval = (map["tracking_interval"] as? Number)?.toInt() ?: 60,
                deviceMotionStatus = map["device_motion_status"] as? String ?: "idle",
                perDeviceSettings = PerDeviceSettings.fromMap(map["per_device_settings"] as? Map<String, Any?> ?: emptyMap()),
                metadata = DeviceMetadata.fromMap(map["metadata"] as? Map<String, Any?> ?: emptyMap())
            )
        }
    }
}

enum class DeviceStatus(val value: String) {
    ONLINE("online"),
    OFFLINE("offline"),
    PAUSED("paused");

    companion object {
        fun fromValue(value: String): DeviceStatus {
            return when (value) {
                "online" -> ONLINE
                "offline" -> OFFLINE
                "paused" -> PAUSED
                else -> ONLINE
            }
        }
    }
}

data class LocationData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val timestamp: Date = Date(),
    val provider: String = "gps" // "gps" or "network"
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "latitude" to latitude,
        "longitude" to longitude,
        "accuracy" to accuracy,
        "timestamp" to timestamp,
        "provider" to provider
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): LocationData {
            return LocationData(
                latitude = (map["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (map["longitude"] as? Number)?.toDouble() ?: 0.0,
                accuracy = (map["accuracy"] as? Number)?.toFloat() ?: 0f,
                timestamp = (map["timestamp"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                provider = map["provider"] as? String ?: "gps"
            )
        }
    }
}

data class PerDeviceSettings(
    val trackingEnabled: Boolean = true,
    val trackingInterval: Int = 60,
    val notificationsEnabled: Boolean = true,
    val locationAccuracyLevel: String = "high", // "high", "medium", "low"
    val alertThresholds: AlertThresholds = AlertThresholds()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "tracking_enabled" to trackingEnabled,
        "tracking_interval" to trackingInterval,
        "notifications_enabled" to notificationsEnabled,
        "location_accuracy_level" to locationAccuracyLevel,
        "alert_thresholds" to alertThresholds.toMap()
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): PerDeviceSettings {
            return PerDeviceSettings(
                trackingEnabled = map["tracking_enabled"] as? Boolean ?: true,
                trackingInterval = (map["tracking_interval"] as? Number)?.toInt() ?: 60,
                notificationsEnabled = map["notifications_enabled"] as? Boolean ?: true,
                locationAccuracyLevel = map["location_accuracy_level"] as? String ?: "high",
                alertThresholds = AlertThresholds.fromMap(map["alert_thresholds"] as? Map<String, Any?> ?: emptyMap())
            )
        }
    }
}

data class AlertThresholds(
    val lowBattery: Int = 15,
    val weakSignal: Int = 20,
    val offlineThresholdMinutes: Int = 10
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "low_battery" to lowBattery,
        "weak_signal" to weakSignal,
        "offline_threshold_minutes" to offlineThresholdMinutes
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): AlertThresholds {
            return AlertThresholds(
                lowBattery = (map["low_battery"] as? Number)?.toInt() ?: 15,
                weakSignal = (map["weak_signal"] as? Number)?.toInt() ?: 20,
                offlineThresholdMinutes = (map["offline_threshold_minutes"] as? Number)?.toInt() ?: 10
            )
        }
    }
}

data class DeviceMetadata(
    val appVersion: String = "",
    val osVersion: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "app_version" to appVersion,
        "os_version" to osVersion
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): DeviceMetadata {
            return DeviceMetadata(
                appVersion = map["app_version"] as? String ?: "",
                osVersion = map["os_version"] as? String ?: ""
            )
        }
    }
}
