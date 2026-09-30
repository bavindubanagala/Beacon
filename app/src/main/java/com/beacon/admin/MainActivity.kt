package com.beacon.admin

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.beacon.admin.services.AdminSosService
import com.beacon.admin.ui.MainScreen
import com.beacon.admin.ui.components.LocationPermissionHandler
import com.beacon.admin.ui.theme.BeaconAdminTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startSosService()
        
        setContent {
            BeaconAdminTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LocationPermissionHandler(
                        onPermissionsGranted = {
                            
                        },
                        onPermissionsDenied = {
                            // Degraded mode: fallback to manual polling or warning UI
                        }
                    )

                    MainScreen(intent = intent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun startSosService() {
        try {
            val intent = Intent(this, AdminSosService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start SOS service", e)
        }
    }
}
