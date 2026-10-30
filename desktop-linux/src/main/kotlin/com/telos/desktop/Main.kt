package com.telos.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.telos.desktop.display.LinuxDisplayManager
import com.telos.desktop.icons.LinuxIconThemeResolver
import com.telos.desktop.monitor.LinuxSystemTelemetryEngine
import com.telos.desktop.settings.TelosDesktopSettings
import androidx.compose.runtime.rememberCoroutineScope
import com.telos.desktop.theme.SystemThemeBridge
import com.telos.desktop.ui.DesktopChrome
import com.telos.desktop.ui.DesktopWorkspace
import com.telos.desktop.ui.TelosDesktopTheme
import com.telos.desktop.ui.handleDesktopKeys
import com.telos.desktop.vault.PhantomVault
import com.telos.desktop.vault.PhantomVaultEngine
import com.telos.desktop.vault.TelosPreBootScreen
import com.telos.desktop.wm.TelosWindowManager

fun main(args: Array<String>) {
    // === TELOS_PENDING_REVIEW_START: telos_labwc_session_p0p1 ===
    if (args.contains("--unlock-only")) {
        com.telos.desktop.session.UnlockCli.run()
        return
    }
    if (args.contains("--lock-only")) {
        com.telos.desktop.session.LockCli.run()
        return
    }
    if (args.contains("--already-unlocked")) {
        System.setProperty("telos.alreadyUnlocked", "1")
    }
    if (args.contains("--session") || System.getenv("TELOS_SESSION") == "1") {
        System.setProperty("telos.session", "1")
        DesktopChrome.fullscreen = true
    }
    // === TELOS_PENDING_REVIEW_END: telos_labwc_session_p0p1 ===
    // === TELOS_PENDING_REVIEW_START: telos_desktop_kwin_parity_upgrade ===
    System.setProperty("skiko.renderApi", System.getProperty("skiko.renderApi") ?: "OPENGL")
    System.setProperty("skiko.vsync.enabled", "true")
    System.setProperty("skiko.gpu.priority", "auto")
    // === TELOS_PENDING_REVIEW_END: telos_desktop_kwin_parity_upgrade ===
    application {
    // === TELOS_PENDING_REVIEW_START: telos_elastic_sharded_pqc_vault ===
    val vault = remember { PhantomVaultEngine().also { PhantomVault.engine = it } }
    // === TELOS_PENDING_REVIEW_END: telos_elastic_sharded_pqc_vault ===
    var unlocked by remember { mutableStateOf(vault.attachMounted()) }
    val wm = remember { TelosWindowManager() }
    // === TELOS_PENDING_REVIEW_START: telos_kde_system_monitor ===
    val telemetry = remember { LinuxSystemTelemetryEngine() }
    // === TELOS_PENDING_REVIEW_END: telos_kde_system_monitor ===
    // === TELOS_PENDING_REVIEW_START: telos_de_final_polish ===
    val windowState = rememberWindowState(width = 1440.dp, height = 900.dp, placement = WindowPlacement.Floating)
    val hostTheme = remember { SystemThemeBridge.load() }
    LaunchedEffect(DesktopChrome.fullscreen) {
        windowState.placement = DesktopChrome.placement
    }
    Window(
        onCloseRequest = {
            if (!com.telos.desktop.vault.TelosSessionHome.session) {
                vault.lock(syncCloud = true)
            }
            exitApplication()
        },
        undecorated = DesktopChrome.fullscreen,
        resizable = !DesktopChrome.fullscreen,
        title = "Telos Desktop",
        state = windowState,
        onPreviewKeyEvent = { ev ->
            handleDesktopKeys(ev, wm, telemetry)
        },
    ) {
        TelosDesktopTheme(accent = hostTheme.accent) {
            if (!unlocked) {
                // === TELOS_PENDING_REVIEW_START: telos_labwc_session_p0p1 ===
                if (com.telos.desktop.vault.TelosSessionHome.alreadyUnlocked && vault.unlocked) {
                    unlocked = true
                } else {
                // === TELOS_PENDING_REVIEW_START: telos_elastic_sharded_pqc_vault ===
                TelosPreBootScreen(vault) {
                    com.telos.desktop.session.SessionNetGuard.apply()
                    unlocked = true
                }
                // === TELOS_PENDING_REVIEW_END: telos_elastic_sharded_pqc_vault ===
                }
                // === TELOS_PENDING_REVIEW_END: telos_labwc_session_p0p1 ===
            } else {
                val scope = rememberCoroutineScope()
                androidx.compose.runtime.LaunchedEffect(Unit) { vault.startDaemons(scope) }
                val settings = remember {
                    TelosDesktopSettings(vault.configDir).also { it.load() }
                }
                LaunchedEffect(settings.startFullscreen) {
                    if (!com.telos.desktop.vault.TelosSessionHome.session) {
                        DesktopChrome.fullscreen = settings.startFullscreen
                    }
                }
                val icons = remember {
                    val themeName = if (settings.followSystemTheme) hostTheme.iconTheme ?: settings.iconTheme else settings.iconTheme
                    LinuxIconThemeResolver(preferredTheme = themeName)
                }
                val display = remember { LinuxDisplayManager(settings) }
                DesktopWorkspace(
                    settings = settings,
                    icons = icons,
                    display = display,
                    wm = wm,
                    vault = vault,
                    telemetry = telemetry,
                    onLogout = {
                        vault.lock(syncCloud = true)
                        exitApplication()
                    },
                    onKeyEvent = { handleDesktopKeys(it, wm, telemetry) },
                )
            }
        }
    }
    }
}
