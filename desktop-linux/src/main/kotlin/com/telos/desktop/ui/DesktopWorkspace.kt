package com.telos.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.telos.desktop.apps.DesktopFileLauncher
import com.telos.desktop.apps.TelosFileHandler
import com.telos.desktop.apps.TelosFilesApp
import com.telos.desktop.apps.TelosFilesNav
import com.telos.desktop.apps.TelosMediaPlayer
import com.telos.desktop.cursor.CursorPreset
import com.telos.desktop.cursor.CursorState
import com.telos.desktop.cursor.TelosCursorEngine
import com.telos.desktop.display.LinuxDisplayManager
import com.telos.desktop.icons.LinuxIconThemeResolver
import com.telos.desktop.monitor.LinuxSystemTelemetryEngine
import com.telos.desktop.monitor.TelosSystemMonitorWindow
import com.telos.desktop.overlay.ClipboardPopup
import com.telos.desktop.overlay.NotificationSidebar
import com.telos.desktop.overlay.PolkitDialog
import com.telos.desktop.overlay.SessionPower
import com.telos.desktop.overlay.SessionPowerDialog
import com.telos.desktop.overlay.TelosCaptureTool
import com.telos.desktop.overlay.TelosClipboardManager
import com.telos.desktop.overlay.TelosNotificationCenter
import com.telos.desktop.overlay.TelosOsd
import com.telos.desktop.overlay.TelosOsdOverlay
import com.telos.desktop.panel.AudioControlManager
import com.telos.desktop.panel.LaptopPowerManager
import com.telos.desktop.panel.TelosTopPanel
import com.telos.desktop.quake.TelosQuake
import com.telos.desktop.quake.TelosQuakeTerminal
import com.telos.desktop.settings.TelosDesktopSettings
import com.telos.desktop.vault.PhantomVault
import com.telos.desktop.vault.PhantomVaultEngine
import com.telos.desktop.wm.TelosWindowManager
import com.telos.desktop.wm.WorkArea
import com.telos.desktop.wm.WindowButtonStyle
import com.telos.desktop.wm.WindowDecorationTheme
import com.telos.desktop.xdg.XdgUserDirsManager
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopWorkspace(
    settings: TelosDesktopSettings,
    icons: LinuxIconThemeResolver,
    display: LinuxDisplayManager,
    wm: TelosWindowManager,
    vault: PhantomVaultEngine,
    telemetry: LinuxSystemTelemetryEngine,
    onLogout: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
) {
    val dirs = remember { XdgUserDirsManager.resolve() }
    var showDrawer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDisplay by remember { mutableStateOf(false) }
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var cursorState by remember { mutableStateOf(CursorState.DEFAULT_ARROW) }
    var altTab by remember { mutableStateOf(false) }
    val preset = runCatching { CursorPreset.valueOf(settings.cursorPreset) }.getOrDefault(CursorPreset.SWEET_DARK_NEON)
    val density = LocalDensity.current

    LaunchedEffect(settings.windowButtonStyle, settings.buttonsOnLeft) {
        val style = runCatching { WindowButtonStyle.valueOf(settings.windowButtonStyle) }
            .getOrDefault(WindowButtonStyle.KLASSY_ACCENT_FILLED)
        wm.decoration = WindowDecorationTheme(buttonStyle = style, buttonsOnLeft = settings.buttonsOnLeft)
    }
    LaunchedEffect(Unit) { display.applyPersistedOnStartup() }

    BoxWithConstraints(Modifier.fillMaxSize().background(TelosColors.background)) {
        val wpx = with(density) { maxWidth.toPx() }
        val hpx = with(density) { maxHeight.toPx() }
        // === TELOS_PENDING_REVIEW_START: telos_kde_replacement_suite ===
        val form = AdaptiveShell.formFactor(maxWidth, maxHeight)
        val topBarPx = settings.panelHeightDp.coerceIn(32f, 48f)
        LaunchedEffect(wpx, hpx, form, topBarPx) {
            wm.setScreen(
                wpx,
                hpx,
                topBarPx,
                WorkArea.DEFAULT_DOCK_PX,
                AdaptiveShell.showDock(form),
            )
        }
        LaunchedEffect(Unit) {
            TelosFileHandler.onOpenFiles = { file ->
                TelosFilesNav.path = if (file.isDirectory) file else file.parentFile ?: file
                wm.open("Files", y = topBarPx + 12f)
            }
            TelosFileHandler.onOpenMedia = { file ->
                wm.open("Media Player", y = topBarPx + 12f)
            }
        }
        // === TELOS_PENDING_REVIEW_END: telos_kde_replacement_suite ===
        Box(
            Modifier
                .fillMaxSize()
                .onPointerEvent(PointerEventType.Move) { pointer = it.changes.first().position }
                .pointerInput(refresh) {
                    detectTapGestures(
                        onLongPress = { menuAt = it },
                        onTap = {
                            menuAt = null
                            if (TelosQuake.open && TelosQuake.retractOnFocusLoss && !TelosQuake.pinned) TelosQuake.open = false
                        },
                    )
                },
        ) {
            keyRefresh(refresh) {
                DesktopSurfaceView(
                    dirs = dirs,
                    settings = settings,
                    icons = icons,
                    modifier = Modifier.fillMaxSize().padding(top = topBarPx.dp, bottom = 56.dp),
                    onEmptyRightClick = { menuAt = pointer },
                    onNotify = { toast = it },
                )
            }
            if (showDrawer) {
                AppDrawer(icons, Modifier.fillMaxSize().padding(top = topBarPx.dp, bottom = 56.dp))
            }
            FloatingWindowLayer(wm) { win ->
                when (win.title) {
                    "Settings" -> LinuxDesktopSettingsPanel(settings, icons, display) { wm.close(win.id); showSettings = false }
                    "Display" -> LinuxDesktopSettingsPanel(settings, icons, display) { wm.close(win.id); showDisplay = false }
                    // === TELOS_PENDING_REVIEW_START: telos_kde_system_monitor ===
                    "System Monitor" -> TelosSystemMonitorWindow(telemetry, vault)
                    // === TELOS_PENDING_REVIEW_END: telos_kde_system_monitor ===
                    // === TELOS_PENDING_REVIEW_START: telos_launcher_desktop_ui_parity ===
                    "Media Player" -> TelosMediaPlayer()
                    "System Optimizer" -> com.telos.desktop.apps.TelosSystemOptimizer(telemetry)
                    // === TELOS_PENDING_REVIEW_START: telos_window_drag_and_ui_fix ===
                    "Notes" -> com.telos.desktop.apps.TelosNotesApp()
                    "Calendar" -> com.telos.desktop.apps.TelosCalendarApp()
                    "Calculator" -> com.telos.desktop.apps.TelosCalculatorApp()
                    "Clock" -> com.telos.desktop.apps.TelosClockApp()
                    "Network" -> com.telos.desktop.apps.TelosNetworkApp()
                    "Store" -> com.telos.desktop.apps.TelosStoreApp()
                    // === TELOS_PENDING_REVIEW_END: telos_window_drag_and_ui_fix ===
                    "Files", "Photos", "Downloads", "Trash" -> {
                        val x = remember { XdgUserDirsManager.resolve() }
                        val start = when (win.title) {
                            "Photos" -> x.pictures
                            "Downloads" -> x.download
                            "Trash" -> x.trash
                            else -> TelosFilesNav.path
                        }
                        TelosFilesApp(start)
                    }
                    // === TELOS_PENDING_REVIEW_END: telos_launcher_desktop_ui_parity ===
                    else -> Text(win.title, color = TelosColors.onSurface, modifier = Modifier.padding(16.dp))
                }
            }
            menuAt?.let { pos ->
                Box(Modifier.offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }) {
                    DesktopContextMenu(
                        settings = settings,
                        onDisplaySettings = { showDisplay = true; wm.open("Display") },
                        onWindowCursorTheme = { showSettings = true; wm.open("Settings") },
                        onOpenTerminal = { DesktopFileLauncher.openTerminalHere(dirs.desktop) },
                        onRefresh = { refresh++ },
                        onDismiss = { menuAt = null },
                    )
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp).background(TelosColors.surfaceContainer),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(" ${com.telos.desktop.i18n.TelosI18n.t("hc_apps")}", color = TelosColors.secondary, modifier = Modifier.clickable { showDrawer = !showDrawer }.padding(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(end = 16.dp)) {
                    XdgUserDirsManager.shortcuts(dirs).take(6).forEach { (label, file) ->
                        Text(label, color = TelosColors.onSurface, fontSize = 12.sp, modifier = Modifier.clickable {
                            TelosFileHandler.open(file)
                        })
                    }
                    Text(com.telos.desktop.i18n.TelosI18n.t("settings"), color = TelosColors.secondary, modifier = Modifier.clickable {
                        showSettings = true
                        wm.open("Settings")
                    })
                }
            }
            // === TELOS_PENDING_REVIEW_START: telos_kde_replacement_suite ===
            TelosTopPanel(
                wm = wm,
                engine = telemetry,
                settings = settings,
                onLauncher = { DesktopChrome.toggleAppGrid() },
                onOpenMonitor = { wm.open("System Monitor", y = WorkArea.DEFAULT_TOP_BAR_PX + 8f, w = 960f, h = 640f) },
                onOpenTelos = { title -> wm.open(title, y = WorkArea.DEFAULT_TOP_BAR_PX + 12f) },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            // === TELOS_PENDING_REVIEW_START: telos_window_drag_and_ui_fix ===
            if (DesktopChrome.appGridOpen) {
                Box(Modifier.fillMaxSize().zIndex(200f)) {
                    PlasmaAppGrid(
                        icons = icons,
                        onDismiss = { DesktopChrome.appGridOpen = false },
                        onOpenTelos = { app ->
                            when (app.windowTitle) {
                                "Quake" -> TelosQuake.toggle()
                                else -> wm.open(app.windowTitle, y = wm.workArea.minY + 12f)
                            }
                        },
                    )
                }
            }
            // === TELOS_PENDING_REVIEW_END: telos_window_drag_and_ui_fix ===
            TelosQuakeTerminal(wm.workArea)
            TelosOsdOverlay()
            Box(Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 12.dp)) { ClipboardPopup() }
            Box(Modifier.align(Alignment.CenterEnd)) { NotificationSidebar() }
            Box(Modifier.align(Alignment.Center)) {
                PolkitDialog()
                SessionPowerDialog(vault, onLogout)
            }
            if (altTab) {
                Row(Modifier.align(Alignment.Center).background(TelosColors.surfaceContainer).padding(16.dp)) {
                    wm.visibleWindows().forEach { w ->
                        Text(w.title, color = if (w.id == wm.focusedId) TelosColors.secondary else TelosColors.onSurface, modifier = Modifier.padding(8.dp))
                    }
                }
            }
            // === TELOS_PENDING_REVIEW_END: telos_kde_replacement_suite ===
            toast?.let {
                Text(it, color = TelosColors.primary, modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp))
            }
            Canvas(Modifier.fillMaxSize()) {
                with(TelosCursorEngine) { drawCursor(cursorState, preset, pointer) }
            }
            if (SessionPower.locked.value) {
                Box(Modifier.fillMaxSize().background(TelosColors.background.copy(alpha = 0.96f)), contentAlignment = Alignment.Center) {
                    var pw by remember { mutableStateOf("") }
                    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Telos locked", color = TelosColors.secondary)
                        OutlinedTextField(pw, { pw = it }, label = { Text("Master password") })
                        Text("Unlock", color = TelosColors.primary, modifier = Modifier.clickable {
                            if (vault.unlock(pw, null, false).ok) SessionPower.locked.value = false
                        }.padding(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun keyRefresh(refreshKey: Int, content: @Composable () -> Unit) {
    androidx.compose.runtime.key(refreshKey) { content() }
}

// === TELOS_PENDING_REVIEW_START: telos_kde_replacement_suite ===
fun handleDesktopKeys(
    ev: androidx.compose.ui.input.key.KeyEvent,
    wm: TelosWindowManager,
    telemetry: LinuxSystemTelemetryEngine,
): Boolean {
    if (TelosQuake.onGlobalKey(ev)) return true
    val down = ev.type == KeyEventType.KeyDown
    if (!down) return false
    val key = ev.key
    val ctrl = ev.isCtrlPressed
    val shift = ev.isShiftPressed
    val alt = ev.isAltPressed
    // === TELOS_PENDING_REVIEW_START: telos_de_final_polish ===
    if (key == Key.F11) {
        DesktopChrome.toggleFullscreen()
        return true
    }
    if (alt && key == Key.F4) {
        wm.focusedId?.let { wm.close(it) }
        return true
    }
    if (key == Key.Escape && DesktopChrome.appGridOpen) {
        DesktopChrome.appGridOpen = false
        return true
    }
    // === TELOS_PENDING_REVIEW_END: telos_de_final_polish ===
    if (key == Key.PrintScreen) {
        if (shift) TelosCaptureTool.area() else TelosCaptureTool.fullscreen()
        return true
    }
    if (ctrl && key == Key.Escape) {
        wm.open("System Monitor", y = WorkArea.DEFAULT_TOP_BAR_PX + 8f, w = 960f, h = 640f)
        return true
    }
    if (ctrl && key == Key.V && ev.isMetaPressed) {
        TelosClipboardManager.popup = true
        return true
    }
    if (alt && key == Key.Tab) {
        wm.cycleFocus()
        return true
    }
    if (key == Key.VolumeUp || (key == Key.DirectionUp && alt)) {
        val a = AudioControlManager.snapshot()
        AudioControlManager.setVolume(a.volume + 5)
        TelosOsd.show("Volume", ((a.volume + 5) / 100.0).toFloat())
        return true
    }
    if (key == Key.VolumeDown) {
        val a = AudioControlManager.snapshot()
        AudioControlManager.setVolume(a.volume - 5)
        TelosOsd.show("Volume", ((a.volume - 5) / 100.0).toFloat())
        return true
    }
    if (key == Key.VolumeMute) {
        AudioControlManager.toggleMute()
        TelosOsd.show("Mute", if (AudioControlManager.snapshot().muted) 0f else 1f)
        return true
    }
    if (key == Key.F7) {
        val p = LaptopPowerManager.snapshot()
        LaptopPowerManager.setScreenBrightness(p.screenBrightness + 8)
        TelosOsd.show("Brightness", (p.screenBrightness + 8) / 100f)
        return true
    }
    if (key == Key.F6) {
        val p = LaptopPowerManager.snapshot()
        LaptopPowerManager.setScreenBrightness(p.screenBrightness - 8)
        TelosOsd.show("Brightness", (p.screenBrightness - 8) / 100f)
        return true
    }
    // === TELOS_PENDING_REVIEW_START: telos_elastic_sharded_pqc_vault ===
    if (ctrl && alt && key == Key.L) {
        runCatching { PhantomVault.engine.panicUnmount() }
        return true
    }
    // === TELOS_PENDING_REVIEW_END: telos_elastic_sharded_pqc_vault ===
    return false
}
// === TELOS_PENDING_REVIEW_END: telos_kde_replacement_suite ===
