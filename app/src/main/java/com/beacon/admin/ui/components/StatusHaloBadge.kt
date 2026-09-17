package com.beacon.admin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beacon.admin.ui.theme.StatusIntervalColor
import com.beacon.admin.ui.theme.StatusLiveColor
import com.beacon.admin.ui.theme.StatusOfflineColor
import com.beacon.admin.ui.theme.StatusStandbyColor
import com.beacon.admin.ui.theme.StatusSosColor

import com.beacon.admin.ui.utils.getStatusUiConfig
import com.beacon.shared.models.DeviceStatus

@Composable
fun StatusHaloBadge(
    status: DeviceStatus,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp
) {
    val (statusColor, _) = getStatusUiConfig(status)

    Box(
        modifier = modifier
            .size(size * 1.6f)
            .clip(CircleShape)
            .background(statusColor.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(statusColor)
        )
    }
}
