package com.beacon.shared.mapper

import com.beacon.shared.models.Alert
import com.beacon.shared.models.Device
import com.beacon.shared.models.Group
import com.google.firebase.firestore.DocumentSnapshot

// ALERT
fun DocumentSnapshot.toAlert(): Alert {
    return Alert(
        alert_type = getString("alert_type") ?: "",
        device_id = getString("device_id") ?: "",
        alert_severity = getString("alert_severity") ?: "",
        created_at = getLong("created_at") ?: 0L
    )
}

// DEVICE
fun DocumentSnapshot.toDevice(): Device {
    return Device(
        deviceId = getString("device_id") ?: "",
        deviceName = getString("device_name") ?: "",
        batteryLevel = (getLong("battery_level") ?: 0L).toInt(),
        status = getString("status") ?: "offline",
        groupId = getString("group_id")
    )
}

fun DocumentSnapshot.toGroup(): Group {
    return Group(
        groupId = id,
        name = getString("name") ?: "",
        deviceIds = (get("device_ids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    )
}
