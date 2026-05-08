package com.beacon.tracker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beacon.shared.constants.SharedPrefsKeys
import com.beacon.tracker.auth.DeviceAuthManager
import com.beacon.tracker.services.LocationTrackingService
import com.beacon.tracker.ui.theme.BeaconTrackerTheme

class MainActivity : AppCompatActivity() {
    private val tag = "TrackerMainActivity"
    private lateinit var deviceAuthManager: DeviceAuthManager
    private val onboardingCompleted = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(tag, "All permissions granted")
            startLocationTracking()
        } else {
            Log.w(tag, "Some permissions denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceAuthManager = DeviceAuthManager(this)

        // Check if onboarding completed
        val prefs = getSharedPreferences(SharedPrefsKeys.PREFS_NAME, MODE_PRIVATE)
        val onboardingDone = prefs.getBoolean(SharedPrefsKeys.ONBOARDING_COMPLETED, false)

        setContent {
            BeaconTrackerTheme {
                Surface(color = MaterialTheme.colors.background) {
                    if (!onboardingDone) {
                        OnboardingScreen(deviceAuthManager) {
                            // Mark onboarding as complete
                            prefs.edit()
                                .putBoolean(SharedPrefsKeys.ONBOARDING_COMPLETED, true)
                                .apply()

                            // Request permissions
                            requestTrackerPermissions()
                        }
                    } else {
                        StatusScreen(deviceAuthManager)
                    }
                }
            }
        }
    }

    private fun requestTrackerPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.POST_NOTIFICATIONS
        )

        // Android 10+ requires ACCESS_BACKGROUND_LOCATION separately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startLocationTracking() {
        Log.d(tag, "Starting location tracking service")
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    @Composable
    private fun OnboardingScreen(
        authManager: DeviceAuthManager,
        onReadyClicked: () -> Unit
    ) {
        val deviceId = remember { authManager.getDeviceId() }
        val deviceSecret = remember { authManager.getDeviceSecret() }
        val copied = remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Welcome to Beacon Tracker",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "This app sends your location to the admin in real-time.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    "Your Device ID:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Device ID Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        deviceId,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Mono,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        android.content.ClipboardManager.setPrimaryClip(
                            android.content.ClipData.newPlainText("Device ID", deviceId)
                        )
                        copied.value = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (copied.value) "Copied!" else "Copy Device ID")
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    "⚠️ Warning: If you uninstall this app, location tracking will stop.",
                    fontSize = 11.sp,
                    color = Color(0xFFE57373)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { onReadyClicked() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("I'm Ready - Start Tracking", fontSize = 16.sp)
                }
            }
        }
    }

    @Composable
    private fun StatusScreen(authManager: DeviceAuthManager) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "✓ Tracking Active",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Your location is being sent to the admin.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    "Device ID: ${authManager.getDeviceId().take(8)}...",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { finish() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

// Helper for clipboard
private fun android.content.ClipboardManager.setPrimaryClip(clip: android.content.ClipData) {
    val context = android.app.Application()
    val manager = context.getSystemService(android.content.ClipboardManager::class.java)
    manager?.primaryClip = clip
}
