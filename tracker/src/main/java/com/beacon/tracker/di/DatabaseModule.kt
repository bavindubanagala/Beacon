package com.beacon.tracker.di

import android.content.Context
import androidx.room.Room
import com.beacon.tracker.data.LocationDao
import com.beacon.tracker.db.TrackerDatabase
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
    fun provideTrackerDatabase(
        @ApplicationContext context: Context
    ): TrackerDatabase {
        return Room.databaseBuilder(
            context,
            TrackerDatabase::class.java,
            "tracker_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideLocationDao(database: TrackerDatabase): LocationDao {
        return database.locationDao()
    }
}