package com.beacon.shared.models

data class ModeHistoryEntry(
    val id: String = "",
    val mode: String = "",
    val intervalMillis: Long = 0L,
    val changedBy: String = "",
    val timestamp: Long = 0L
)
