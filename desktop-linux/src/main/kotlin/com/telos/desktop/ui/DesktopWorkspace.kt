package com.telos.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.apps.DesktopFileLauncher
import com.telos.desktop.cursor.CursorPreset
import com.telos.desktop.cursor.CursorState
import com.telos.desktop.cursor.TelosCursorEngine
import com.telos.desktop.display.LinuxDisplayManager
import com.telos.desktop.icons.LinuxIconThemeResolver
import com.telos.desktop.settings.TelosDesktopSettings
import com.telos.desktop.wm.TelosWindowManager
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
    val preset = runCatching { CursorPreset.valueOf(settings.cursorPreset) }.getOrDefault(CursorPreset.SWEET_DARK_NEON)

    LaunchedEffect(settings.windowButtonStyle, settings.buttonsOnLeft) {
        val style = runCatching { WindowButtonStyle.valueOf(settings.windowButtonStyle) }
            .getOrDefault(WindowButtonStyle.KLASSY_ACCENT_FILLED)
        wm.decoration = WindowDecorationTheme(buttonStyle = style, buttonsOnLeft = settings.buttonsOnLeft)
    }
    LaunchedEffect(Unit) { display.applyPersistedOnStartup() }

    Box(
        Modifier
            .fillMaxSize()
            .background(TelosColors.background)
            .onPointerEvent(PointerEventType.Move) {
                pointer = it.changes.first().position
            }
            .pointerInput(refresh) {
                detectTapGestures(
                    onLongPress = { menuAt = it },
                    onTap = { menuAt = null },
                )
            },
    ) {
        keyRefresh(refresh) {
            DesktopSurfaceView(
                dirs = dirs,
                settings = settings,
                icons = icons,
                modifier = Modifier.fillMaxSize().padding(bottom = 56.dp),
                onEmptyRightClick = { menuAt = pointer },
                onNotify = { toast = it },
            )
        }
        if (showDrawer) {
            AppDrawer(icons, Modifier.fillMaxSize().padding(bottom = 56.dp))
        }
        FloatingWindowLayer(wm) { win ->
            when (win.title) {
                "Settings" -> LinuxDesktopSettingsPanel(settings, icons, display) { wm.close(win.id); showSettings = false }
                "Display" -> LinuxDesktopSettingsPanel(settings, icons, display) { wm.close(win.id); showDisplay = false }
                else -> Text(win.title, color = TelosColors.onSurface, modifier = Modifier.padding(16.dp))
            }
        }
        menuAt?.let { pos ->
            Box(Modifier.offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }) {
                DesktopContextMenu(
                    settings = settings,
                    onDisplaySettings = {
                        showDisplay = true
                        wm.open("Display")
                    },
                    onWindowCursorTheme = {
                        showSettings = true
                        wm.open("Settings")
                    },
                    onOpenTerminal = { DesktopFileLauncher.openTerminalHere(dirs.desktop) },
                    onRefresh = { refresh++ },
                    onDismiss = { menuAt = null },
                )
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp)
                .background(TelosColors.surfaceContainer),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                " Applications",
                color = TelosColors.secondary,
                modifier = Modifier.clickable { showDrawer = !showDrawer }.padding(16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(end = 16.dp)) {
                XdgUserDirsManager.shortcuts(dirs).take(6).forEach { (label, file) ->
                    Text(
                        label,
                        color = TelosColors.onSurface,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            runCatching { java.awt.Desktop.getDesktop().open(file) }
                        },
                    )
                }
                Text("Settings", color = TelosColors.secondary, modifier = Modifier.clickable {
                    showSettings = true
                    wm.open("Settings")
                })
            }
        }
        toast?.let {
            Text(it, color = TelosColors.primary, modifier = Modifier.align(Alignment.TopCenter).padding(24.dp))
        }
        Canvas(Modifier.fillMaxSize()) {
            with(TelosCursorEngine) {
                drawCursor(cursorState, preset, pointer)
            }
        }
    }
}

@Composable
private fun keyRefresh(key: Int, content: @Composable () -> Unit) {
    androidx.compose.runtime.key(key, content)
}
