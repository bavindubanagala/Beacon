package com.beacon.tracker.di

import android.content.Context
import androidx.room.Room
import com.beacon.tracker.auth.DeviceAuthManager
import com.beacon.tracker.db.BeaconTrackerDatabase
import com.beacon.tracker.db.OfflineBufferDao
import com.beacon.tracker.repository.FirebaseTrackerRepository
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TrackerModule {

    @Provides
    @Singleton
    fun provideDeviceAuthManager(@ApplicationContext context: Context): DeviceAuthManager {
        return DeviceAuthManager(context)
    }

    @Provides
    @Singleton
    fun provideFirebaseTrackerRepository(
        firestore: FirebaseFirestore,
        realtimeDb: FirebaseDatabase,
        deviceAuthManager: DeviceAuthManager
    ): FirebaseTrackerRepository {
        return FirebaseTrackerRepository(firestore, realtimeDb, deviceAuthManager)
    }

    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope {
        return CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    @Provides
    @Singleton
    fun provideBeaconTrackerDatabase(@ApplicationContext context: Context): BeaconTrackerDatabase {
        return Room.databaseBuilder(
            context,
            BeaconTrackerDatabase::class.java,
            "beacon_tracker_offline_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideOfflineBufferDao(db: BeaconTrackerDatabase): OfflineBufferDao {
        return db.offlineBufferDao()
    }
}
