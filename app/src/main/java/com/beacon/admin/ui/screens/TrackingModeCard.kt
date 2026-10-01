package com.beacon.admin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beacon.admin.ui.devices.DeviceUiModel
import com.beacon.admin.ui.theme.*
import com.beacon.admin.ui.viewmodels.ApplyState
import kotlinx.coroutines.delay
import kotlin.math.round

private enum class TrackingTimeUnit(val label: String, val millisPerUnit: Long) {
    SECONDS("Seconds", 1_000L),
    MINUTES("Minutes", 60_000L),
    HOURS("Hours", 3_600_000L),
    DAYS("Days", 86_400_000L)
}

@Composable
fun TrackingModeCard(
    device: DeviceUiModel,
    applyState: ApplyState,
    onApply: (mode: String, intervalMillis: Long?, revertAfterMillis: Long?) -> Unit,
    onStateHandled: () -> Unit
) {
    val liveCyan = Color(0xFF00E5FF)
    val scheduledViolet = Color(0xFF7C4DFF)
    val onlineNeutral = Color(0xFF81C784)

    var selectedMode by remember(device.trackingMode) { mutableStateOf(device.trackingMode.uppercase()) }

    // Scheduled interval state
    val (bestValue, bestUnit) = remember(device.scheduledIntervalMillis) {
        val ms = if (device.scheduledIntervalMillis <= 0) 900_000L else device.scheduledIntervalMillis
        when {
            ms % 86_400_000L == 0L -> (ms / 86_400_000L) to TrackingTimeUnit.DAYS
            ms % 3_600_000L == 0L -> (ms / 3_600_000L) to TrackingTimeUnit.HOURS
            ms % 60_000L == 0L -> (ms / 60_000L) to TrackingTimeUnit.MINUTES
            else -> (ms / 1_000L) to TrackingTimeUnit.SECONDS
        }
    }

    var numberText by remember(device.scheduledIntervalMillis) { mutableStateOf(bestValue.toString()) }
    var selectedUnit by remember(device.scheduledIntervalMillis) { mutableStateOf(bestUnit) }
    var expandedDropdown by remember { mutableStateOf(false) }

    // Live interval state
    var sliderValue by remember(device.liveIntervalMillis) {
        mutableStateOf((device.liveIntervalMillis.coerceIn(5_000L, 60_000L) / 1000L).toFloat())
    }

    // Live revert-after state
    val (revertValue, revertUnit) = remember(device.liveRevertAfterMillis) {
        val ms = if (device.liveRevertAfterMillis <= 0L) 1_800_000L else device.liveRevertAfterMillis
        when {
            ms % 86_400_000L == 0L -> (ms / 86_400_000L) to TrackingTimeUnit.DAYS
            ms % 3_600_000L == 0L -> (ms / 3_600_000L) to TrackingTimeUnit.HOURS
            else -> {
                val mins = round(ms.toDouble() / 60_000.0).toLong().coerceAtLeast(1L)
                mins to TrackingTimeUnit.MINUTES
            }
        }
    }

    var neverSwitchBack by remember(device.liveRevertAfterMillis) { mutableStateOf(device.liveRevertAfterMillis == 0L) }
    var revertNumberText by remember(device.liveRevertAfterMillis) { mutableStateOf(revertValue.toString()) }
    var selectedRevertUnit by remember(device.liveRevertAfterMillis) { mutableStateOf(revertUnit) }
    var expandedRevertDropdown by remember { mutableStateOf(false) }

    // Validation for Scheduled
    val num = numberText.toLongOrNull()
    val calculatedScheduledMs = if (num != null) num * selectedUnit.millisPerUnit else 0L
    val scheduledError = when {
        numberText.isBlank() || calculatedScheduledMs <= 0L -> "Enter a number greater than 0"
        calculatedScheduledMs < 15_000L -> "Minimum is 15 seconds"
        calculatedScheduledMs > 2_592_000_000L -> "Maximum is 30 days"
        else -> null
    }

    // Validation for Live revert
    val revertNum = revertNumberText.toLongOrNull()
    val calculatedRevertMs = if (revertNum != null) revertNum * selectedRevertUnit.millisPerUnit else 0L
    val revertError = if (!neverSwitchBack) {
        when {
            revertNumberText.isBlank() || calculatedRevertMs <= 0L -> "Enter a number greater than 0"
            calculatedRevertMs < 60_000L -> "Minimum is 1 minute"
            calculatedRevertMs > 604_800_000L -> "Maximum is 7 days"
            else -> null
        }
    } else null

    val isValid = when (selectedMode) {
        "SCHEDULED" -> scheduledError == null
        "LIVE" -> revertError == null
        else -> true
    }

    // Check if anything has changed
    val hasChanged = when (selectedMode) {
        "SCHEDULED" -> {
            val calcMs = (numberText.toLongOrNull() ?: 0L) * selectedUnit.millisPerUnit
            device.trackingMode.uppercase() != "SCHEDULED" || calcMs != device.scheduledIntervalMillis
        }
        "LIVE" -> {
            val liveMs = sliderValue.toLong() * 1000L
            val currentRevertMs = if (neverSwitchBack) 0L else calculatedRevertMs
            device.trackingMode.uppercase() != "LIVE" || liveMs != device.liveIntervalMillis || currentRevertMs != device.liveRevertAfterMillis
        }
        "ONLINE" -> {
            device.trackingMode.uppercase() != "ONLINE"
        }
        else -> selectedMode != device.trackingMode.uppercase()
    }

    val isApplying = applyState is ApplyState.Applying
    val canApply = hasChanged && isValid && !isApplying

    LaunchedEffect(applyState) {
        if (applyState is ApplyState.Success) {
            delay(3000L)
            onStateHandled()
        }
    }

    LaunchedEffect(selectedMode, numberText, selectedUnit, sliderValue, neverSwitchBack, revertNumberText, selectedRevertUnit) {
        if (applyState is ApplyState.Error) {
            onStateHandled()
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassSurfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tracking Mode",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )

            // Selectable mode buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val modes = listOf("SCHEDULED", "LIVE", "ONLINE")
                modes.forEach { mode ->
                    val isSelected = selectedMode == mode
                    val accentColor = when (mode) {
                        "LIVE" -> liveCyan
                        "SCHEDULED" -> scheduledViolet
                        else -> onlineNeutral
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .background(
                                if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) accentColor else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                selectedMode = mode
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.lowercase().replaceFirstChar { it.uppercase() },
                            color = if (isSelected) accentColor else TextMuted,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Mode-specific controls
            when (selectedMode) {
                "SCHEDULED" -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = numberText,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() } && input.length <= 6) {
                                        numberText = input
                                    }
                                },
                                label = { Text("Check-in every") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BeaconCyan,
                                    unfocusedBorderColor = GlassSurfaceBorder,
                                    focusedLabelColor = BeaconCyan,
                                    unfocusedLabelColor = TextMuted,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            Box {
                                OutlinedButton(
                                    onClick = { expandedDropdown = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassSurfaceBorder)
                                ) {
                                    Text(selectedUnit.label)
                                }
                                DropdownMenu(
                                    expanded = expandedDropdown,
                                    onDismissRequest = { expandedDropdown = false }
                                ) {
                                    TrackingTimeUnit.entries.forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit.label) },
                                            onClick = {
                                                selectedUnit = unit
                                                expandedDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (scheduledError != null) {
                            Text(
                                text = scheduledError,
                                color = BeaconCrimson,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                        }
                    }
                }
                "LIVE" -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Updating every ${sliderValue.toInt()} seconds",
                            style = MaterialTheme.typography.bodyMedium,
                            color = liveCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            valueRange = 5f..60f,
                            steps = 54,
                            colors = SliderDefaults.colors(
                                thumbColor = liveCyan,
                                activeTrackColor = liveCyan
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val baseMode = if (device.trackingMode.trim().uppercase() == "LIVE") device.revertToMode else device.trackingMode
                        val normalizedBase = baseMode.trim().uppercase()
                        val targetModeLabel = if (normalizedBase == "ONLINE") "Online" else "Scheduled"

                        Text(
                            text = "Switch back to $targetModeLabel after",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { neverSwitchBack = !neverSwitchBack }
                        ) {
                            Checkbox(
                                checked = neverSwitchBack,
                                onCheckedChange = { neverSwitchBack = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = BeaconCyan,
                                    uncheckedColor = GlassSurfaceBorder
                                )
                            )
                            Text(
                                text = "Never switch back",
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                        }

                        if (!neverSwitchBack) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = revertNumberText,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() } && input.length <= 4) {
                                            revertNumberText = input
                                        }
                                    },
                                    label = { Text("Switch back in") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BeaconCyan,
                                        unfocusedBorderColor = GlassSurfaceBorder,
                                        focusedLabelColor = BeaconCyan,
                                        unfocusedLabelColor = TextMuted,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )

                                Box {
                                    OutlinedButton(
                                        onClick = { expandedRevertDropdown = true },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassSurfaceBorder)
                                    ) {
                                        Text(selectedRevertUnit.label)
                                    }
                                    DropdownMenu(
                                        expanded = expandedRevertDropdown,
                                        onDismissRequest = { expandedRevertDropdown = false }
                                    ) {
                                        val revertUnits = listOf(
                                            TrackingTimeUnit.MINUTES,
                                            TrackingTimeUnit.HOURS,
                                            TrackingTimeUnit.DAYS
                                        )
                                        revertUnits.forEach { unit ->
                                            DropdownMenuItem(
                                                text = { Text(unit.label) },
                                                onClick = {
                                                    selectedRevertUnit = unit
                                                    expandedRevertDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (revertError != null) {
                                Text(
                                    text = revertError,
                                    color = BeaconCrimson,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                                )
                            }
                        }
                    }
                }
                "ONLINE" -> {
                    Text(
                        text = "Not tracking right now. You can still request this device's location anytime, and it quietly checks in every 5 minutes to confirm it's connected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }

            // Apply Button
            Button(
                onClick = {
                    val intervalMillis = when (selectedMode) {
                        "SCHEDULED" -> (numberText.toLongOrNull() ?: 0L) * selectedUnit.millisPerUnit
                        "LIVE" -> sliderValue.toLong() * 1000L
                        else -> null
                    }
                    val revertAfterMillis = when (selectedMode) {
                        "LIVE" -> if (neverSwitchBack) 0L else calculatedRevertMs
                        else -> null
                    }
                    onApply(selectedMode, intervalMillis, revertAfterMillis)
                },
                enabled = canApply,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BeaconCyan,
                    disabledContainerColor = GlassSurfaceBorder
                )
            ) {
                Text(
                    text = if (isApplying) "Applying..." else "Apply",
                    color = if (canApply) MaterialTheme.colorScheme.onPrimary else TextMuted,
                    fontWeight = FontWeight.Bold
                )
            }

            // Status feedback below button
            when (applyState) {
                is ApplyState.Success -> {
                    Text(
                        text = "Applied. The device will use this setting shortly.",
                        color = onlineNeutral,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                is ApplyState.Error -> {
                    Text(
                        text = (applyState as ApplyState.Error).message,
                        color = BeaconCrimson,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                else -> {}
            }
        }
    }
}
