package com.beacon.admin.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.beacon.admin.ui.theme.BeaconCyan
import com.beacon.admin.ui.theme.TextMuted

@Composable
fun TopBarThemeToggle(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggleTheme,
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
            contentDescription = "Toggle Theme",
            tint = if (isDarkTheme) BeaconCyan else TextMuted
        )
    }
}
