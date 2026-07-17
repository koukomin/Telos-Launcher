package de.mm20.launcher2.ui.desktopmode

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DesktopSystemTray() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        DesktopVolumeTrayIcon()
        DesktopNetworkTrayIcon()
        DesktopBatteryTrayIcon()
        DesktopNotificationsTrayIcon()
    }
}
