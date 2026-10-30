package com.telos.desktop.panel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import com.telos.desktop.settings.TelosDesktopSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.monitor.LinuxSystemTelemetryEngine
import com.telos.desktop.overlay.TelosClipboardManager
import com.telos.desktop.overlay.TelosNotificationCenter
import com.telos.desktop.overlay.SessionPower
import com.telos.desktop.overlay.TelosCaptureTool
import com.telos.desktop.quake.TelosQuake
import com.telos.desktop.icons.CandyCyberIcon
import com.telos.desktop.icons.CandyIcon
import com.telos.desktop.ui.DesktopChrome
import com.telos.desktop.ui.TelosColors
import com.telos.desktop.apps.TelosVirtualApps
import com.telos.desktop.wm.TelosWindowManager
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class PanelPopup { None, Clock, Audio, Network, Power, Tray, Style }

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun TelosTopPanel(
    wm: TelosWindowManager,
    engine: LinuxSystemTelemetryEngine,
    settings: TelosDesktopSettings? = null,
    onLauncher: () -> Unit,
    onOpenMonitor: () -> Unit,
    onOpenTelos: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var clock by remember { mutableStateOf(LocalDateTime.now()) }
    var popup by remember { mutableStateOf(PanelPopup.None) }
    var audio by remember { mutableStateOf(AudioControlManager.snapshot()) }
    var net by remember { mutableStateOf(ConnectivityManager.snapshot()) }
    var power by remember { mutableStateOf(LaptopPowerManager.snapshot()) }
    var tray by remember { mutableStateOf(SystemTrayManager.snapshot()) }
    var cpuPill by remember { mutableStateOf("CPU --") }
    LaunchedEffect(Unit) {
        while (true) {
            clock = LocalDateTime.now()
            audio = AudioControlManager.snapshot()
            net = ConnectivityManager.snapshot()
            power = LaptopPowerManager.snapshot()
            tray = SystemTrayManager.snapshot()
            val s = engine.overviewHistory.lastOrNull() ?: engine.poll()
            cpuPill = "${s.cpu.totalPercent.toInt()}%"
            delay(2000)
        }
    }
    val barH = (settings?.panelHeightDp ?: 36f).dp
    val barColor = Color((settings?.panelArgb ?: 0xF2241E36).toInt()).copy(alpha = settings?.panelAlpha ?: 0.94f)
    val iconDp = (settings?.panelIconDp ?: 22f).dp
    Box(
        modifier.fillMaxWidth().height(barH)
            .background(barColor)
            .border(1.dp, TelosColors.outline)
            .combinedClickable(onClick = {}, onLongClick = { popup = PanelPopup.Style })
            .onPointerEvent(PointerEventType.Press) { ev ->
                if (ev.button == androidx.compose.ui.input.pointer.PointerButton.Secondary) {
                    popup = PanelPopup.Style
                }
            },
    ) {
        Row(Modifier.fillMaxWidth().height(barH).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // === TELOS_PENDING_REVIEW_START: telos_de_final_polish ===
                Box(Modifier.clickable { onLauncher() }.padding(4.dp)) {
                    CandyCyberIcon(CandyIcon.TelosLogo, iconDp)
                }
                // === TELOS_PENDING_REVIEW_END: telos_de_final_polish ===
                (0 until wm.workspaceCount).forEach { i ->
                    val active = wm.currentWorkspace == i
                    val dots = wm.windows.count { it.workspace == i }
                    Text(
                        "${i + 1}${if (dots > 0) "·$dots" else ""}",
                        color = if (active) TelosColors.primary else TelosColors.onSurface,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { wm.switchWorkspace(i) },
                    )
                }
                Text(wm.windows.find { it.id == wm.focusedId }?.title ?: "Desktop", color = TelosColors.onSurface, fontSize = 12.sp)
                // === TELOS_PENDING_REVIEW_START: telos_launcher_desktop_ui_parity ===
                TelosVirtualApps.all.filter { it.domain in TelosVirtualApps.pinnedDefaults }.forEach { app ->
                    Box(Modifier.clickable { onOpenTelos(app.windowTitle) }.padding(horizontal = 2.dp)) {
                        CandyCyberIcon(app.icon, iconDp)
                    }
                }
                // === TELOS_PENDING_REVIEW_END: telos_launcher_desktop_ui_parity ===
            }
            Text(
                clock.format(DateTimeFormatter.ofPattern("EEE d MMM  HH:mm")),
                color = TelosColors.onSurface,
                modifier = Modifier.clickable { popup = if (popup == PanelPopup.Clock) PanelPopup.None else PanelPopup.Clock },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { TelosQuake.toggle() }.padding(2.dp)) { CandyCyberIcon(CandyIcon.Terminal, 18.dp) }
                Text(cpuPill, fontSize = 11.sp, color = TelosColors.secondary, modifier = Modifier.clickable { onOpenMonitor() })
                Box(Modifier.clickable { DesktopChrome.toggleControlCenter() }.padding(2.dp)) { CandyCyberIcon(CandyIcon.Volume, 18.dp) }
                Box(Modifier.clickable { DesktopChrome.toggleControlCenter() }.padding(2.dp)) { CandyCyberIcon(CandyIcon.Network, 18.dp) }
                Box(Modifier.clickable { DesktopChrome.toggleControlCenter() }.padding(2.dp)) { CandyCyberIcon(CandyIcon.Battery, 18.dp) }
                Box(Modifier.clickable { popup = PanelPopup.Tray }.padding(2.dp)) { CandyCyberIcon(CandyIcon.Tray, 18.dp) }
                Box(Modifier.clickable { TelosClipboardManager.popup = !TelosClipboardManager.popup }.padding(2.dp)) { CandyCyberIcon(CandyIcon.Clip, 18.dp) }
                Box(Modifier.clickable { TelosNotificationCenter.open.value = !TelosNotificationCenter.open.value }.padding(2.dp)) { CandyCyberIcon(CandyIcon.Bell, 18.dp) }
                Box(Modifier.clickable { TelosCaptureTool.fullscreen() }.padding(2.dp)) { CandyCyberIcon(CandyIcon.Shot, 18.dp) }
                Text("Vault+", fontSize = 11.sp, color = TelosColors.secondary, modifier = Modifier.clickable {
                    runCatching { com.telos.desktop.vault.PhantomVault.engine.expandManual() }
                })
                Box(Modifier.clickable { DesktopChrome.toggleFullscreen() }.padding(2.dp)) { CandyCyberIcon(CandyIcon.Monitor, 18.dp) }
                Box(Modifier.clickable { SessionPower.open.value = true }.padding(2.dp)) { CandyCyberIcon(CandyIcon.Power, 18.dp) }
            }
        }
        if (popup != PanelPopup.None) {
            Box(Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.TopEnd) {
                Column(Modifier.width(320.dp).background(TelosColors.menu).border(1.dp, TelosColors.outline).padding(12.dp)) {
                    when (popup) {
                        PanelPopup.Clock -> {
                            val s = engine.overviewHistory.lastOrNull()
                            Text(clock.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")), color = TelosColors.onSurface)
                            HelixMonthGrid(clock)
                            Text("CPU ${s?.cpu?.totalPercent?.toInt() ?: 0}%  RAM ${s?.mem?.usedKb?.div(1024) ?: 0} MiB", color = TelosColors.secondary, fontSize = 12.sp)
                            Text("Open System Monitor", color = TelosColors.primary, modifier = Modifier.clickable { onOpenMonitor(); popup = PanelPopup.None }.padding(8.dp))
                        }
                        PanelPopup.Audio -> {
                            Text(audio.track.ifBlank { "No media" }, color = TelosColors.onSurface, fontSize = 12.sp)
                            Slider(audio.volume.toFloat() / 100f, { AudioControlManager.setVolume(it * 100.0) })
                            Text(if (audio.muted) "Unmute" else "Mute", modifier = Modifier.clickable { AudioControlManager.toggleMute() })
                            Text(if (audio.micMuted) "Mic muted" else "Mic live", modifier = Modifier.clickable { AudioControlManager.toggleMic() })
                            Text("⏯", modifier = Modifier.clickable { AudioControlManager.playPause() })
                            Text("⏭", modifier = Modifier.clickable { AudioControlManager.next() })
                            audio.sinks.forEach { Text(it, fontSize = 11.sp, modifier = Modifier.clickable { AudioControlManager.setSink(it) }) }
                        }
                        PanelPopup.Network -> {
                            Text("Wi-Fi ${if (net.wifiOn) "on" else "off"}  VPN ${net.vpn ?: "—"}", color = TelosColors.onSurface)
                            net.aps.take(8).forEach {
                                Text("${it.ssid}  ${it.signal}% ${if (it.connected) "•" else ""}", fontSize = 12.sp, modifier = Modifier.clickable { ConnectivityManager.wifiConnect(it.ssid) })
                            }
                            Text("Bluetooth ${if (net.btOn) "on" else "off"}", modifier = Modifier.clickable { ConnectivityManager.btPower(!net.btOn) })
                            net.btDevices.forEach {
                                Text("${it.name} ${it.battery?.let { b -> "$b%" } ?: ""}", fontSize = 12.sp, modifier = Modifier.clickable { ConnectivityManager.btConnect(it.mac) })
                            }
                        }
                        PanelPopup.Power -> {
                            Text("Battery ${power.battery.percent}% ${if (power.battery.charging) "charging" else ""} ${power.battery.watts?.let { "%.1f W".format(it) } ?: ""}", color = TelosColors.onSurface)
                            power.profiles.forEach { Text(it, color = if (it == power.profile) TelosColors.secondary else TelosColors.onSurface, modifier = Modifier.clickable { LaptopPowerManager.setProfile(it) }) }
                            power.gpuModes.forEach { Text("GPU $it", fontSize = 12.sp, modifier = Modifier.clickable { LaptopPowerManager.setGpuMode(it) }) }
                            Text("Screen ${power.screenBrightness}%")
                            Slider(power.screenBrightness / 100f, { LaptopPowerManager.setScreenBrightness((it * 100).toInt()) })
                            Text("Keyboard ${power.kbdBrightness}%")
                            Slider(power.kbdBrightness / 100f, { LaptopPowerManager.setKbdBrightness((it * 100).toInt()) })
                        }
                        PanelPopup.Tray -> tray.forEach {
                            Text("${it.name} ${if (it.running) "●" else "○"}", modifier = Modifier.clickable { SystemTrayManager.launch(it) })
                        }
                        // === TELOS_PENDING_REVIEW_START: telos_desktop_kwin_parity_upgrade ===
                        PanelPopup.Style -> {
                            val s = settings
                            if (s == null) {
                                Text("Panel settings unavailable", color = TelosColors.onSurface)
                            } else {
                                Text(com.telos.desktop.i18n.TelosI18n.t("menu_customize"), color = TelosColors.secondary)
                                Text("Height ${s.panelHeightDp.toInt()} dp", fontSize = 11.sp, color = TelosColors.onSurface)
                                Slider((s.panelHeightDp - 32f) / 16f, {
                                    s.panelHeightDp = (32f + it * 16f).coerceIn(32f, 48f)
                                    s.save()
                                })
                                Text("Icon size ${s.panelIconDp.toInt()} dp", fontSize = 11.sp, color = TelosColors.onSurface)
                                Slider((s.panelIconDp - 20f) / 16f, {
                                    s.panelIconDp = (20f + it * 16f).coerceIn(20f, 36f)
                                    s.save()
                                })
                                Text("Transparency ${(s.panelAlpha * 100).toInt()}%", fontSize = 11.sp, color = TelosColors.onSurface)
                                Slider(s.panelAlpha, {
                                    s.panelAlpha = it.coerceIn(0.2f, 1f)
                                    s.save()
                                })
                                Text("Glass blur ${s.panelBlur.toInt()}", fontSize = 11.sp, color = TelosColors.onSurface)
                                Slider((s.panelBlur / 32f).coerceIn(0f, 1f), {
                                    s.panelBlur = (it * 32f).coerceIn(0f, 32f)
                                    s.save()
                                })
                                Text("Tint (ARGB hex ${s.panelArgb.toString(16)})", fontSize = 11.sp, color = TelosColors.onSurface)
                                val r = ((s.panelArgb shr 16) and 0xFF) / 255f
                                val g = ((s.panelArgb shr 8) and 0xFF) / 255f
                                val b = (s.panelArgb and 0xFF) / 255f
                                Slider(r, { nr ->
                                    s.panelArgb = (s.panelArgb and 0xFF00FFFFL) or ((nr * 255).toLong() shl 16)
                                    s.save()
                                })
                                Slider(g, { ng ->
                                    s.panelArgb = (s.panelArgb and 0xFFFF00FFL) or ((ng * 255).toLong() shl 8)
                                    s.save()
                                })
                                Slider(b, { nb ->
                                    s.panelArgb = (s.panelArgb and 0xFFFFFF00L) or (nb * 255).toLong()
                                    s.save()
                                })
                            }
                        }
                        else -> {}
                        // === TELOS_PENDING_REVIEW_END: telos_desktop_kwin_parity_upgrade ===
                    }
                }
            }
        }
    }
}

@Composable
private fun HelixMonthGrid(now: LocalDateTime) {
    val month = java.time.YearMonth.from(now.toLocalDate())
    val firstDow = month.atDay(1).dayOfWeek.value % 7
    val days = month.lengthOfMonth()
    val today = now.dayOfMonth
    Text("${month.month} ${month.year}", color = TelosColors.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
            Text(d, color = TelosColors.onSurface.copy(0.5f), fontSize = 10.sp, modifier = Modifier.width(28.dp))
        }
    }
    var cell = 0
    repeat(6) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(7) { col ->
                val day = cell - firstDow + 1
                cell++
                val label = if (day in 1..days) day.toString() else ""
                Text(
                    label,
                    color = if (day == today) TelosColors.primary else TelosColors.onSurface,
                    fontSize = 11.sp,
                    modifier = Modifier.width(28.dp),
                )
            }
        }
    }
}
