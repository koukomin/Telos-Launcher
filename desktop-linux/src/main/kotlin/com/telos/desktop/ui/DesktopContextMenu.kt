package com.telos.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.settings.TelosDesktopSettings

@Composable
fun DesktopContextMenu(
    settings: TelosDesktopSettings,
    onDisplaySettings: () -> Unit,
    onWindowCursorTheme: () -> Unit,
    onOpenTerminal: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .width(280.dp)
            .clip(shape)
            .background(TelosColors.menu)
            .border(1.dp, TelosColors.outline, shape)
            .padding(8.dp),
    ) {
        MenuRow("Display Settings & Resolution…") { onDisplaySettings(); onDismiss() }
        Text("Desktop Icons Visibility", color = TelosColors.secondary, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
        ToggleRow("All icons", settings.showDesktopIcons) {
            settings.showDesktopIcons = it; settings.save(); onRefresh()
        }
        ToggleRow("Hidden .files", settings.showHiddenFiles) {
            settings.showHiddenFiles = it; settings.save(); onRefresh()
        }
        ToggleRow(".lnk files", settings.showLnkFiles) {
            settings.showLnkFiles = it; settings.save(); onRefresh()
        }
        ToggleRow(".desktop shortcuts", settings.showDesktopFiles) {
            settings.showDesktopFiles = it; settings.save(); onRefresh()
        }
        ToggleRow("Standard folders", settings.showStandardFolders) {
            settings.showStandardFolders = it; settings.save(); onRefresh()
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        MenuRow("Window & Cursor Theme (Klassy / Sweet)") { onWindowCursorTheme(); onDismiss() }
        MenuRow("Open Terminal Here") { onOpenTerminal(); onDismiss() }
        MenuRow("Refresh Desktop") { onRefresh(); onDismiss() }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = TelosColors.onSurface,
        modifier = Modifier.clickable(onClick = onClick).padding(10.dp),
    )
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Text(
        "$label: ${if (value) "On" else "Off"}",
        color = TelosColors.onSurface,
        modifier = Modifier.clickable { onChange(!value) }.padding(horizontal = 10.dp, vertical = 6.dp),
        fontSize = 13.sp,
    )
}
