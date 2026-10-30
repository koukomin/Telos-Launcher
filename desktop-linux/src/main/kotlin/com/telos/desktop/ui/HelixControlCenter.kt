package com.telos.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.overlay.TelosNotificationCenter
import com.telos.desktop.panel.AudioControlManager
import com.telos.desktop.panel.ConnectivityManager
import com.telos.desktop.panel.LaptopPowerManager
import com.telos.desktop.wm.TelosWindowManager

@Composable
fun HelixControlCenter(
    wm: TelosWindowManager,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val audio = AudioControlManager.snapshot()
    val net = ConnectivityManager.snapshot()
    val power = LaptopPowerManager.snapshot()
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .width(360.dp)
            .clip(shape)
            .background(TelosColors.menu)
            .border(1.dp, TelosColors.outline, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Control Center", color = TelosColors.secondary, fontSize = 16.sp)
        Text(audio.track.ifBlank { "No media" }, color = TelosColors.onSurface, fontSize = 12.sp)
        Slider(audio.volume.toFloat() / 100f, { AudioControlManager.setVolume(it * 100.0) })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (audio.muted) "Unmute" else "Mute", color = TelosColors.primary, modifier = Modifier.clickable { AudioControlManager.toggleMute() })
            Text("Play", color = TelosColors.onSurface, modifier = Modifier.clickable { AudioControlManager.playPause() })
        }
        Text(
            "Wi-Fi ${if (net.wifiOn) "on" else "off"}  ${net.activeSsid ?: ""}",
            color = TelosColors.onSurface,
            modifier = Modifier.clickable { ConnectivityManager.wifiConnect(net.activeSsid ?: "") },
        )
        net.aps.take(4).forEach {
            Text("${it.ssid}  ${it.signal}%", fontSize = 12.sp, color = TelosColors.onSurface, modifier = Modifier.clickable { ConnectivityManager.wifiConnect(it.ssid) })
        }
        Text("Brightness ${power.screenBrightness}%", color = TelosColors.onSurface, fontSize = 12.sp)
        Slider(power.screenBrightness / 100f, { LaptopPowerManager.setScreenBrightness((it * 100).toInt()) })
        Text(
            if (TelosNotificationCenter.dnd.value) "Do Not Disturb: On" else "Do Not Disturb: Off",
            color = TelosColors.primary,
            modifier = Modifier.clickable { TelosNotificationCenter.dnd.value = !TelosNotificationCenter.dnd.value },
        )
        Text(
            "Vault +128 MB",
            color = TelosColors.secondary,
            modifier = Modifier.clickable { runCatching { com.telos.desktop.vault.PhantomVault.engine.expandManual() } },
        )
        Text(
            com.telos.desktop.i18n.TelosI18n.t("settings"),
            color = TelosColors.primary,
            modifier = Modifier.fillMaxWidth().clickable {
                onOpenSettings()
                wm.open("Settings")
                onDismiss()
            }.padding(8.dp),
        )
    }
}
