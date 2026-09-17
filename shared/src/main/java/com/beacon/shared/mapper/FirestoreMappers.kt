package com.beacon.shared.mapper

import android.util.Log
import com.beacon.shared.models.Alert
import com.beacon.shared.models.Device
import com.beacon.shared.models.DeviceGroup
import com.google.firebase.firestore.DocumentSnapshot

// ALERT
fun DocumentSnapshot.toAlert(): Alert {
    return Alert(
        id = id,
        alert_type = getString("alert_type") ?: "",
        device_id = getString("device_id") ?: "",
        device_name = getString("device_name") ?: getString("deviceName") ?: "Unknown",
        alert_severity = getString("alert_severity") ?: "INFO",
        message = getString("message") ?: "",
        created_at = (get("created_at") as? Number)?.toLong() ?: System.currentTimeMillis(),
        is_read = getBoolean("is_read") ?: false
    )
}

// DEVICE
fun DocumentSnapshot.toDevice(): Device {
    val data = data ?: emptyMap<String, Any>()
    Log.d("MapperDiagnostic", "Raw lat: ${data["latitude"]} (${data["latitude"]?.javaClass?.simpleName}), Raw lng: ${data["longitude"]} (${data["longitude"]?.javaClass?.simpleName})")
    
    val lat = (data["latitude"] as? Number)?.toDouble()
        ?: (data["last_location"] as? Map<*, *>)?.get("latitude")?.let { (it as? Number)?.toDouble() }
        ?: 0.0

    val lng = (data["longitude"] as? Number)?.toDouble()
        ?: (data["last_location"] as? Map<*, *>)?.get("longitude")?.let { (it as? Number)?.toDouble() }
        ?: 0.0

    val accuracy = (data["accuracy"] as? Number)?.toFloat()
        ?: (data["last_location"] as? Map<*, *>)?.get("accuracy")?.let { (it as? Number)?.toFloat() }
        ?: 0f

    val thresholdsMap = data["alertThresholds"] as? Map<*, *>
    val alertThresholds = com.beacon.shared.models.AlertThresholds(
        lowBatteryPercent = (thresholdsMap?.get("lowBatteryPercent") as? Number)?.toInt() ?: 15,
        offlineThresholdMinutes = (thresholdsMap?.get("offlineThresholdMinutes") as? Number)?.toInt() ?: 10,
        speedLimitKmH = (thresholdsMap?.get("speedLimitKmH") as? Number)?.toInt() ?: 0,
        isCrashDetectionEnabled = thresholdsMap?.get("isCrashDetectionEnabled") as? Boolean ?: true,
        isShockAlertEnabled = thresholdsMap?.get("isShockAlertEnabled") as? Boolean ?: false
    )

    return Device(
        deviceId = getString("deviceId") ?: getString("device_id") ?: "",
        deviceName = getString("deviceName") ?: getString("device_name") ?: "New Device",
        batteryLevel = ((get("batteryLevel") ?: get("battery_level")) as? Number)?.toInt() ?: 0,
        status = getString("status") ?: "paired",
        latitude = lat,
        longitude = lng,
        speed = ((get("speed") ?: get("last_speed")) as? Number)?.toFloat() ?: 0f,
        signalStrength = ((get("signal_strength") ?: get("signalStrength")) as? Number)?.toInt() ?: 0,
        ownerId = getString("ownerId") ?: getString("owner_id") ?: "",
        trackerAuthUid = getString("trackerAuthUid") ?: "",
        groupId = getString("groupId") ?: getString("group_id") ?: "",
        is_paired = getBoolean("is_paired") ?: false,
        trackingMode = getString("trackingMode") ?: getString("tracking_mode") ?: "interval",
        intervalSeconds = ((get("intervalSeconds") ?: get("interval_seconds")) as? Number)?.toInt() ?: 900,
        autoRevertSeconds = ((get("autoRevertSeconds") ?: get("auto_revert_seconds")) as? Number)?.toInt() ?: 1800,
        isEmergencyMode = getBoolean("isEmergencyMode") ?: getBoolean("is_emergency_mode") ?: false,
        batterySavingEnabled = getBoolean("batterySavingEnabled") ?: getBoolean("battery_saving_enabled") ?: true,
        stationaryIntervalMinutes = ((get("stationaryIntervalMinutes") ?: get("stationary_interval_minutes")) as? Number)?.toInt() ?: 45,
        commandMode = getString("commandMode") ?: getString("command_mode") ?: "",
        commandTimestamp = ((get("commandTimestamp") ?: get("command_timestamp")) as? Number)?.toLong() ?: 0L,
        accuracy = accuracy,
        alertThresholds = alertThresholds,
        lastSeenTimestamp = ((get("lastSeenTimestamp") ?: get("last_seen")) as? Number)?.toLong() ?: 0L
    )
}

fun DocumentSnapshot.toGroup(): DeviceGroup {
    return DeviceGroup(
        id = id,
        name = getString("name") ?: "",
        deviceIds = (get("device_ids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    )
}
