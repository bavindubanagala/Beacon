package com.beacon.tracker.database

import androidx.room.*

@Dao
interface LocationCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: CachedLocation): Long

    @Update
    suspend fun updateLocation(location: CachedLocation): Int

    @Delete
    suspend fun deleteLocation(location: CachedLocation): Int

    @Query("SELECT * FROM CachedLocation WHERE synced = 0")
    suspend fun getUnSyncedLocations(): List<CachedLocation>

    @Query("SELECT * FROM CachedLocation ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLocations(limit: Int): List<CachedLocation>

    @Query("""
        SELECT * FROM CachedLocation 
        WHERE timestamp BETWEEN :startTime AND :endTime
    """)
    suspend fun getLocationsByTimeRange(
        startTime: Long,
        endTime: Long
    ): List<CachedLocation>

    @Query("DELETE FROM CachedLocation WHERE timestamp < :cutoffTime")
    suspend fun deleteOldLocations(cutoffTime: Long): Int

    @Query("UPDATE CachedLocation SET synced = 1 WHERE id IN (:ids)")
    suspend fun markLocationsSynced(ids: List<Int>): Int

    @Query("DELETE FROM CachedLocation WHERE synced = 1")
    suspend fun clearSyncedLocations(): Int

    @Query("SELECT COUNT(*) FROM CachedLocation WHERE synced = 0")
    suspend fun getUnSyncedCount(): Int
}
