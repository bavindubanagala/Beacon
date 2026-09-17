package com.beacon.admin.ui.utils

import androidx.compose.ui.graphics.Color
import com.beacon.shared.models.DeviceStatus
import java.util.concurrent.TimeUnit

fun getStatusUiConfig(status: DeviceStatus): Pair<Color, String> {
    return when (status) {
        DeviceStatus.GREEN_LIVE -> Color(0xFF00E676) to "Real-Time Live"
        DeviceStatus.BLUE_INTERVAL -> Color(0xFF00B0FF) to "Interval Tracking"
        DeviceStatus.YELLOW_IDLE -> Color(0xFFFFD600) to "Idle & Reachable"
        DeviceStatus.RED_OFFLINE -> Color(0xFFFF5252) to "Offline / Unreachable"
        DeviceStatus.GRAY_UNPAIRED -> Color(0xFF8E8E93) to "Unpaired"
        else -> Color(0xFF8E8E93) to "Unknown"
    }
}

fun formatRelativeSyncTime(timestamp: Long): String {
    if (timestamp == 0L) return "No recent sync"
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    if (diffMs < 0) return "Updated just now"
    
    val seconds = TimeUnit.MILLISECONDS.toSeconds(diffMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
    val days = TimeUnit.MILLISECONDS.toDays(diffMs)

    return when {
        seconds < 60 -> "Updated ${seconds}s ago"
        minutes < 60 -> "Updated ${minutes}m ago"
        hours < 24 -> "Updated ${hours}h ago"
        else -> "Updated ${days}d ago"
    }
}
