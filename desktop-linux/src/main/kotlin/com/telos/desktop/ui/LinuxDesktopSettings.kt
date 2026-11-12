package com.telos.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.cursor.CursorPreset
import com.telos.desktop.display.LinuxDisplayManager
import com.telos.desktop.icons.IconThemeSource
import com.telos.desktop.icons.LinuxIconThemeResolver
import com.telos.desktop.icons.TelosIconPackCatalog
import com.telos.desktop.icons.TelosIconPackInstaller
import com.telos.desktop.icons.TelosIconPackPaths
import com.telos.desktop.platform.PlatformEnvironment
import com.telos.desktop.platform.WindowsShellIntegration
import com.telos.desktop.sync.DesktopSessionSync
import com.telos.desktop.settings.TelosDesktopSettings
import com.telos.desktop.wm.WindowButtonStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File

@Composable
fun LinuxDesktopSettingsPanel(
    settings: TelosDesktopSettings,
    icons: LinuxIconThemeResolver,
    display: LinuxDisplayManager,
    onClose: () -> Unit,
) {
    val filter = PlatformEnvironment.settings
    var themes by remember { mutableStateOf(icons.listInstalledThemes()) }
    val outputs = remember { display.queryOutputs() }
    val iconScope = rememberCoroutineScope()
    var iconBusyId by remember { mutableStateOf<String?>(null) }
    var iconStatus by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching {
            val opsec = com.telos.desktop.vault.PhantomVault.engine.opsec
            opsec.secureClipboardEnabled = settings.secureClipboard
            opsec.clipboardClearSeconds = settings.clipboardClearSeconds
        }
    }
    Column(
        Modifier.fillMaxSize().background(TelosColors.surface).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(com.telos.desktop.i18n.TelosI18n.t("telos_desktop_settings"), color = TelosColors.secondary, fontSize = 22.sp)
        HelixSettingsCard("Session") {
            Toggle("Start fullscreen", settings.startFullscreen) {
                settings.startFullscreen = it
                settings.save()
                DesktopChrome.fullscreen = it
            }
            Toggle("Follow KDE/GTK system theme", settings.followSystemTheme) {
                settings.followSystemTheme = it
                settings.save()
            }
            Text("Idle lock ${settings.idleSeconds}s", fontSize = 12.sp, color = TelosColors.onSurface)
            androidx.compose.material3.Slider(
                (settings.idleSeconds - 30).toFloat() / 570f,
                {
                    settings.idleSeconds = (30 + it * 570f).toInt().coerceIn(30, 600)
                    settings.save()
                },
            )
            TextButton(onClick = { DesktopChrome.toggleFullscreen() }) {
                Text(if (DesktopChrome.fullscreen) "Exit fullscreen (F11)" else "Enter fullscreen (F11)")
            }
            if (filter.showAndroidMobileSettings) {
                Text(
                    "Shizuku / Dhizuku / Island and Android permissions are hidden on Linux.",
                    fontSize = 12.sp,
                    color = TelosColors.onSurface.copy(0.6f),
                )
            }
        }
        if (filter.showDisplayResolution) {
            HelixSettingsCard("Display") {
                outputs.forEach { out ->
                    Text(out.name + " " + (out.current?.label ?: ""), color = TelosColors.onSurface)
                    out.modes.take(12).forEach { mode ->
                        TextButton(onClick = {
                            display.applyDisplayMode(out.name, "${mode.width}x${mode.height}", mode.refreshHz, out.scale)
                        }) { Text(mode.label) }
                    }
                    Row {
                        com.telos.desktop.session.SessionCompositor.fractionalScales.forEach { scale ->
                            TextButton(onClick = {
                                val res = out.current?.let { "${it.width}x${it.height}" } ?: return@TextButton
                                val hz = out.current?.refreshHz ?: return@TextButton
                                display.applyDisplayMode(out.name, res, hz, scale)
                            }) { Text(com.telos.desktop.session.SessionCompositor.scaleLabel(scale)) }
                        }
                    }
                }
            }
        }
        if (filter.showDesktopSurfaceFilters) {
            HelixSettingsCard("Desktop") {
                Toggle("Show desktop icons", settings.showDesktopIcons) { settings.showDesktopIcons = it; settings.save() }
                Toggle("Show hidden files", settings.showHiddenFiles) { settings.showHiddenFiles = it; settings.save() }
                Toggle("Show .lnk files", settings.showLnkFiles) { settings.showLnkFiles = it; settings.save() }
                Toggle("Show .desktop shortcuts", settings.showDesktopFiles) { settings.showDesktopFiles = it; settings.save() }
                Toggle("Show standard folders", settings.showStandardFolders) { settings.showStandardFolders = it; settings.save() }
            }
        }
        if (filter.showLinuxIconThemeSelector) {
            HelixSettingsCard(com.telos.desktop.i18n.TelosI18n.t("desktop_icons_title")) {
                Text(
                    com.telos.desktop.i18n.TelosI18n.t("desktop_icons_freedesktop_note"),
                    fontSize = 11.sp,
                    color = TelosColors.onSurface.copy(0.7f),
                )
                Text(
                    com.telos.desktop.i18n.TelosI18n.t(
                        "desktop_icons_path",
                        TelosIconPackPaths.iconsRoot().absolutePath,
                    ),
                    fontSize = 10.sp,
                    color = TelosColors.onSurface.copy(0.6f),
                )
                if (TelosIconPackPaths.usePortableIcons()) {
                    Text(
                        com.telos.desktop.i18n.TelosI18n.t("desktop_icons_portable_only"),
                        fontSize = 11.sp,
                        color = TelosColors.secondary,
                    )
                }
                Text(com.telos.desktop.i18n.TelosI18n.t("desktop_icons_theme_label"), fontSize = 12.sp)
                var open by remember { mutableStateOf(false) }
                val selectedLabel = themes.firstOrNull { it.name == settings.iconTheme }?.let { theme ->
                    val src = when (theme.source) {
                        IconThemeSource.System -> com.telos.desktop.i18n.TelosI18n.t("desktop_icons_source_system")
                        IconThemeSource.TelosDownloaded -> com.telos.desktop.i18n.TelosI18n.t("desktop_icons_source_telos")
                        IconThemeSource.BuiltIn -> com.telos.desktop.i18n.TelosI18n.t("desktop_icons_source_builtin")
                    }
                    "${theme.displayName} ($src)"
                } ?: settings.iconTheme
                TextButton(onClick = {
                    icons.invalidate()
                    themes = icons.listInstalledThemes()
                    open = true
                }) { Text(selectedLabel) }
                DropdownMenu(
                    expanded = open,
                    onDismissRequest = { open = false },
                    modifier = Modifier.background(TelosColors.menu),
                ) {
                    themes.forEach { theme ->
                        DropdownMenuItem(
                            text = {
                                val src = when (theme.source) {
                                    IconThemeSource.System -> com.telos.desktop.i18n.TelosI18n.t("desktop_icons_source_system")
                                    IconThemeSource.TelosDownloaded -> com.telos.desktop.i18n.TelosI18n.t("desktop_icons_source_telos")
                                    IconThemeSource.BuiltIn -> com.telos.desktop.i18n.TelosI18n.t("desktop_icons_source_builtin")
                                }
                                Text("${theme.displayName} · $src")
                            },
                            onClick = {
                                settings.iconTheme = theme.name
                                icons.preferredTheme = theme.name
                                settings.followSystemTheme = false
                                settings.save()
                                open = false
                            },
                        )
                    }
                }
                Text(
                    com.telos.desktop.i18n.TelosI18n.t("desktop_icons_download_section"),
                    color = TelosColors.secondary,
                    fontSize = 13.sp,
                )
                TelosIconPackCatalog.entries.forEach { pack ->
                    val installed = TelosIconPackInstaller.isInstalled(pack, icons)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(pack.displayName, color = TelosColors.onSurface, fontSize = 13.sp)
                            Text(
                                com.telos.desktop.i18n.TelosI18n.t("desktop_icons_license", pack.license),
                                fontSize = 10.sp,
                                color = TelosColors.onSurface.copy(0.65f),
                            )
                        }
                        if (installed) {
                            TextButton(onClick = {
                                val name = icons.listInstalledThemes()
                                    .firstOrNull { t ->
                                        t.name in pack.preferredThemeDirs ||
                                            t.name.equals(pack.displayName, true) ||
                                            t.name.equals(pack.id, true)
                                    }?.name ?: pack.preferredThemeDirs.firstOrNull() ?: pack.displayName
                                settings.iconTheme = name
                                icons.preferredTheme = name
                                settings.followSystemTheme = false
                                settings.save()
                            }) {
                                Text(com.telos.desktop.i18n.TelosI18n.t("desktop_icons_apply"))
                            }
                            Text(
                                com.telos.desktop.i18n.TelosI18n.t("desktop_icons_installed"),
                                fontSize = 11.sp,
                                color = TelosColors.secondary,
                            )
                        } else {
                            TextButton(
                                onClick = {
                                    if (iconBusyId != null) return@TextButton
                                    iconBusyId = pack.id
                                    iconStatus = com.telos.desktop.i18n.TelosI18n.t("desktop_icons_installing")
                                    iconScope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            TelosIconPackInstaller.install(pack)
                                        }
                                        icons.invalidate()
                                        themes = icons.listInstalledThemes()
                                        iconBusyId = null
                                        iconStatus = if (result.ok) {
                                            val applyName = result.themeNames.firstOrNull()
                                            if (applyName != null) {
                                                settings.iconTheme = applyName
                                                icons.preferredTheme = applyName
                                                settings.followSystemTheme = false
                                                settings.save()
                                            }
                                            com.telos.desktop.i18n.TelosI18n.t("desktop_icons_download_ok")
                                        } else {
                                            com.telos.desktop.i18n.TelosI18n.t("desktop_icons_download_failed") +
                                                (result.message.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
                                        }
                                    }
                                },
                                enabled = iconBusyId == null,
                            ) {
                                Text(
                                    if (iconBusyId == pack.id) {
                                        com.telos.desktop.i18n.TelosI18n.t("desktop_icons_installing")
                                    } else {
                                        com.telos.desktop.i18n.TelosI18n.t("desktop_icons_download")
                                    },
                                )
                            }
                        }
                    }
                }
                iconStatus?.let { status ->
                    Text(status, fontSize = 11.sp, color = TelosColors.onSurface.copy(0.8f))
                }
            }
        }
        if (filter.showKlassyWindowManager) {
            HelixSettingsCard("Windows") {
                WindowButtonStyle.entries.forEach { style ->
                    TextButton(onClick = { settings.windowButtonStyle = style.name; settings.save() }) {
                        Text(style.name + if (settings.windowButtonStyle == style.name) " ✓" else "")
                    }
                }
                Toggle("Buttons on left", settings.buttonsOnLeft) { settings.buttonsOnLeft = it; settings.save() }
            }
        }
        if (filter.showSweetCursor) {
            HelixSettingsCard("Cursor") {
                CursorPreset.entries.forEach { preset ->
                    TextButton(onClick = { settings.cursorPreset = preset.name; settings.save() }) {
                        Text(preset.name + if (settings.cursorPreset == preset.name) " ✓" else "")
                    }
                }
            }
        }
        HelixSettingsCard(com.telos.desktop.i18n.TelosI18n.t("desktop_vos_profiles_title")) {
            com.telos.desktop.vault.VaultProfileSwitchPanel(
                vaultConfigDir = runCatching { com.telos.desktop.vault.PhantomVault.engine.configDir }.getOrNull(),
                compact = true,
            )
        }
        if (filter.showPortableDesktopShell) {
            HelixSettingsCard("Portable") {
                Text(com.telos.desktop.i18n.TelosI18n.t("desktop_helix_portable_note"), fontSize = 12.sp)
                Text(
                    com.telos.desktop.vault.PortableTraceGuard.dataRoot().absolutePath,
                    fontSize = 11.sp,
                    color = TelosColors.onSurface.copy(0.7f),
                )
            }
            if (WindowsShellIntegration.isWindows) {
                HelixSettingsCard(com.telos.desktop.i18n.TelosI18n.t("desktop_windows_shell_title")) {
                    Text(
                        com.telos.desktop.i18n.TelosI18n.t("desktop_windows_shell_risk"),
                        fontSize = 12.sp,
                        color = TelosColors.primary,
                    )
                    val active = WindowsShellIntegration.isTelosShellActive()
                    TextButton(onClick = { WindowsShellIntegration.installTelosShell() }) {
                        Text(com.telos.desktop.i18n.TelosI18n.t("desktop_windows_shell_enable") + if (active) " ✓" else "")
                    }
                    TextButton(onClick = { WindowsShellIntegration.restoreExplorerShell() }) {
                        Text(com.telos.desktop.i18n.TelosI18n.t("desktop_windows_shell_revert"))
                    }
                }
            }
            HelixSettingsCard(com.telos.desktop.i18n.TelosI18n.t("desktop_session_sync_title")) {
                Text(com.telos.desktop.i18n.TelosI18n.t("desktop_session_sync_pass", DesktopSessionSync.DEFAULT_KEEPASS_ENTRY), fontSize = 12.sp)
                Text(
                    File(com.telos.desktop.network.DesktopNetworkController.configHome(), "sync").absolutePath,
                    fontSize = 11.sp,
                    color = TelosColors.onSurface.copy(0.7f),
                )
            }
        }
        if (filter.showLuksVault) {
            HelixSettingsCard("Vault") {
                Text("Configurations are stored only inside ~/.local/share/telos/vault_mnt after unlock.", fontSize = 12.sp)
                Text(com.telos.desktop.vault.HardwareCryptoAccelerator.probe().label, fontSize = 12.sp, color = TelosColors.secondary)
                TextButton(onClick = { runCatching { com.telos.desktop.vault.PhantomVault.engine.expandManual() } }) { Text("Expand vault +128MB") }
                TextButton(onClick = { runCatching { com.telos.desktop.vault.PhantomVault.engine.compact() } }) { Text("Compact vault (fstrim)") }
                TextButton(onClick = { runCatching { com.telos.desktop.vault.PhantomVault.engine.lock(syncCloud = true) } }) { Text("Sync vault now") }
                Toggle("Auto-grow", com.telos.desktop.vault.PhantomVault.engine.autoGrow) {
                    com.telos.desktop.vault.PhantomVault.engine.autoGrow = it
                    com.telos.desktop.vault.PhantomVault.engine.expander?.autoGrowEnabled = it
                }
            }
        }

        HelixSettingsCard(com.telos.desktop.i18n.TelosI18n.t("desktop_opsec_title")) {
            val opsec = runCatching { com.telos.desktop.vault.PhantomVault.engine.opsec }.getOrNull()
            var secure by remember {
                mutableStateOf(settings.secureClipboard)
            }
            var clearSecs by remember { mutableStateOf(settings.clipboardClearSeconds) }
            var auditLines by remember {
                mutableStateOf(com.telos.desktop.vault.TelosSessionAuditLog.readTail(24))
            }
            Toggle(com.telos.desktop.i18n.TelosI18n.t("desktop_opsec_secure_clipboard"), secure) {
                secure = it
                settings.secureClipboard = it
                opsec?.secureClipboardEnabled = it
                settings.save()
            }
            Text(
                com.telos.desktop.i18n.TelosI18n.t("desktop_opsec_clipboard_timer", clearSecs),
                fontSize = 12.sp,
                color = TelosColors.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0, 15, 45, 120).forEach { secs ->
                    TextButton(onClick = {
                        clearSecs = secs
                        settings.clipboardClearSeconds = secs
                        opsec?.clipboardClearSeconds = secs
                        settings.save()
                    }) {
                        Text(
                            if (secs == 0) com.telos.desktop.i18n.TelosI18n.t("desktop_opsec_clipboard_timer_off")
                            else "${secs}s",
                        )
                    }
                }
            }
            TextButton(onClick = {
                runCatching { com.telos.desktop.overlay.TelosClipboardManager.clearSystemAndHistory() }
            }) {
                Text(com.telos.desktop.i18n.TelosI18n.t("desktop_opsec_clipboard_clear_now"))
            }
            TextButton(onClick = {
                runCatching { com.telos.desktop.vault.PhantomVault.engine.opsec.recordAttestation() }
                auditLines = com.telos.desktop.vault.TelosSessionAuditLog.readTail(24)
            }) {
                Text(com.telos.desktop.i18n.TelosI18n.t("desktop_opsec_attestation_run"))
            }
            Text(
                com.telos.desktop.i18n.TelosI18n.t("desktop_opsec_audit_title"),
                color = TelosColors.secondary,
                fontSize = 13.sp,
            )
            Text(
                com.telos.desktop.vault.TelosSessionAuditLog.auditFile().absolutePath,
                fontSize = 10.sp,
                color = TelosColors.onSurface.copy(0.6f),
            )
            TextButton(onClick = {
                auditLines = com.telos.desktop.vault.TelosSessionAuditLog.readTail(24)
            }) {
                Text(com.telos.desktop.i18n.TelosI18n.t("desktop_opsec_audit_refresh"))
            }
            auditLines.takeLast(12).forEach { line ->
                Text(line, fontSize = 10.sp, color = TelosColors.onSurface.copy(0.85f))
            }
            if (auditLines.isEmpty()) {
                Text(
                    com.telos.desktop.i18n.TelosI18n.t("desktop_opsec_audit_empty"),
                    fontSize = 11.sp,
                    color = TelosColors.onSurface.copy(0.6f),
                )
            }
        }

        TextButton(onClick = onClose) { Text(com.telos.desktop.i18n.TelosI18n.t("close")) }
    }
}

@Composable
private fun HelixSettingsCard(title: String, body: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TelosColors.surfaceContainer)
            .border(1.dp, TelosColors.outline, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = TelosColors.primary, fontSize = 14.sp)
        body()
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    TextButton(onClick = { onChange(!value) }) {
        Text("$label: ${if (value) "On" else "Off"}")
    }
}
