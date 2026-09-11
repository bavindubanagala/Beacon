package com.beacon.admin

import android.app.Application
import com.beacon.admin.services.GeofenceEventObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BeaconApplication : Application() {

    @Inject lateinit var geofenceEventObserver: GeofenceEventObserver

    override fun onCreate() {
        super.onCreate()
        geofenceEventObserver.startObserving()
    }
}
