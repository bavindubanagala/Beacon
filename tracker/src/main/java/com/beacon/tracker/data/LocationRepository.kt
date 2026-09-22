package com.beacon.tracker.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val locationDao: LocationDao
) {
    fun getLatestLocation(): Flow<LocationEntity?> {
        return locationDao.getLatestLocation()
    }
}
