package com.beacon.tracker.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.beacon.tracker.data.LocationDao
import com.beacon.tracker.data.LocationEntity

@Database(
    entities = [LocationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
}
