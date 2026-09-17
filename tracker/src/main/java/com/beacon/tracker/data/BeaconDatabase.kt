package com.beacon.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.beacon.tracker.db.OfflineLocationEntity
import com.beacon.tracker.db.OfflineGeofenceEventEntity
import com.beacon.tracker.db.OfflineBufferDao

@Database(
    entities = [LocationEntity::class, OfflineLocationEntity::class, OfflineGeofenceEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BeaconDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun offlineBufferDao(): OfflineBufferDao

    companion object {
        @Volatile
        private var INSTANCE: BeaconDatabase? = null

        fun getDatabase(context: Context): BeaconDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BeaconDatabase::class.java,
                    "beacon_tracker_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
