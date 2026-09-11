package com.beacon.tracker.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [OfflineLocationEntity::class, OfflineGeofenceEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BeaconTrackerDatabase : RoomDatabase() {
    abstract fun offlineBufferDao(): OfflineBufferDao
}
