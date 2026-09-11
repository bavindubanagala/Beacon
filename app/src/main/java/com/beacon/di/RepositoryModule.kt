package com.beacon.di

import com.beacon.data.repository.AlertRepository
import com.beacon.data.repository.DeviceRepository
import com.beacon.data.repository.FirestoreAlertRepositoryImpl
import com.beacon.data.repository.FirestoreDeviceRepositoryImpl
import com.beacon.data.repository.FirebaseLocationRepository
import com.beacon.data.repository.LocationRepository
import com.beacon.data.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAlertRepository(
        impl: FirestoreAlertRepositoryImpl
    ): AlertRepository

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(
        impl: FirestoreDeviceRepositoryImpl
    ): DeviceRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        impl: FirebaseLocationRepository
    ): LocationRepository

    companion object {
        @Provides
        @Singleton
        fun provideSettingsRepository(
            firestore: com.google.firebase.firestore.FirebaseFirestore
        ): SettingsRepository {
            return SettingsRepository(firestore)
        }
    }
}
