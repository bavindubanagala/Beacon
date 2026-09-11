package com.beacon.data.repository

import com.beacon.shared.models.Location
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    suspend fun getLocationsInTimeRange(
        deviceId: String,
        startTime: Long,
        endTime: Long
    ): Result<List<Location>>
    
    suspend fun getRecentLocations(deviceId: String, limit: Int = 100): Result<List<Location>>
}
