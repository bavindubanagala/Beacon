package com.beacon.admin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = BeaconCyan,
    onPrimary = ObsidianBase,
    secondary = BeaconViolet,
    onSecondary = TextPrimary,
    tertiary = BeaconAmber,
    error = BeaconCrimson,
    onError = TextPrimary,
    background = ObsidianBase,
    onBackground = TextPrimary,
    surface = GlassSurface,
    onSurface = TextPrimary,
    surfaceVariant = GlassSurfaceBorder,
    onSurfaceVariant = TextSecondary
)

@Composable
fun BeaconAdminTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
