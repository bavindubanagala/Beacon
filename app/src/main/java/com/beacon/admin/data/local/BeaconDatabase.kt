package com.beacon.admin.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DeviceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BeaconDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
}
