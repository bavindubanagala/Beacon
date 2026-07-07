package com.beacon.admin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BeaconCyan,
    onPrimary = Color.Black,
    secondary = BeaconViolet,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkBorder,
    onSurfaceVariant = Color.LightGray,
    error = SeverityCritical,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = BeaconCyan,
    onPrimary = Color.Black,
    secondary = BeaconViolet,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFFAFAFA),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color.DarkGray,
    error = SeverityCritical,
    onError = Color.White
)

@Composable
fun BeaconAdminTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(), // Will update in later phase if needed
        content = content
    )
}
