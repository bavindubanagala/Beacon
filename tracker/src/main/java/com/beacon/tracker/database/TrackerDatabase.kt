package com.beacon.tracker.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CachedLocation::class], version = 1, exportSchema = false)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun locationCacheDao(): LocationCacheDao

    companion object {
        @Volatile
        private var instance: TrackerDatabase? = null

        fun getInstance(context: Context): TrackerDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    TrackerDatabase::class.java,
                    "beacon_tracker_db"
                ).build().also { instance = it }
            }
        }
    }
}
