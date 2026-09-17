package com.beacon.tracker.di

import android.content.Context
import androidx.room.Room
import com.beacon.tracker.data.BeaconDatabase
import com.beacon.tracker.data.LocationDao
import com.beacon.tracker.db.OfflineBufferDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideBeaconDatabase(@ApplicationContext context: Context): BeaconDatabase {
        return Room.databaseBuilder(
            context,
            BeaconDatabase::class.java,
            "beacon_tracker_db"
        ).build()
    }

    @Provides
    fun provideLocationDao(database: BeaconDatabase): LocationDao {
        return database.locationDao()
    }

    @Provides
    @Singleton
    fun provideOfflineBufferDao(database: BeaconDatabase): OfflineBufferDao {
        return database.offlineBufferDao()
    }
}
