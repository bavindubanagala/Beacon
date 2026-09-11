package com.beacon.di

import android.content.Context
import androidx.room.Room
import com.beacon.admin.data.local.BeaconDatabase
import com.beacon.admin.data.local.DeviceDao
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
    fun provideBeaconDatabase(
        @ApplicationContext context: Context
    ): BeaconDatabase {
        return Room.databaseBuilder(
            context,
            BeaconDatabase::class.java,
            "beacon_database.db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideDeviceDao(
        database: BeaconDatabase
    ): DeviceDao {
        return database.deviceDao()
    }
}
