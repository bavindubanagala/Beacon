package com.beacon.tracker

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beacon.tracker.permissions.*
import com.beacon.tracker.services.LocationTrackingService
import com.beacon.tracker.ui.TrackerViewModel
import com.beacon.tracker.ui.theme.BeaconTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val viewModel: TrackerViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode

            BeaconTrackerTheme(darkTheme = isDarkMode) {
                MainContent(viewModel, permissionManager)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionManager.refreshPermissions()
    }
}

@Composable
fun MainContent(viewModel: TrackerViewModel, permissionManager: PermissionManager) {
    val isPaired by viewModel.isPaired
    val permissionState by permissionManager.permissionState.collectAsStateWithLifecycle()

    StatusUpdateReceiver(viewModel)
    PermissionRequestFlow(permissionManager, permissionState)

    if (isPaired) {
        StatusScreen(viewModel, permissionState.isFullyGranted)
    } else {
        PairingScreen(viewModel)
    }
}

@Composable
fun StatusUpdateReceiver(viewModel: TrackerViewModel) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val message = intent?.getStringExtra(LocationTrackingService.EXTRA_STATUS_MESSAGE)
                if (message != null) {
                    viewModel.updateStatus(message)
                }
            }
        }
        val filter = IntentFilter(LocationTrackingService.ACTION_STATUS_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}

