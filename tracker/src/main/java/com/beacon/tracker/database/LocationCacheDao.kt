package com.beacon.tracker.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface LocationCacheDao {
    @Insert
    suspend fun insertLocation(location: CachedLocation)

    @Update
    suspend fun updateLocation(location: CachedLocation)

    @Delete
    suspend fun deleteLocation(location: CachedLocation)

    @Query("SELECT * FROM cached_locations WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnSyncedLocations(): List<CachedLocation>

    @Query("SELECT * FROM cached_locations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLocations(limit: Int = 100): List<CachedLocation>

    @Query("SELECT * FROM cached_locations WHERE timestamp > :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getLocationsByTimeRange(startTime: Long, endTime: Long): List<CachedLocation>

    @Query("DELETE FROM cached_locations WHERE timestamp < :cutoffTime")
    suspend fun deleteOldLocations(cutoffTime: Long)

    @Query("UPDATE cached_locations SET synced = 1 WHERE id IN (:ids)")
    suspend fun markLocationsSynced(ids: List<Int>)

    @Query("DELETE FROM cached_locations WHERE synced = 1")
    suspend fun clearSyncedLocations()

    @Query("SELECT COUNT(*) FROM cached_locations WHERE synced = 0")
    suspend fun getUnSyncedCount(): Int
}
