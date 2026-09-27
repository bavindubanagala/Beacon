package com.beacon.tracker.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ManualSyncResult(val requestId: String, val success: Boolean, val message: String)

object ManualSyncBus {
    private val _results = MutableSharedFlow<ManualSyncResult>(extraBufferCapacity = 4)
    val results = _results.asSharedFlow()

    suspend fun report(result: ManualSyncResult) {
        _results.emit(result)
    }
}
