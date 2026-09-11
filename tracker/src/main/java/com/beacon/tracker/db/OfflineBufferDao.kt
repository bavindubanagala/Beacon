package com.beacon.tracker.db

import androidx.room.*

@Dao
interface OfflineBufferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: OfflineLocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeofenceEvent(event: OfflineGeofenceEventEntity)

    @Query("SELECT * FROM offline_locations ORDER BY timestamp ASC")
    suspend fun getUnsyncedLocations(): List<OfflineLocationEntity>

    @Query("SELECT * FROM offline_geofence_events ORDER BY timestamp ASC")
    suspend fun getUnsyncedGeofenceEvents(): List<OfflineGeofenceEventEntity>

    @Query("DELETE FROM offline_locations WHERE id IN (:ids)")
    suspend fun deleteLocationsByIds(ids: List<String>)

    @Query("DELETE FROM offline_geofence_events WHERE id IN (:ids)")
    suspend fun deleteGeofenceEventsByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM offline_locations")
    suspend fun getUnsyncedLocationsCount(): Int

    @Query("SELECT COUNT(*) FROM offline_geofence_events")
    suspend fun getUnsyncedGeofenceEventsCount(): Int
}
