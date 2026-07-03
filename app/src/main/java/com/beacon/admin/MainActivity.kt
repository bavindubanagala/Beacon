package com.beacon.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.Text
import com.google.firebase.firestore.FirebaseFirestore

// Import the classes from the :shared module

import com.beacon.admin.auth.AuthManager
import com.beacon.admin.repository.DeviceRepository
import com.beacon.admin.repository.GroupRepository
import com.beacon.admin.repository.LocationRepository
import com.beacon.admin.repository.AlertRepository
import com.beacon.admin.repository.SettingsRepository

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var groupRepository: GroupRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var alertRepository: AlertRepository
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val firestore = FirebaseFirestore.getInstance()

        authManager = AuthManager(this)

        deviceRepository = DeviceRepository(firestore)
        groupRepository = GroupRepository(firestore)
        locationRepository = LocationRepository(firestore)
        alertRepository = AlertRepository(firestore)
        settingsRepository = SettingsRepository(firestore)

        setContent {
            com.beacon.admin.ui.theme.BeaconAdminTheme {
                Text("HELLO BEACON")
            }
        }
    }
}
