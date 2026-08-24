package com.beacon.tracker.database

import android.content.Context
import androidx.room.*

@Entity(tableName = "pending_locations")
data class PendingLocation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val deviceId: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val provider: String,
    val speed: Float,
    val heading: Float,
    val batteryLevel: Int,
    val signalStrength: Int,
    val deviceMotionStatus: String
)

@Dao
interface LocationDao {
    @Insert
    suspend fun insert(location: PendingLocation)

    @Query("SELECT * FROM pending_locations ORDER BY timestamp ASC")
    suspend fun getAllPending(): List<PendingLocation>

    @Delete
    suspend fun delete(location: PendingLocation)

    @Query("DELETE FROM pending_locations")
    suspend fun clearAll()
}

@Database(entities = [PendingLocation::class], version = 1, exportSchema = false)
abstract class LocationDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile
        private var INSTANCE: LocationDatabase? = null

        fun getDatabase(context: Context): LocationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LocationDatabase::class.java,
                    "location_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
