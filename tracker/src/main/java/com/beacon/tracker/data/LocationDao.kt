package com.beacon.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationEntity)

    @Query("SELECT * FROM location_logs WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedLocations(): List<LocationEntity>

    @Query("UPDATE location_logs SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)

    @Query("UPDATE location_logs SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markLocationsSynced(ids: List<String>)

    @Query("DELETE FROM location_logs WHERE isSynced = 1 AND timestamp < :threshold")
    suspend fun deleteOldLocationLogs(threshold: Long)
    
    @Query("SELECT COUNT(*) FROM location_logs WHERE isSynced = 0")
    suspend fun getUnsyncedLocationsCount(): Int

    @Query("SELECT * FROM location_logs ORDER BY timestamp DESC LIMIT 1")
    fun getLatestLocation(): Flow<LocationEntity>
}
