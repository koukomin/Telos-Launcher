package com.telos.desktop.apps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.i18n.TelosI18n.t
import com.telos.desktop.icons.CandyCyberIcon
import com.telos.desktop.icons.CandyIcon
import com.telos.desktop.linux.Sys
import com.telos.desktop.panel.AudioControlManager
import com.telos.desktop.ui.TelosColors
import kotlinx.coroutines.delay

@Composable
fun TelosMediaPlayer() {
    var state by remember { mutableStateOf(AudioControlManager.snapshot()) }
    val pending = TelosFileHandler.pendingMedia
    LaunchedEffect(pending?.absolutePath) {
        pending?.let { Sys.runBg("xdg-open", it.absolutePath) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            state = AudioControlManager.snapshot()
            delay(1000)
        }
    }
    Column(Modifier.padding(16.dp)) {
        Row {
            CandyCyberIcon(CandyIcon.Volume, 48.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(t("dl_m_tab"), color = TelosColors.secondary, fontSize = 20.sp)
                Text(state.track.ifBlank { pending?.name ?: t("music_widget_no_data") }, color = TelosColors.onSurface)
                pending?.let { Text(t("menu_open_file") + ": " + it.name, color = TelosColors.secondary, fontSize = 12.sp) }
            }
        }
        Row(Modifier.padding(top = 16.dp)) {
            Button(onClick = { AudioControlManager.prev() }) { Text(t("hc_previous")) }
            Button(onClick = { AudioControlManager.playPause() }) { Text(if (state.playing) t("music_widget_pause") else t("hc_play")) }
            Button(onClick = { AudioControlManager.next() }) { Text(t("hc_next")) }
        }
        Text("${t("comms_mute")} ${state.volume.toInt()}%", color = TelosColors.onSurface, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = { AudioControlManager.toggleMute() }) { Text(t("comms_mute")) }
    }
}