@Composable
fun PermissionRequestFlow(permissionManager: PermissionManager, permissionState: PermissionState) {
    val context = LocalContext.current
    var showBackgroundRationale by remember { mutableStateOf(false) }
    var showBatteryRationale by remember { mutableStateOf(false) }
    var showSettingsRationale by remember { mutableStateOf(false) }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionManager.refreshPermissions()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionManager.refreshPermissions()
    }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionManager.refreshPermissions()
    }

    if (showBackgroundRationale) {
        BackgroundLocationRationaleDialog(
            onConfirm = {
                showBackgroundRationale = false
                backgroundLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            },
            onDismiss = { showBackgroundRationale = false }
        )
    }

    if (showBatteryRationale) {
        BatteryOptimizationDialog(
            onConfirm = {
                showBatteryRationale = false
                permissionManager.requestIgnoreBatteryOptimizations()
            },
            onDismiss = { showBatteryRationale = false }
        )
    }

    if (showSettingsRationale) {
        SettingsRedirectDialog(
            onConfirm = {
                showSettingsRationale = false
                permissionManager.openAppSettings()
            },
            onDismiss = { showSettingsRationale = false }
        )
    }

    LaunchedEffect(permissionState) {
        if (!permissionState.hasForegroundLocation) {
            foregroundLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionState.hasNotificationPermission) {
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !permissionState.hasBackgroundLocation) {
            showBackgroundRationale = true
        } else if (!permissionState.isBatteryOptimizationIgnored) {
            showBatteryRationale = true
        } else {
            val serviceIntent = Intent(context, LocationTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}

@Composable
fun PairingScreen(viewModel: TrackerViewModel) {
    val pairingCode by viewModel.pairingCode
    val expiresAt by viewModel.pairingExpiresAt
    val context = LocalContext.current
    var timeLeft by remember { mutableStateOf(0L) }
    
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10000)
            viewModel.checkPairingStatus()
        }
    }

    LaunchedEffect(expiresAt) {
        while (expiresAt > System.currentTimeMillis()) {
            timeLeft = (expiresAt - System.currentTimeMillis()) / 1000
            kotlinx.coroutines.delay(1000)
        }
        timeLeft = 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to Beacon", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("THIS DEVICE IS NOT PAIRED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(48.dp))
        if (pairingCode == null || timeLeft <= 0) {
            Button(onClick = { viewModel.generatePairingCode() }, modifier = Modifier.fillMaxWidth().height(64.dp), shape = MaterialTheme.shapes.medium) {
                Text("Generate Pairing Code")
            }
        } else {
            Text("Your Pairing Code:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = pairingCode!!, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, letterSpacing = 8.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { viewModel.checkPairingStatus() }) {
                Icon(Icons.Rounded.Sync, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Check if paired", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(16.dp))
            val minutes = timeLeft / 60
            val seconds = timeLeft % 60
            Text(text = String.format("Expires in %02d:%02d", minutes, seconds), style = MaterialTheme.typography.labelMedium, color = if (timeLeft < 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Pairing Code", pairingCode)
                    clipboard.setPrimaryClip(clip)
                }, modifier = Modifier.weight(1f).height(56.dp), shape = MaterialTheme.shapes.medium, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                    Text("Copy")
                }
                OutlinedButton(onClick = { viewModel.generatePairingCode() }, modifier = Modifier.weight(1f).height(56.dp), shape = MaterialTheme.shapes.medium) {
                    Text("Regenerate")
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
            TextButton(onClick = { viewModel.resetAndUnpair() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))) {
                Text("Reset Device & Delete ID", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun SosButton(isActive: Boolean, onTrigger: () -> Unit, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "sos_waves")
    val wave1Scale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 2.5f, animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Restart), label = "wave1_scale")
    val wave1Alpha by infiniteTransition.animateFloat(initialValue = 0.6f, targetValue = 0f, animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Restart), label = "wave1_alpha")
    val wave2Scale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 2.5f, animationSpec = infiniteRepeatable(animation = tween(1500, delayMillis = 500, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Restart), label = "wave2_scale")
    val wave2Alpha by infiniteTransition.animateFloat(initialValue = 0.6f, targetValue = 0f, animationSpec = infiniteRepeatable(animation = tween(1500, delayMillis = 500, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Restart), label = "wave2_alpha")

    Box(modifier = modifier.size(200.dp), contentAlignment = Alignment.Center) {
        if (isActive) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = Color.Red.copy(alpha = wave1Alpha), radius = (size.minDimension / 4) * wave1Scale)
                drawCircle(color = Color.Red.copy(alpha = wave2Alpha), radius = (size.minDimension / 4) * wave2Scale)
            }
        }
        Surface(onClick = onTrigger, modifier = Modifier.size(120.dp), shape = CircleShape, color = if (isActive) Color.Red else MaterialTheme.colorScheme.errorContainer, shadowElevation = if (isActive) 12.dp else 4.dp) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "SOS", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = if (isActive) Color.White else MaterialTheme.colorScheme.onErrorContainer)
                    if (isActive) Text(text = "ACTIVE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(viewModel: TrackerViewModel, isFullyGranted: Boolean) {
    val deviceId by viewModel.deviceId
    val isUpdating by viewModel.isUpdating
    val statusMessage by viewModel.statusMessage
    val isDarkMode by viewModel.isDarkMode
    val isSosActive by viewModel.isSosActive

    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("Beacon Tracker", style = MaterialTheme.typography.titleMedium) }, actions = {
            IconButton(onClick = { viewModel.toggleDarkMode(!isDarkMode) }) {
                Icon(imageVector = if (isDarkMode) Icons.Rounded.WbSunny else Icons.Rounded.Bedtime, contentDescription = "Toggle Theme")
            }
        })
    }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if (isSosActive) "Emergency SOS Active" else (if (isFullyGranted) "Tracking Active" else "Permissions Required"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (isSosActive) MaterialTheme.colorScheme.error else (if (isFullyGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error))
                Text(text = "BEACON TRACKER SERVICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            }
            if (!isFullyGranted) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(text = "Some permissions or battery exemptions are missing. Background tracking may be unreliable.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(Modifier.padding(16.dp)) {
                    Text(text = "STATUS: ${statusMessage.uppercase()}", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "ID: $deviceId", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            var showSosConfirm by remember { mutableStateOf(false) }
            SosButton(isActive = isSosActive, onTrigger = { if (!isSosActive) showSosConfirm = true })
            if (showSosConfirm) {
                AlertDialog(onDismissRequest = { showSosConfirm = false }, title = { Text("Trigger Emergency SOS?") }, text = { Text("This will immediately alert your admin and broadcast your live location.") }, confirmButton = {
                    Button(onClick = { showSosConfirm = false; viewModel.triggerSos() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Confirm SOS") }
                }, dismissButton = { TextButton(onClick = { showSosConfirm = false }) { Text("Cancel") } })
            }
            Button(onClick = { viewModel.forceUpdate() }, enabled = !isUpdating, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium) {
                if (isUpdating) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) else { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Force Sync Location") }
            }
            TextButton(onClick = { viewModel.generatePairingCode() }) { Text("Device Re-pair / Logout", style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { viewModel.resetAndUnpair() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))) { Text("UNPAIR & DELETE ALL DATA", style = MaterialTheme.typography.labelSmall) }
        }
    }
}
