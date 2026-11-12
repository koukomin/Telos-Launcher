package com.telos.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.cursor.CursorPreset
import com.telos.desktop.cursor.XCursorThemeInfo
import com.telos.desktop.display.LinuxDisplayManager
import com.telos.desktop.display.NightLightController
import com.telos.desktop.icons.IconThemeInfo
import com.telos.desktop.icons.IconThemeSource
import com.telos.desktop.icons.LinuxIconThemeResolver
import com.telos.desktop.icons.TelosIconPackCatalog
import com.telos.desktop.icons.TelosIconPackInstaller
import com.telos.desktop.icons.TelosIconPackPaths
import com.telos.desktop.platform.PlatformEnvironment
import com.telos.desktop.platform.WindowsShellIntegration
import com.telos.desktop.sync.DesktopLuksImageRestore
import com.telos.desktop.sync.DesktopSessionSync
import com.telos.desktop.sync.DesktopVaultRestore
import com.telos.desktop.sync.pairing.LanPairProtocol
import com.telos.desktop.sync.pairing.LanPairingServer
import com.telos.desktop.theme.PanelIconContrast
import com.telos.desktop.theme.PanelSurfaceMapping
import com.telos.desktop.theme.PanelSurfaceMode
import com.telos.desktop.update.DesktopGitHubReleasesChannel
import com.telos.desktop.update.DesktopSignedUpdate
import com.telos.desktop.update.DesktopUpdateRestartHelper
import com.telos.desktop.settings.TelosDesktopSettings
import com.telos.desktop.theme.DesktopThemeChrome
import com.telos.desktop.theme.MatchedPackInstaller
import com.telos.desktop.theme.MatchedThemePacks
import com.telos.desktop.theme.ThemeAccent
import com.telos.desktop.theme.ThemeDensity
import com.telos.desktop.theme.ThemeIntensity
import com.telos.desktop.vault.PhantomVault
import com.telos.desktop.wm.WindowButtonStyle
import de.mm20.launcher2.uikmp.TelosSurfaceTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import java.io.File

@Composable
fun LinuxDesktopSettingsPanel(
    settings: TelosDesktopSettings,
    icons: LinuxIconThemeResolver,
    display: LinuxDisplayManager,
    onClose: () -> Unit,
) {
    val filter = PlatformEnvironment.settings
    // Theme scans stay off the first composition frame — load on IO when Settings opens.
    var themes by remember { mutableStateOf(emptyList<IconThemeInfo>()) }
    var cursorThemes by remember { mutableStateOf(emptyList<XCursorThemeInfo>()) }
    val outputs = remember { display.queryOutputs() }
    val iconScope = rememberCoroutineScope()
    var iconBusyId by remember { mutableStateOf<String?>(null) }
    var iconStatus by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var nightEnabled by remember { mutableStateOf(settings.nightLightEnabled) }
    var nightTemp by remember { mutableStateOf(settings.nightLightTemperatureK.toFloat()) }
    var nightStatus by remember {
        mutableStateOf(
            NightLightController.snapshot(settings.nightLightEnabled, settings.nightLightTemperatureK),
        )
    }
    var wallpaperTonalOn by remember {
        mutableStateOf(
            settings.wallpaperTonal ||
                settings.themeAccent.equals("wallpaper", ignoreCase = true),
        )
    }
    var panelMode by remember { mutableStateOf(PanelSurfaceMode.parse(settings.panelSurfaceMode)) }
    var panelAlpha by remember { mutableStateOf(settings.panelAlpha) }
    var panelBlur by remember { mutableStateOf(settings.panelBlur) }
    val t = com.telos.desktop.i18n.TelosI18n
    val indexEntries = remember {
        helixSettingsIndexEntries(
            themeStudioTitle = t.t("desktop_theme_studio_title"),
            sessionTitle = t.t("desktop_settings_session"),
            appearanceTitle = t.t("desktop_theme_panel_surface"),
            displayTitle = t.t("desktop_settings_display"),
            wallpaperTonalTitle = t.t("desktop_wallpaper_tonal_title"),
            nightTitle = t.t("desktop_night_light_title"),
            desktopTitle = t.t("desktop_settings_desktop"),
            iconsTitle = t.t("desktop_icons_title"),
            windowsTitle = t.t("desktop_settings_windows"),
            cursorTitle = t.t("desktop_cursor_title"),
            profilesTitle = t.t("desktop_vos_profiles_title"),
            portableTitle = t.t("desktop_settings_portable"),
            shellTitle = t.t("desktop_windows_shell_title"),
            syncTitle = t.t("desktop_session_sync_title"),
            vaultTitle = t.t("desktop_settings_vault"),
            portalTitle = t.t("desktop_portal_device_title"),
            opsecTitle = t.t("desktop_opsec_title"),
            restoreTitle = t.t("desktop_vault_restore_title"),
            updateTitle = t.t("desktop_update_title"),
            themeStudioKeywords = listOf(
                t.t("desktop_theme_intensity"),
                t.t("desktop_theme_quiet_ops"),
                t.t("desktop_theme_vault_neon"),
                t.t("desktop_theme_paper_ops"),
                t.t("desktop_theme_accent"),
                t.t("desktop_theme_density"),
                t.t("desktop_theme_density_compact"),
                t.t("desktop_theme_density_comfort"),
                t.t("desktop_theme_density_touch"),
                t.t("desktop_theme_from_wallpaper"),
                t.t("desktop_theme_panel_surface"),
                t.t("desktop_theme_panel_transparent"),
                t.t("desktop_theme_panel_blurred"),
                t.t("desktop_theme_panel_solid"),
                t.t("desktop_panel_icon_contrast"),
                t.t("desktop_panel_icon_contrast_auto"),
                t.t("desktop_panel_icon_contrast_light"),
                t.t("desktop_panel_icon_contrast_dark"),
                t.t("desktop_panic_mute_toggle"),
                t.t("desktop_matched_packs_title"),
                t.t("desktop_unlock_theatre_hint"),
                t.t("desktop_night_light_title"),
                "appearance",
                "neon",
                "panel",
                "glass",
                "blur",
                "transparent",
                "contrast",
                "panic",
                "mute",
                "match",
                "papirus",
                "bibata",
            ),
            wallpaperTonalKeywords = listOf(
                t.t("desktop_wallpaper_tonal_enable"),
                t.t("desktop_wallpaper_tonal_hint"),
                "wallpaper",
                "tonal",
                "accent",
            ),
            nightKeywords = listOf(
                t.t("desktop_night_light_enable"),
                t.t("desktop_night_light_temperature"),
                "gammastep",
                "redshift",
                "wlsunset",
            ),
            sessionKeywords = listOf("fullscreen", "theme", "idle"),
            desktopKeywords = listOf(
                "icons",
                "hidden",
                ".lnk",
                ".desktop",
                "folders",
                t.t("desktop_theme_workspace_tints"),
                t.t("desktop_theme_workspace_tints_hint"),
                "workspace",
                "tint",
                t.t("desktop_panel_icon_contrast"),
                t.t("desktop_panel_icon_contrast_auto"),
                t.t("desktop_panel_icon_contrast_light"),
                t.t("desktop_panel_icon_contrast_dark"),
                t.t("desktop_panel_icon_contrast_hint"),
                "panel",
                "tray",
                "contrast",
                "wallpaper",
            ),
            appearanceKeywords = listOf(
                t.t("desktop_theme_panel_surface"),
                t.t("desktop_theme_panel_transparent"),
                t.t("desktop_theme_panel_blurred"),
                t.t("desktop_theme_panel_solid"),
                t.t("desktop_theme_panel_surface_hint"),
                t.t("desktop_appearance_title"),
                t.t("desktop_theme_intensity"),
                t.t("desktop_theme_intensity_quiet"),
                t.t("desktop_theme_intensity_neon"),
                t.t("desktop_unlock_theatre_hint"),
                "panel",
                "glass",
                "blur",
                "transparent",
                "opaque",
                "wallpaper",
                "quiet",
                "neon",
            ),
            portalKeywords = listOf(
                t.t("desktop_perm_camera"),
                t.t("desktop_perm_microphone"),
                "camera",
                "microphone",
                "portal",
            ),
            opsecKeywords = listOf(
                t.t("desktop_opsec_secure_clipboard"),
                t.t("desktop_opsec_clipboard_title"),
                t.t("desktop_opsec_audit_title"),
            ),
        )
    }
    val visibleIds = remember(searchQuery, indexEntries) {
        HelixSettingsIndex.filterIds(indexEntries, searchQuery)
    }
    fun show(id: String): Boolean = searchQuery.isBlank() || id in visibleIds
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching {
            val opsec = com.telos.desktop.vault.PhantomVault.engine.opsec
            opsec.secureClipboardEnabled = settings.secureClipboard
            opsec.clipboardClearSeconds = settings.clipboardClearSeconds
        }
        withContext(Dispatchers.IO) {
            themes = icons.listInstalledThemes()
            cursorThemes = com.telos.desktop.cursor.XCursorThemeDiscovery.listInstalled()
        }
    }
    Column(
        Modifier.fillMaxSize().background(TelosColors.surface).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t.t("telos_desktop_settings"), color = TelosColors.secondary, fontSize = 22.sp)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(t.t("desktop_settings_search_hint")) },
        )
        if (searchQuery.isNotBlank() && visibleIds.isEmpty()) {
            Text(
                t.t("desktop_settings_search_empty"),
                fontSize = 12.sp,
                color = TelosColors.onSurface.copy(0.7f),
            )
        }
        if (show("theme_studio")) {
            ThemeStudioCard(
                settings = settings,
                themes = themes,
                cursorThemes = cursorThemes,
                icons = icons,
                onThemesRefresh = {
                    icons.invalidate()
                    com.telos.desktop.cursor.XCursorThemeDiscovery.invalidate()
                    themes = icons.listInstalledThemes()
                    cursorThemes = com.telos.desktop.cursor.XCursorThemeDiscovery.listInstalled()
                },
                nightEnabled = nightEnabled,
                nightTemp = nightTemp,
                nightStatus = nightStatus,
                onNightEnabled = { on ->
                    nightEnabled = on
                    settings.nightLightEnabled = on
                    settings.nightLightTemperatureK = NightLightController.clampTemp(nightTemp.toInt())
                    settings.save()
                    val status = NightLightController.apply(on, settings.nightLightTemperatureK)
                    nightStatus = NightLightController.snapshot(on, settings.nightLightTemperatureK).copy(status = status)
                },
                onNightTemp = { value -> nightTemp = value },
                onNightTempFinished = {
                    val kelvin = NightLightController.clampTemp(nightTemp.toInt())
                    nightTemp = kelvin.toFloat()
                    settings.nightLightTemperatureK = kelvin
                    settings.save()
                    if (nightEnabled) {
                        val status = NightLightController.apply(true, kelvin)
                        nightStatus = NightLightController.snapshot(true, kelvin).copy(status = status)
                    } else {
                        nightStatus = NightLightController.snapshot(false, kelvin)
                    }
                },
                onWallpaperAccent = { on ->
                    wallpaperTonalOn = on
                },
            )
        }
        if (show("session")) HelixSettingsCard(t.t("desktop_settings_session")) {
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
        if (show("appearance")) HelixSettingsCard(t.t("desktop_appearance_title")) {
            Text(t.t("desktop_theme_intensity"), fontSize = 12.sp, color = TelosColors.onSurface)
            Text(
                t.t("desktop_unlock_theatre_hint"),
                fontSize = 11.sp,
                color = TelosColors.onSurface.copy(0.7f),
            )
            val current = ThemeIntensity.parse(settings.themeIntensity)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HelixTechPill(
                    t.t("desktop_theme_intensity_quiet"),
                    selected = current == ThemeIntensity.QuietOps,
                    accent = TelosColors.secondary,
                ) {
                    settings.themeIntensity = ThemeIntensity.QuietOps.propertyValue
                    settings.save()
                }
                HelixTechPill(
                    t.t("desktop_theme_intensity_neon"),
                    selected = current == ThemeIntensity.VaultNeon,
                    accent = TelosColors.primary,
                ) {
                    settings.themeIntensity = ThemeIntensity.VaultNeon.propertyValue
                    settings.save()
                }
                HelixTechPill(
                    t.t("desktop_theme_intensity_paper"),
                    selected = current == ThemeIntensity.PaperOps,
                    accent = TelosColors.secondary.copy(alpha = 0.8f),
                ) {
                    settings.themeIntensity = ThemeIntensity.PaperOps.propertyValue
                    settings.save()
                }
            }
            OutlinedTextField(
                value = settings.wallpaperPath,
                onValueChange = {
                    settings.wallpaperPath = it
                    settings.save()
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(t.t("desktop_wallpaper_path")) },
            )
            Text(
                t.t("desktop_wallpaper_path_hint"),
                fontSize = 11.sp,
                color = TelosColors.onSurface.copy(0.65f),
            )
        }
        if (filter.showLabwcSession) {
            HelixSettingsCard(com.telos.desktop.i18n.TelosI18n.t("desktop_helix_window_mode")) {
                val mode = com.telos.desktop.wm.WindowMode.parse(settings.windowMode)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelixTechPill(
                        com.telos.desktop.i18n.TelosI18n.t("desktop_helix_tiling"),
                        selected = mode == com.telos.desktop.wm.WindowMode.Tiling,
                        accent = TelosColors.secondary,
                    ) {
                        settings.windowMode = com.telos.desktop.wm.WindowMode.Tiling.propertyValue
                        settings.save()
                    }
                    HelixTechPill(
                        com.telos.desktop.i18n.TelosI18n.t("desktop_helix_stacking"),
                        selected = mode == com.telos.desktop.wm.WindowMode.Stacking,
                        accent = TelosColors.primary,
                    ) {
                        settings.windowMode = com.telos.desktop.wm.WindowMode.Stacking.propertyValue
                        settings.save()
                    }
                }
                Text(
                    com.telos.desktop.i18n.TelosI18n.t("desktop_helix_window_mode_hint"),
                    fontSize = 12.sp,
                    color = TelosColors.onSurface.copy(0.7f),
                )
            }
        }
        if (filter.showDisplayResolution && show("display")) {
            HelixSettingsCard(t.t("desktop_settings_display")) {
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
        if (show("wallpaper_tonal")) {
            HelixSettingsCard(t.t("desktop_wallpaper_tonal_title")) {
                Text(
                    t.t("desktop_wallpaper_tonal_hint"),
                    fontSize = 11.sp,
                    color = TelosColors.onSurface.copy(0.7f),
                )
                Toggle(t.t("desktop_wallpaper_tonal_enable"), wallpaperTonalOn) { on ->
                    wallpaperTonalOn = on
                    settings.applyThemeAccent(
                        if (on) ThemeAccent.FromWallpaper else ThemeAccent.NeonMagenta,
                    )
                    settings.save()
                    com.telos.desktop.theme.WallpaperTonalSeed.clearCache()
                    DesktopChrome.bumpAccentEpoch()
                }
                if (wallpaperTonalOn) {
                    Text(
                        t.t("desktop_wallpaper_tonal_accent_wallpaper"),
                        fontSize = 11.sp,
                        color = TelosColors.secondary,
                    )
                }
            }
        }
        if (show("night_light")) {
            HelixSettingsCard(t.t("desktop_night_light_title")) {
                Text(t.t("desktop_night_light_hint"), fontSize = 11.sp, color = TelosColors.onSurface.copy(0.7f))
                Toggle(t.t("desktop_night_light_enable"), nightEnabled) { on ->
                    nightEnabled = on
                    settings.nightLightEnabled = on
                    settings.nightLightTemperatureK = NightLightController.clampTemp(nightTemp.toInt())
                    settings.save()
                    val status = NightLightController.apply(on, settings.nightLightTemperatureK)
                    nightStatus = NightLightController.snapshot(on, settings.nightLightTemperatureK).copy(status = status)
                }
                Text(
                    t.t("desktop_night_light_temperature", NightLightController.clampTemp(nightTemp.toInt())),
                    fontSize = 12.sp,
                    color = TelosColors.onSurface,
                )
                Slider(
                    value = ((nightTemp - NightLightController.MIN_TEMP_K) /
                        (NightLightController.MAX_TEMP_K - NightLightController.MIN_TEMP_K).toFloat())
                        .coerceIn(0f, 1f),
                    onValueChange = {
                        nightTemp = NightLightController.MIN_TEMP_K +
                            it * (NightLightController.MAX_TEMP_K - NightLightController.MIN_TEMP_K)
                    },
                    onValueChangeFinished = {
                        val kelvin = NightLightController.clampTemp(nightTemp.toInt())
                        nightTemp = kelvin.toFloat()
                        settings.nightLightTemperatureK = kelvin
                        settings.save()
                        if (nightEnabled) {
                            val status = NightLightController.apply(true, kelvin)
                            nightStatus = NightLightController.snapshot(true, kelvin).copy(status = status)
                        } else {
                            nightStatus = NightLightController.snapshot(false, kelvin)
                        }
                    },
                )
                Text(
                    when (nightStatus.status) {
                        NightLightController.ApplyStatus.PreferenceOnly ->
                            t.t("desktop_night_light_pref_only")
                        NightLightController.ApplyStatus.Failed ->
                            t.t("desktop_night_light_failed")
                        NightLightController.ApplyStatus.Applied ->
                            t.t(
                                "desktop_night_light_applied",
                                nightStatus.backend.name.lowercase(),
                            )
                        NightLightController.ApplyStatus.Cleared ->
                            t.t("desktop_night_light_cleared")
                    },
                    fontSize = 11.sp,
                    color = TelosColors.secondary,
                )
            }
        }
        if (filter.showDesktopSurfaceFilters && show("desktop")) {
            HelixSettingsCard(t.t("desktop_settings_desktop")) {
                Toggle("Show desktop icons", settings.showDesktopIcons) { settings.showDesktopIcons = it; settings.save() }
                Toggle("Show hidden files", settings.showHiddenFiles) { settings.showHiddenFiles = it; settings.save() }
                Toggle("Show .lnk files", settings.showLnkFiles) { settings.showLnkFiles = it; settings.save() }
                Toggle("Show .desktop shortcuts", settings.showDesktopFiles) { settings.showDesktopFiles = it; settings.save() }
                Toggle("Show standard folders", settings.showStandardFolders) { settings.showStandardFolders = it; settings.save() }
                Toggle(t.t("desktop_theme_workspace_tints"), settings.workspaceTints) {
                    settings.workspaceTints = it
                    settings.save()
                }
                Text(
                    t.t("desktop_theme_workspace_tints_hint"),
                    fontSize = 11.sp,
                    color = TelosColors.onSurface.copy(0.7f),
                )
                Text(
                    t.t("desktop_panel_icon_contrast"),
                    fontSize = 13.sp,
                    color = TelosColors.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
                val contrastMode = com.telos.desktop.theme.PanelIconContrast.parseMode(settings.panelIconContrast)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelixTechPill(
                        t.t("desktop_panel_icon_contrast_auto"),
                        selected = contrastMode == com.telos.desktop.theme.PanelIconContrast.Mode.Auto,
                        accent = TelosColors.secondary,
                    ) {
                        settings.panelIconContrast =
                            com.telos.desktop.theme.PanelIconContrast.Mode.Auto.propertyValue
                        com.telos.desktop.theme.PanelIconContrast.clearCache()
                        settings.save()
                    }
                    HelixTechPill(
                        t.t("desktop_panel_icon_contrast_light"),
                        selected = contrastMode == com.telos.desktop.theme.PanelIconContrast.Mode.Light,
                        accent = TelosColors.primary,
                    ) {
                        settings.panelIconContrast =
                            com.telos.desktop.theme.PanelIconContrast.Mode.Light.propertyValue
                        com.telos.desktop.theme.PanelIconContrast.clearCache()
                        settings.save()
                    }
                    HelixTechPill(
                        t.t("desktop_panel_icon_contrast_dark"),
                        selected = contrastMode == com.telos.desktop.theme.PanelIconContrast.Mode.Dark,
                        accent = TelosColors.primary,
                    ) {
                        settings.panelIconContrast =
                            com.telos.desktop.theme.PanelIconContrast.Mode.Dark.propertyValue
                        com.telos.desktop.theme.PanelIconContrast.clearCache()
                        settings.save()
                    }
                }
                Text(
                    t.t("desktop_panel_icon_contrast_hint"),
                    fontSize = 11.sp,
                    color = TelosColors.onSurface.copy(0.7f),
                )
            }
        }
        if (show("appearance")) {
            HelixSettingsCard(t.t("desktop_theme_panel_surface")) {
                Text(
                    t.t("desktop_theme_panel_surface_hint"),
                    fontSize = 11.sp,
                    color = TelosColors.onSurface.copy(0.7f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelixTechPill(
                        t.t("desktop_theme_panel_transparent"),
                        selected = panelMode == PanelSurfaceMode.Transparent,
                        accent = TelosColors.secondary,
                    ) {
                        panelMode = PanelSurfaceMode.Transparent
                        settings.panelSurfaceMode = panelMode.propertyValue
                        settings.save()
                    }
                    HelixTechPill(
                        t.t("desktop_theme_panel_blurred"),
                        selected = panelMode == PanelSurfaceMode.Blurred,
                        accent = Color(0xFF00E676),
                    ) {
                        panelMode = PanelSurfaceMode.Blurred
                        settings.panelSurfaceMode = panelMode.propertyValue
                        settings.save()
                    }
                    HelixTechPill(
                        t.t("desktop_theme_panel_solid"),
                        selected = panelMode == PanelSurfaceMode.Solid,
                        accent = TelosColors.primary,
                    ) {
                        panelMode = PanelSurfaceMode.Solid
                        settings.panelSurfaceMode = panelMode.propertyValue
                        settings.save()
                    }
                }
                val resolved = PanelSurfaceMapping.resolve(panelMode, panelAlpha, panelBlur)
                if (resolved.frostPlateFallback) {
                    Text(
                        t.t("desktop_theme_panel_blur_unavailable"),
                        fontSize = 10.sp,
                        color = TelosColors.secondary.copy(0.85f),
                    )
                }
                if (panelMode != PanelSurfaceMode.Solid) {
                    Text(
                        t.t("desktop_theme_panel_opacity", (panelAlpha * 100).toInt()),
                        fontSize = 11.sp,
                        color = TelosColors.onSurface,
                    )
                    Slider(
                        value = panelAlpha.coerceIn(0.2f, 1f),
                        onValueChange = {
                            panelAlpha = it.coerceIn(0.2f, 1f)
                            settings.panelAlpha = panelAlpha
                        },
                        onValueChangeFinished = { settings.save() },
                    )
                }
                if (panelMode == PanelSurfaceMode.Blurred) {
                    Text(
                        t.t("desktop_theme_panel_blur", panelBlur.toInt()),
                        fontSize = 11.sp,
                        color = TelosColors.onSurface,
                    )
                    Slider(
                        value = (panelBlur / 32f).coerceIn(0f, 1f),
                        onValueChange = {
                            panelBlur = (it * 32f).coerceIn(0f, 32f)
                            settings.panelBlur = panelBlur
                        },
                        onValueChangeFinished = { settings.save() },
                    )
                }
            }
        }
        if (filter.showLinuxIconThemeSelector && show("icons")) {
            HelixSettingsCard(t.t("desktop_icons_title")) {
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
                    com.telos.desktop.cursor.XCursorThemeDiscovery.invalidate()
                    themes = icons.listInstalledThemes()
                    cursorThemes = com.telos.desktop.cursor.XCursorThemeDiscovery.listInstalled()
                    open = true
                }) { Text(selectedLabel) }
                DropdownMenu(
                    expanded = open,
                    onDismissRequest = { open = false },
                    modifier = Modifier.background(TelosSurfaceTokens.popupOpaque),
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
                                        com.telos.desktop.cursor.XCursorThemeDiscovery.invalidate()
                                        themes = icons.listInstalledThemes()
                                        cursorThemes = com.telos.desktop.cursor.XCursorThemeDiscovery.listInstalled()
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
                Text(
                    com.telos.desktop.i18n.TelosI18n.t("desktop_matched_packs_title"),
                    color = TelosColors.secondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    com.telos.desktop.i18n.TelosI18n.t("desktop_matched_packs_hint"),
                    fontSize = 11.sp,
                    color = TelosColors.onSurface.copy(0.7f),
                )
                MatchedThemePacks.all.forEach { pack ->
                    TextButton(
                        onClick = {
                            if (iconBusyId != null) return@TextButton
                            iconBusyId = "match-${pack.id}"
                            iconStatus = com.telos.desktop.i18n.TelosI18n.t("desktop_matched_packs_working")
                            iconScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    MatchedPackInstaller.apply(pack, settings, icons, downloadMissing = true)
                                }
                                icons.invalidate()
                                com.telos.desktop.cursor.XCursorThemeDiscovery.invalidate()
                                themes = icons.listInstalledThemes()
                                cursorThemes = com.telos.desktop.cursor.XCursorThemeDiscovery.listInstalled()
                                if (result.ok) {
                                    reapplyCursor(settings)
                                }
                                iconBusyId = null
                                iconStatus = if (result.ok) {
                                    com.telos.desktop.i18n.TelosI18n.t(
                                        "desktop_matched_packs_ok",
                                        pack.displayName,
                                    )
                                } else {
                                    com.telos.desktop.i18n.TelosI18n.t(
                                        "desktop_matched_packs_fail",
                                        result.message,
                                    )
                                }
                            }
                        },
                        enabled = iconBusyId == null,
                    ) {
                        Text(
                            com.telos.desktop.i18n.TelosI18n.t(
                                "desktop_matched_packs_apply",
                                pack.displayName,
                            ),
                        )
                    }
                }
            }
        }
        if (filter.showKlassyWindowManager && show("windows")) {
            HelixSettingsCard(t.t("desktop_settings_windows")) {
                WindowButtonStyle.entries.forEach { style ->
                    TextButton(onClick = { settings.windowButtonStyle = style.name; settings.save() }) {
                        Text(style.name + if (settings.windowButtonStyle == style.name) " ✓" else "")
                    }
                }
                Toggle("Buttons on left", settings.buttonsOnLeft) { settings.buttonsOnLeft = it; settings.save() }
            }
        }
        if (filter.showSweetCursor && show("cursor")) {
            HelixSettingsCard(t.t("desktop_cursor_title")) {
                var themeOpen by remember { mutableStateOf(false) }
                var downloadMsg by remember { mutableStateOf<String?>(null) }
                Text(com.telos.desktop.i18n.TelosI18n.t("desktop_cursor_theme"), fontSize = 12.sp, color = TelosColors.onSurface)
                TextButton(onClick = { themeOpen = true }) {
                    Text(
                        settings.cursorTheme.ifBlank {
                            com.telos.desktop.i18n.TelosI18n.t("desktop_cursor_system_default")
                        },
                    )
                }
                DropdownMenu(
                    expanded = themeOpen,
                    onDismissRequest = { themeOpen = false },
                    modifier = Modifier.background(TelosSurfaceTokens.popupOpaque),
                ) {
                    DropdownMenuItem(
                        text = { Text(com.telos.desktop.i18n.TelosI18n.t("desktop_cursor_system_default")) },
                        onClick = {
                            settings.cursorTheme = ""
                            settings.save()
                            reapplyCursor(settings)
                            themeOpen = false
                        },
                    )
                    cursorThemes.forEach { theme ->
                        DropdownMenuItem(
                            text = { Text(theme.displayName) },
                            onClick = {
                                settings.cursorTheme = theme.name
                                settings.save()
                                reapplyCursor(settings)
                                themeOpen = false
                            },
                        )
                    }
                }
                Text(com.telos.desktop.i18n.TelosI18n.t("desktop_cursor_size"), fontSize = 12.sp, color = TelosColors.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    com.telos.desktop.cursor.KdeCursorConfig.KDE_SIZES.forEach { sz ->
                        TextButton(onClick = {
                            settings.cursorSize = sz
                            settings.save()
                            reapplyCursor(settings)
                        }) {
                            Text("${sz}" + if (settings.cursorSize == sz) " ✓" else "")
                        }
                    }
                }
                Text(
                    com.telos.desktop.i18n.TelosI18n.t("desktop_cursor_preset_style"),
                    fontSize = 12.sp,
                    color = TelosColors.onSurface,
                )
                CursorPreset.entries.forEach { preset ->
                    TextButton(onClick = { settings.cursorPreset = preset.name; settings.save() }) {
                        Text(preset.name + if (settings.cursorPreset == preset.name) " ✓" else "")
                    }
                }
                Text(
                    when {
                        PlatformEnvironment.current == com.telos.desktop.platform.TelosRuntimePlatform.WindowsDesktop ->
                            com.telos.desktop.i18n.TelosI18n.t("desktop_cursor_limit_windows")
                        PlatformEnvironment.current == com.telos.desktop.platform.TelosRuntimePlatform.MacDesktop ->
                            com.telos.desktop.i18n.TelosI18n.t("desktop_cursor_limit_macos")
                        else ->
                            com.telos.desktop.i18n.TelosI18n.t("desktop_cursor_limit_linux")
                    },
                    fontSize = 11.sp,
                    color = TelosColors.onSurface.copy(0.65f),
                )
                if (filter.showLinuxIconThemeSelector || filter.showPortableDesktopShell) {
                    Text(com.telos.desktop.i18n.TelosI18n.t("desktop_cursor_download_section"), fontSize = 12.sp, color = TelosColors.onSurface)
                    com.telos.desktop.cursor.CursorThemeCatalog.offered().forEach { entry ->
                        val match = MatchedThemePacks.forCursorCatalogId(entry.id)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                val result = com.telos.desktop.cursor.CursorThemeCatalog.download(entry)
                                downloadMsg = if (result.ok) {
                                    settings.cursorTheme = result.themeFolder ?: entry.themeFolder
                                    settings.save()
                                    reapplyCursor(settings)
                                    com.telos.desktop.i18n.TelosI18n.t("desktop_cursor_download_ok", entry.displayName)
                                } else {
                                    com.telos.desktop.i18n.TelosI18n.t("desktop_cursor_download_fail", result.message)
                                }
                            }) {
                                Text(
                                    com.telos.desktop.i18n.TelosI18n.t(
                                        "desktop_cursor_download_named",
                                        entry.displayName,
                                        entry.license,
                                    ),
                                )
                            }
                            if (match != null) {
                                TextButton(onClick = {
                                    iconBusyId = "match-${match.id}"
                                    iconScope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            MatchedPackInstaller.apply(match, settings, icons, downloadMissing = true)
                                        }
                                        icons.invalidate()
                                        com.telos.desktop.cursor.XCursorThemeDiscovery.invalidate()
                                        themes = icons.listInstalledThemes()
                                        cursorThemes = com.telos.desktop.cursor.XCursorThemeDiscovery.listInstalled()
                                        if (result.ok) reapplyCursor(settings)
                                        iconBusyId = null
                                        downloadMsg = if (result.ok) {
                                            com.telos.desktop.i18n.TelosI18n.t(
                                                "desktop_matched_packs_ok",
                                                match.displayName,
                                            )
                                        } else {
                                            com.telos.desktop.i18n.TelosI18n.t(
                                                "desktop_matched_packs_fail",
                                                result.message,
                                            )
                                        }
                                    }
                                }) {
                                    Text(com.telos.desktop.i18n.TelosI18n.t("desktop_matched_packs_one_click"))
                                }
                            }
                        }
                    }
                    downloadMsg?.let {
                        Text(it, fontSize = 11.sp, color = TelosColors.secondary)
                    }
                }
            }
        }
        if (show("profiles")) {
            HelixSettingsCard(t.t("desktop_vos_profiles_title")) {
                Text(
                    t.t("desktop_vos_profiles_appearance_hint"),
                    fontSize = 11.sp,
                    color = TelosColors.onSurface.copy(0.7f),
                )
                com.telos.desktop.vault.VaultProfileSwitchPanel(
                    vaultConfigDir = runCatching { com.telos.desktop.vault.PhantomVault.engine.configDir }.getOrNull(),
                    compact = true,
                    onChanged = {
                        // Lock-gated or plain switch: reload appearance from profile overlay then global.
                        settings.reloadAppearanceFromProfile()
                    },
                )
            }
        }
        if (filter.showPortableDesktopShell) {
            if (show("portable")) {
                HelixSettingsCard(t.t("desktop_settings_portable")) {
                    Text(t.t("desktop_helix_portable_note"), fontSize = 12.sp)
                    Text(
                        com.telos.desktop.vault.PortableTraceGuard.dataRoot().absolutePath,
                        fontSize = 11.sp,
                        color = TelosColors.onSurface.copy(0.7f),
                    )
                }
            }
            if (WindowsShellIntegration.isWindows && show("windows_shell")) {
                HelixSettingsCard(t.t("desktop_windows_shell_title")) {
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
            if (show("session_sync")) {
                HelixSettingsCard(t.t("desktop_session_sync_title")) {
                    Text(t.t("desktop_session_sync_pass", DesktopSessionSync.DEFAULT_KEEPASS_ENTRY), fontSize = 12.sp)
                    Text(
                        File(com.telos.desktop.network.DesktopNetworkController.configHome(), "sync").absolutePath,
                        fontSize = 11.sp,
                        color = TelosColors.onSurface.copy(0.7f),
                    )
                }
            }
            HelixSettingsCard(com.telos.desktop.i18n.TelosI18n.t("desktop_lan_pair_title")) {
                var pairStatus by remember {
                    mutableStateOf(
                        LanPairingServer.sharedOrNull()?.status()
                            ?: LanPairingServer.Status(running = false),
                    )
                }
                Text(
                    com.telos.desktop.i18n.TelosI18n.t("desktop_lan_pair_hint"),
                    fontSize = 12.sp,
                    color = TelosColors.onSurface.copy(0.85f),
                )
                Text(
                    com.telos.desktop.i18n.TelosI18n.t("desktop_lan_pair_scan_hint"),
                    fontSize = 11.sp,
                    color = TelosColors.onSurface.copy(0.7f),
                )
                pairStatus.session?.let { live ->
                    Text(
                        com.telos.desktop.i18n.TelosI18n.t("desktop_lan_pair_code") + ": " + live.code,
                        fontSize = 14.sp,
                        color = TelosColors.primary,
                    )
                    Text(live.uri, fontSize = 11.sp, color = TelosColors.onSurface)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        iconScope.launch(Dispatchers.IO) {
                            val host = LanPairProtocol.suggestLanBindHost()
                            LanPairingServer.startShared(bindHost = host)
                            pairStatus = LanPairingServer.sharedOrNull()?.status()
                                ?: LanPairingServer.Status(running = false)
                        }
                    }) {
                        Text(com.telos.desktop.i18n.TelosI18n.t("desktop_lan_pair_start"))
                    }
                    TextButton(onClick = {
                        iconScope.launch(Dispatchers.IO) {
                            LanPairingServer.stopShared()
                            pairStatus = LanPairingServer.Status(running = false)
                        }
                    }) {
                        Text(com.telos.desktop.i18n.TelosI18n.t("desktop_lan_pair_stop"))
                    }
                }
            }
        }
        Vos5RestoreAndUpdateCard(settings, show = { id -> show(id) })
        if (filter.showLuksVault && show("vault")) {
            HelixSettingsCard(t.t("desktop_settings_vault")) {
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

        if (show("portal_device")) {
            HelixSettingsCard(com.telos.desktop.i18n.TelosI18n.t("desktop_portal_device_title")) {
                val scope = rememberCoroutineScope()
                var camera by remember {
                    mutableStateOf(
                        com.telos.desktop.session.PortalDeviceAccess.readRequested(
                            com.telos.desktop.session.PortalDeviceKind.Camera,
                        ),
                    )
                }
                var mic by remember {
                    mutableStateOf(
                        com.telos.desktop.session.PortalDeviceAccess.readRequested(
                            com.telos.desktop.session.PortalDeviceKind.Microphone,
                        ),
                    )
                }
                var portalStatus by remember { mutableStateOf<String?>(null) }
                Text(
                    com.telos.desktop.i18n.TelosI18n.t("desktop_portal_device_hint"),
                    fontSize = 12.sp,
                    color = TelosColors.onSurface.copy(0.75f),
                )
                if (!com.telos.desktop.session.PortalDeviceAccess.available()) {
                    Text(
                        com.telos.desktop.i18n.TelosI18n.t("desktop_portal_device_pref_only"),
                        fontSize = 12.sp,
                        color = TelosColors.secondary,
                    )
                }
                fun portalLabel(result: com.telos.desktop.session.PortalDeviceAskResult): String = when (result) {
                    com.telos.desktop.session.PortalDeviceAskResult.Requested ->
                        com.telos.desktop.i18n.TelosI18n.t("desktop_portal_device_requested")
                    com.telos.desktop.session.PortalDeviceAskResult.PreferenceOnly ->
                        com.telos.desktop.i18n.TelosI18n.t("desktop_portal_device_pref_only")
                    com.telos.desktop.session.PortalDeviceAskResult.Unavailable ->
                        com.telos.desktop.i18n.TelosI18n.t("desktop_portal_device_unavailable")
                    com.telos.desktop.session.PortalDeviceAskResult.Failed,
                    com.telos.desktop.session.PortalDeviceAskResult.Invalid,
                    -> com.telos.desktop.i18n.TelosI18n.t("desktop_portal_device_failed")
                }
                Toggle(com.telos.desktop.i18n.TelosI18n.t("desktop_perm_camera"), camera) {
                    camera = it
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            com.telos.desktop.session.PortalDeviceAccess.setRequested(
                                com.telos.desktop.session.PortalDeviceKind.Camera,
                                it,
                            )
                        }
                        portalStatus = portalLabel(result)
                    }
                }
                Toggle(com.telos.desktop.i18n.TelosI18n.t("desktop_perm_microphone"), mic) {
                    mic = it
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            com.telos.desktop.session.PortalDeviceAccess.setRequested(
                                com.telos.desktop.session.PortalDeviceKind.Microphone,
                                it,
                            )
                        }
                        portalStatus = portalLabel(result)
                    }
                }
                portalStatus?.let {
                    Text(it, fontSize = 11.sp, color = TelosColors.secondary)
                }
            }
        }

        if (show("opsec")) HelixSettingsCard(t.t("desktop_opsec_title")) {
            val opsec = runCatching { com.telos.desktop.vault.PhantomVault.engine.opsec }.getOrNull()
            var secure by remember {
                mutableStateOf(settings.secureClipboard)
            }
            var clearSecs by remember { mutableStateOf(settings.clipboardClearSeconds) }
            var fileIndex by remember { mutableStateOf(settings.fileNameIndexEnabled) }
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
                com.telos.desktop.i18n.TelosI18n.t("desktop_file_index_title"),
                color = TelosColors.secondary,
                fontSize = 13.sp,
            )
            Toggle(com.telos.desktop.i18n.TelosI18n.t("desktop_file_index_enable"), fileIndex) {
                fileIndex = it
                settings.fileNameIndexEnabled = it
                com.telos.desktop.search.VaultFileNameIndex.enabled = it
                settings.save()
                if (it) {
                    iconScope.launch(Dispatchers.IO) {
                        com.telos.desktop.search.VaultFileNameIndex.maybeRescan(force = true)
                    }
                } else {
                    com.telos.desktop.search.VaultFileNameIndex.clearMemory()
                }
            }
            Text(
                com.telos.desktop.i18n.TelosI18n.t("desktop_file_index_hint"),
                fontSize = 12.sp,
                color = TelosColors.onSurface.copy(0.75f),
            )
            Text(
                com.telos.desktop.search.VaultFileNameIndex.indexDir().absolutePath,
                fontSize = 10.sp,
                color = TelosColors.onSurface.copy(0.55f),
            )
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
            var tpmStatus by remember {
                mutableStateOf(
                    opsec?.tpmStatusForUi()?.takeIf { it != "unknown" }
                        ?: com.telos.desktop.i18n.TelosI18n.t("desktop_opsec_tpm_unknown"),
                )
            }
            Text(
                com.telos.desktop.i18n.TelosI18n.t("desktop_opsec_tpm_status", tpmStatus),
                fontSize = 12.sp,
                color = TelosColors.onSurface,
            )
            Text(
                com.telos.desktop.i18n.TelosI18n.t("desktop_opsec_tpm_hint"),
                fontSize = 10.sp,
                color = TelosColors.onSurface.copy(0.65f),
            )
            TextButton(onClick = {
                runCatching { com.telos.desktop.vault.PhantomVault.engine.opsec.recordAttestation() }
                tpmStatus = runCatching {
                    com.telos.desktop.vault.PhantomVault.engine.opsec.tpmStatusForUi()
                }.getOrNull()?.takeIf { it.isNotBlank() && it != "unknown" }
                    ?: com.telos.desktop.i18n.TelosI18n.t("desktop_opsec_tpm_unknown")
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

private fun reapplyCursor(settings: TelosDesktopSettings) {
    com.telos.desktop.cursor.CursorThemeApplier.apply(
        themeName = settings.cursorTheme.ifBlank { null },
        size = settings.cursorSize,
        followSystemTheme = settings.followSystemTheme,
        hostTheme = if (settings.followSystemTheme) {
            com.telos.desktop.theme.SystemThemeBridge.load().cursorTheme
        } else {
            null
        },
    )
}

private fun helixSettingsIndexEntries(
    themeStudioTitle: String,
    sessionTitle: String,
    displayTitle: String,
    wallpaperTonalTitle: String,
    nightTitle: String,
    desktopTitle: String,
    iconsTitle: String,
    windowsTitle: String,
    cursorTitle: String,
    profilesTitle: String,
    portableTitle: String,
    shellTitle: String,
    syncTitle: String,
    vaultTitle: String,
    portalTitle: String,
    opsecTitle: String,
    restoreTitle: String,
    updateTitle: String,
    themeStudioKeywords: List<String>,
    wallpaperTonalKeywords: List<String>,
    nightKeywords: List<String>,
    sessionKeywords: List<String>,
    desktopKeywords: List<String>,
    appearanceTitle: String,
    appearanceKeywords: List<String>,
    portalKeywords: List<String>,
    opsecKeywords: List<String>,
): List<HelixSettingsIndexEntry> = listOf(
    HelixSettingsIndexEntry("theme_studio", themeStudioTitle, themeStudioKeywords),
    HelixSettingsIndexEntry("session", sessionTitle, sessionKeywords),
    HelixSettingsIndexEntry("display", displayTitle, listOf("resolution", "scale", "refresh")),
    HelixSettingsIndexEntry("wallpaper_tonal", wallpaperTonalTitle, wallpaperTonalKeywords),
    HelixSettingsIndexEntry("night_light", nightTitle, nightKeywords),
    HelixSettingsIndexEntry("desktop", desktopTitle, desktopKeywords),
    HelixSettingsIndexEntry("appearance", appearanceTitle, appearanceKeywords),
    HelixSettingsIndexEntry("icons", iconsTitle, listOf("theme", "papirus", "tela", "candy", "match", "breezex")),
    HelixSettingsIndexEntry("windows", windowsTitle, listOf("klassy", "buttons")),
    HelixSettingsIndexEntry("cursor", cursorTitle, listOf("xcursor", "size", "breeze", "bibata", "match")),
    HelixSettingsIndexEntry("profiles", profilesTitle, listOf("vault", "profile", "portable", "appearance")),
    HelixSettingsIndexEntry("portable", portableTitle, listOf("portable", "data")),
    HelixSettingsIndexEntry("windows_shell", shellTitle, listOf("explorer", "shell")),
    HelixSettingsIndexEntry("session_sync", syncTitle, listOf("sync", "keepass")),
    HelixSettingsIndexEntry("vault", vaultTitle, listOf("luks", "expand", "compact")),
    HelixSettingsIndexEntry("portal_device", portalTitle, portalKeywords),
    HelixSettingsIndexEntry("opsec", opsecTitle, opsecKeywords),
    HelixSettingsIndexEntry("vault_restore", restoreTitle, listOf("restore", "backup")),
    HelixSettingsIndexEntry("update", updateTitle, listOf("update", "signed", "manifest")),
)

@Composable
private fun ThemeStudioCard(
    settings: TelosDesktopSettings,
    themes: List<IconThemeInfo>,
    cursorThemes: List<XCursorThemeInfo>,
    icons: LinuxIconThemeResolver,
    onThemesRefresh: () -> Unit,
    nightEnabled: Boolean,
    nightTemp: Float,
    nightStatus: NightLightController.Snapshot,
    onNightEnabled: (Boolean) -> Unit,
    onNightTemp: (Float) -> Unit,
    onNightTempFinished: () -> Unit,
    onWallpaperAccent: (Boolean) -> Unit = {},
) {
    val t = com.telos.desktop.i18n.TelosI18n
    var intensity by remember {
        mutableStateOf(ThemeIntensity.parse(settings.themeIntensity))
    }
    var accent by remember {
        mutableStateOf(ThemeAccent.parse(settings.themeAccent))
    }
    var density by remember {
        mutableStateOf(ThemeDensity.parse(settings.themeDensity))
    }
    var panelHeight by remember { mutableStateOf(settings.panelHeightDp) }
    var panelIcon by remember { mutableStateOf(settings.panelIconDp) }
    var panelMode by remember { mutableStateOf(PanelSurfaceMode.parse(settings.panelSurfaceMode)) }
    var panelAlpha by remember { mutableStateOf(settings.panelAlpha) }
    var panelBlur by remember { mutableStateOf(settings.panelBlur) }
    var iconOpen by remember { mutableStateOf(false) }
    var cursorOpen by remember { mutableStateOf(false) }
    var matchBusyId by remember { mutableStateOf<String?>(null) }
    var matchStatus by remember { mutableStateOf<String?>(null) }
    val matchScope = rememberCoroutineScope()

    HelixSettingsCard(t.t("desktop_theme_studio_title")) {
        Text(t.t("desktop_theme_studio_hint"), fontSize = 11.sp, color = TelosColors.onSurface.copy(0.7f))

        Toggle(
            t.t("desktop_panic_mute_toggle"),
            settings.panicMute || com.telos.desktop.theme.PanicMuteController.sessionMuted,
        ) {
            com.telos.desktop.theme.PanicMuteController.setMuted(it, settings, persist = true)
        }
        Text(
            t.t("desktop_panic_mute_hint"),
            fontSize = 10.sp,
            color = TelosColors.onSurface.copy(0.6f),
        )

        Text(t.t("desktop_theme_intensity"), fontSize = 12.sp, color = TelosColors.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HelixTechPill(
                t.t("desktop_theme_quiet_ops"),
                selected = intensity == ThemeIntensity.QuietOps,
                accent = TelosColors.secondary,
            ) {
                intensity = ThemeIntensity.QuietOps
                settings.applyThemeIntensity(intensity)
                settings.save()
            }
            HelixTechPill(
                t.t("desktop_theme_vault_neon"),
                selected = intensity == ThemeIntensity.VaultNeon,
                accent = Color(0xFFFF007F),
            ) {
                intensity = ThemeIntensity.VaultNeon
                settings.applyThemeIntensity(intensity)
                settings.save()
            }
            HelixTechPill(
                t.t("desktop_theme_paper_ops"),
                selected = intensity == ThemeIntensity.PaperOps,
                accent = Color(0xFF00838F),
            ) {
                intensity = ThemeIntensity.PaperOps
                settings.applyThemeIntensity(intensity)
                settings.save()
            }
        }

        Text(t.t("desktop_theme_accent"), fontSize = 12.sp, color = TelosColors.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeAccent.NEON_PRESETS.forEach { preset ->
                val selected = accent == preset
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(preset.color)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                TelosColors.onSurface
                            } else {
                                preset.color.copy(alpha = DesktopThemeChrome.techBorderAlpha)
                            },
                            shape = CircleShape,
                        )
                        .clickable {
                            accent = preset
                            settings.applyThemeAccent(preset)
                            settings.save()
                            onWallpaperAccent(false)
                            com.telos.desktop.theme.WallpaperTonalSeed.clearCache()
                            DesktopChrome.bumpAccentEpoch()
                        },
                )
            }
        }
        Toggle(t.t("desktop_theme_from_wallpaper"), accent == ThemeAccent.FromWallpaper) { on ->
            accent = if (on) ThemeAccent.FromWallpaper else ThemeAccent.NeonMagenta
            settings.applyThemeAccent(accent)
            settings.save()
            onWallpaperAccent(on)
            com.telos.desktop.theme.WallpaperTonalSeed.clearCache()
            DesktopChrome.bumpAccentEpoch()
        }
        Text(t.t("desktop_theme_from_wallpaper_hint"), fontSize = 10.sp, color = TelosColors.onSurface.copy(0.6f))

        Text(t.t("desktop_theme_density"), fontSize = 12.sp, color = TelosColors.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ThemeDensity.Compact to t.t("desktop_theme_density_compact"),
                ThemeDensity.Comfort to t.t("desktop_theme_density_comfort"),
                ThemeDensity.Touch to t.t("desktop_theme_density_touch"),
            ).forEach { (dens, label) ->
                HelixTechPill(label, selected = density == dens, accent = TelosColors.secondary) {
                    density = dens
                    settings.applyThemeDensity(dens)
                    panelHeight = dens.panelHeightDp
                    panelIcon = dens.panelIconDp
                    settings.save()
                }
            }
        }

        Text(
            t.t("desktop_theme_panel_height", panelHeight.toInt()),
            fontSize = 11.sp,
            color = TelosColors.onSurface,
        )
        Slider(
            value = ((panelHeight - 32f) / 16f).coerceIn(0f, 1f),
            onValueChange = {
                panelHeight = (32f + it * 16f).coerceIn(32f, 48f)
                settings.panelHeightDp = panelHeight
            },
            onValueChangeFinished = { settings.save() },
        )
        Text(
            t.t("desktop_theme_panel_icon", panelIcon.toInt()),
            fontSize = 11.sp,
            color = TelosColors.onSurface,
        )
        Slider(
            value = ((panelIcon - 20f) / 16f).coerceIn(0f, 1f),
            onValueChange = {
                panelIcon = (20f + it * 16f).coerceIn(20f, 36f)
                settings.panelIconDp = panelIcon
            },
            onValueChangeFinished = { settings.save() },
        )
        Text(t.t("desktop_theme_panel_surface"), fontSize = 12.sp, color = TelosColors.onSurface)
        Text(
            t.t("desktop_theme_panel_surface_hint"),
            fontSize = 10.sp,
            color = TelosColors.onSurface.copy(0.6f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HelixTechPill(
                t.t("desktop_theme_panel_transparent"),
                selected = panelMode == PanelSurfaceMode.Transparent,
                accent = TelosColors.secondary,
            ) {
                panelMode = PanelSurfaceMode.Transparent
                settings.panelSurfaceMode = panelMode.propertyValue
                settings.save()
            }
            HelixTechPill(
                t.t("desktop_theme_panel_blurred"),
                selected = panelMode == PanelSurfaceMode.Blurred,
                accent = Color(0xFF00E676),
            ) {
                panelMode = PanelSurfaceMode.Blurred
                settings.panelSurfaceMode = panelMode.propertyValue
                settings.save()
            }
            HelixTechPill(
                t.t("desktop_theme_panel_solid"),
                selected = panelMode == PanelSurfaceMode.Solid,
                accent = TelosColors.primary,
            ) {
                panelMode = PanelSurfaceMode.Solid
                settings.panelSurfaceMode = panelMode.propertyValue
                settings.save()
            }
        }
        val resolved = PanelSurfaceMapping.resolve(panelMode, panelAlpha, panelBlur)
        if (resolved.frostPlateFallback) {
            Text(
                t.t("desktop_theme_panel_blur_unavailable"),
                fontSize = 10.sp,
                color = TelosColors.secondary.copy(0.85f),
            )
        }
        if (panelMode != PanelSurfaceMode.Solid) {
            Text(
                t.t("desktop_theme_panel_opacity", (panelAlpha * 100).toInt()),
                fontSize = 11.sp,
                color = TelosColors.onSurface,
            )
            Slider(
                value = panelAlpha.coerceIn(0.2f, 1f),
                onValueChange = {
                    panelAlpha = it.coerceIn(0.2f, 1f)
                    settings.panelAlpha = panelAlpha
                },
                onValueChangeFinished = { settings.save() },
            )
        }
        if (panelMode == PanelSurfaceMode.Blurred) {
            Text(
                t.t("desktop_theme_panel_blur", panelBlur.toInt()),
                fontSize = 11.sp,
                color = TelosColors.onSurface,
            )
            Slider(
                value = (panelBlur / 32f).coerceIn(0f, 1f),
                onValueChange = {
                    panelBlur = (it * 32f).coerceIn(0f, 32f)
                    settings.panelBlur = panelBlur
                },
                onValueChangeFinished = { settings.save() },
            )
        }

        Text(t.t("desktop_panel_icon_contrast"), fontSize = 12.sp, color = TelosColors.onSurface)
        Text(
            t.t("desktop_panel_icon_contrast_hint"),
            fontSize = 10.sp,
            color = TelosColors.onSurface.copy(0.6f),
        )
        val contrastMode = PanelIconContrast.parseMode(settings.panelIconContrast)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HelixTechPill(
                t.t("desktop_panel_icon_contrast_auto"),
                selected = contrastMode == PanelIconContrast.Mode.Auto,
                accent = TelosColors.secondary,
            ) {
                settings.panelIconContrast = PanelIconContrast.Mode.Auto.propertyValue
                PanelIconContrast.clearCache()
                settings.save()
            }
            HelixTechPill(
                t.t("desktop_panel_icon_contrast_light"),
                selected = contrastMode == PanelIconContrast.Mode.Light,
                accent = TelosColors.primary,
            ) {
                settings.panelIconContrast = PanelIconContrast.Mode.Light.propertyValue
                PanelIconContrast.clearCache()
                settings.save()
            }
            HelixTechPill(
                t.t("desktop_panel_icon_contrast_dark"),
                selected = contrastMode == PanelIconContrast.Mode.Dark,
                accent = TelosColors.primary,
            ) {
                settings.panelIconContrast = PanelIconContrast.Mode.Dark.propertyValue
                PanelIconContrast.clearCache()
                settings.save()
            }
        }

        Text(t.t("desktop_icons_theme_label"), fontSize = 12.sp, color = TelosColors.onSurface)
        val selectedIconLabel = themes.firstOrNull { it.name == settings.iconTheme }?.displayName
            ?: settings.iconTheme
        TextButton(onClick = {
            onThemesRefresh()
            iconOpen = true
        }) { Text(selectedIconLabel) }
        DropdownMenu(
            expanded = iconOpen,
            onDismissRequest = { iconOpen = false },
            modifier = Modifier.background(TelosColors.menu),
        ) {
            themes.forEach { theme ->
                DropdownMenuItem(
                    text = { Text(theme.displayName) },
                    onClick = {
                        settings.iconTheme = theme.name
                        settings.save()
                        icons.preferredTheme = theme.name
                        iconOpen = false
                    },
                )
            }
        }

        Text(t.t("desktop_cursor_theme"), fontSize = 12.sp, color = TelosColors.onSurface)
        TextButton(onClick = {
            onThemesRefresh()
            cursorOpen = true
        }) {
            Text(
                settings.cursorTheme.ifBlank { t.t("desktop_cursor_system_default") },
            )
        }
        DropdownMenu(
            expanded = cursorOpen,
            onDismissRequest = { cursorOpen = false },
            modifier = Modifier.background(TelosColors.menu),
        ) {
            DropdownMenuItem(
                text = { Text(t.t("desktop_cursor_system_default")) },
                onClick = {
                    settings.cursorTheme = ""
                    settings.save()
                    reapplyCursor(settings)
                    cursorOpen = false
                },
            )
            cursorThemes.forEach { theme ->
                DropdownMenuItem(
                    text = { Text(theme.displayName) },
                    onClick = {
                        settings.cursorTheme = theme.name
                        settings.save()
                        reapplyCursor(settings)
                        cursorOpen = false
                    },
                )
            }
        }

        Text(t.t("desktop_matched_packs_title"), fontSize = 12.sp, color = TelosColors.onSurface)
        Text(
            t.t("desktop_matched_packs_hint"),
            fontSize = 10.sp,
            color = TelosColors.onSurface.copy(0.6f),
        )
        MatchedThemePacks.all.forEach { pack ->
            TextButton(
                onClick = {
                    if (matchBusyId != null) return@TextButton
                    matchBusyId = pack.id
                    matchStatus = t.t("desktop_matched_packs_working")
                    matchScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            MatchedPackInstaller.apply(pack, settings, icons, downloadMissing = true)
                        }
                        onThemesRefresh()
                        if (result.ok) {
                            reapplyCursor(settings)
                        }
                        matchBusyId = null
                        matchStatus = if (result.ok) {
                            t.t("desktop_matched_packs_ok", pack.displayName)
                        } else {
                            t.t("desktop_matched_packs_fail", result.message)
                        }
                    }
                },
                enabled = matchBusyId == null,
            ) {
                Text(t.t("desktop_matched_packs_apply", pack.displayName))
            }
        }
        matchStatus?.let { status ->
            Text(status, fontSize = 10.sp, color = TelosColors.secondary)
        }

        Text(t.t("desktop_night_light_title"), fontSize = 12.sp, color = TelosColors.onSurface)
        Toggle(t.t("desktop_night_light_enable"), nightEnabled, onNightEnabled)
        Text(
            t.t("desktop_night_light_temperature", NightLightController.clampTemp(nightTemp.toInt())),
            fontSize = 11.sp,
            color = TelosColors.onSurface,
        )
        Slider(
            value = ((nightTemp - NightLightController.MIN_TEMP_K) /
                (NightLightController.MAX_TEMP_K - NightLightController.MIN_TEMP_K).toFloat())
                .coerceIn(0f, 1f),
            onValueChange = {
                onNightTemp(
                    NightLightController.MIN_TEMP_K +
                        it * (NightLightController.MAX_TEMP_K - NightLightController.MIN_TEMP_K),
                )
            },
            onValueChangeFinished = onNightTempFinished,
        )
        Text(
            when (nightStatus.status) {
                NightLightController.ApplyStatus.PreferenceOnly ->
                    t.t("desktop_night_light_pref_only")
                NightLightController.ApplyStatus.Failed ->
                    t.t("desktop_night_light_failed")
                NightLightController.ApplyStatus.Applied ->
                    t.t("desktop_night_light_applied", nightStatus.backend.name.lowercase())
                NightLightController.ApplyStatus.Cleared ->
                    t.t("desktop_night_light_cleared")
            },
            fontSize = 11.sp,
            color = TelosColors.secondary,
        )
    }
}

@Composable
private fun Vos5RestoreAndUpdateCard(
    settings: TelosDesktopSettings,
    show: (String) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    var restorePass by remember { mutableStateOf("") }
    var restoreMsg by remember { mutableStateOf<String?>(null) }
    var restorePreview by remember { mutableStateOf<DesktopVaultRestore.RestorePreview?>(null) }
    var updateUrl by remember {
        mutableStateOf(DesktopSignedUpdate.preferredManifestUrl(settings).orEmpty())
    }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var updateOffer by remember { mutableStateOf<DesktopSignedUpdate.VerifiedOffer?>(null) }
    var stagedUpdateJar by remember { mutableStateOf<File?>(null) }
    var stagedUpdateVersion by remember { mutableStateOf<String?>(null) }
    var luksBackupPath by remember {
        mutableStateOf(DesktopLuksImageRestore.defaultDestImage().absolutePath)
    }
    var luksMsg by remember { mutableStateOf<String?>(null) }
    var luksPreview by remember { mutableStateOf<DesktopLuksImageRestore.Preview?>(null) }

    if (show("vault_restore")) HelixSettingsCard(com.telos.desktop.i18n.TelosI18n.t("desktop_vault_restore_title")) {
        Text(com.telos.desktop.i18n.TelosI18n.t("desktop_vault_restore_hint"), fontSize = 12.sp)
        OutlinedTextField(
            restorePass,
            onValueChange = { restorePass = it },
            label = {
                Text(
                    com.telos.desktop.i18n.TelosI18n.t(
                        "desktop_session_sync_pass",
                        DesktopSessionSync.DEFAULT_KEEPASS_ENTRY,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = {
            scope.launch(Dispatchers.IO) {
                val pass = restorePass.toCharArray()
                if (pass.isEmpty()) {
                    restoreMsg = com.telos.desktop.i18n.TelosI18n.t("desktop_session_sync_pass_required")
                    restorePreview = null
                    return@launch
                }
                val r = DesktopVaultRestore.preview(DesktopVaultRestore.defaultBundle(), pass)
                pass.fill('\u0000')
                if (r.isSuccess) {
                    val prev = r.getOrThrow()
                    restorePreview = prev
                    restoreMsg = com.telos.desktop.i18n.TelosI18n.t(
                        "desktop_vault_restore_preview",
                        prev.telosConfigFileCount.toString(),
                        if (prev.hasDesktopProperties) "yes" else "no",
                    )
                } else {
                    restorePreview = null
                    restoreMsg = r.exceptionOrNull()?.message
                }
            }
        }) {
            Text(com.telos.desktop.i18n.TelosI18n.t("desktop_vault_restore_preview_btn"))
        }
        restorePreview?.let { preview ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val pass = restorePass.toCharArray()
                        val r = DesktopVaultRestore.applyConfirmed(
                            preview.bundle,
                            pass,
                            confirmToken = preview.confirmToken,
                            expectedToken = preview.confirmToken,
                            vaultSettings = settings,
                        )
                        pass.fill('\u0000')
                        restorePreview = null
                        restoreMsg = if (r.isSuccess) {
                            com.telos.desktop.i18n.TelosI18n.t("desktop_vault_restore_done")
                        } else {
                            r.exceptionOrNull()?.message
                                ?: com.telos.desktop.i18n.TelosI18n.t("desktop_vault_restore_need_confirm")
                        }
                    }
                }) {
                    Text(com.telos.desktop.i18n.TelosI18n.t("desktop_vault_restore_confirm"))
                }
                TextButton(onClick = {
                    restorePreview = null
                    restoreMsg = com.telos.desktop.i18n.TelosI18n.t("desktop_vault_restore_cancel")
                }) {
                    Text(com.telos.desktop.i18n.TelosI18n.t("desktop_vault_restore_cancel"))
                }
            }
        }
        restoreMsg?.let { Text(it, fontSize = 11.sp, color = TelosColors.primary) }
    }

    HelixSettingsCard(com.telos.desktop.i18n.TelosI18n.t("desktop_luks_restore_title")) {
        Text(com.telos.desktop.i18n.TelosI18n.t("desktop_luks_restore_hint"), fontSize = 12.sp)
        OutlinedTextField(
            luksBackupPath,
            onValueChange = { luksBackupPath = it },
            label = { Text(com.telos.desktop.i18n.TelosI18n.t("desktop_luks_restore_path_label")) },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = {
            scope.launch(Dispatchers.IO) {
                val unlocked = runCatching { PhantomVault.engine.unlocked }.getOrDefault(false)
                val backup = File(luksBackupPath.trim())
                val r = DesktopLuksImageRestore.preview(
                    backup = backup,
                    destImage = DesktopLuksImageRestore.defaultDestImage(),
                    vaultUnlocked = unlocked,
                )
                if (r.isSuccess) {
                    val prev = r.getOrThrow()
                    luksPreview = prev
                    luksMsg = com.telos.desktop.i18n.TelosI18n.t(
                        "desktop_luks_restore_preview",
                        prev.backupBytes.toString(),
                        prev.backupSha256.take(16),
                    )
                } else {
                    luksPreview = null
                    luksMsg = r.exceptionOrNull()?.message
                        ?: com.telos.desktop.i18n.TelosI18n.t("desktop_luks_restore_locked_required")
                }
            }
        }) {
            Text(com.telos.desktop.i18n.TelosI18n.t("desktop_luks_restore_preview_btn"))
        }
        luksPreview?.let { preview ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val unlocked = runCatching { PhantomVault.engine.unlocked }.getOrDefault(false)
                        val r = DesktopLuksImageRestore.applyConfirmed(
                            backup = preview.backup,
                            destImage = preview.destImage,
                            expectedSha256 = preview.backupSha256,
                            confirmToken = preview.confirmToken,
                            expectedToken = preview.confirmToken,
                            vaultUnlocked = unlocked,
                        )
                        luksPreview = null
                        luksMsg = if (r.isSuccess) {
                            com.telos.desktop.i18n.TelosI18n.t(
                                "desktop_luks_restore_done",
                                r.getOrThrow().destImage.name,
                            )
                        } else {
                            r.exceptionOrNull()?.message
                                ?: com.telos.desktop.i18n.TelosI18n.t("desktop_luks_restore_need_confirm")
                        }
                    }
                }) {
                    Text(com.telos.desktop.i18n.TelosI18n.t("desktop_luks_restore_confirm"))
                }
                TextButton(onClick = {
                    luksPreview = null
                    luksMsg = com.telos.desktop.i18n.TelosI18n.t("desktop_luks_restore_cancel")
                }) {
                    Text(com.telos.desktop.i18n.TelosI18n.t("desktop_luks_restore_cancel"))
                }
            }
        }
        luksMsg?.let { Text(it, fontSize = 11.sp, color = TelosColors.primary) }
    }

    if (show("update")) HelixSettingsCard(com.telos.desktop.i18n.TelosI18n.t("desktop_update_title")) {
        Text(com.telos.desktop.i18n.TelosI18n.t("desktop_update_hint"), fontSize = 12.sp)
        Text(
            com.telos.desktop.i18n.TelosI18n.t(
                "desktop_update_releases_hint",
                DesktopGitHubReleasesChannel.latestDownloadUrl(),
            ),
            fontSize = 11.sp,
        )
        OutlinedTextField(
            updateUrl,
            onValueChange = { updateUrl = it },
            label = { Text(com.telos.desktop.i18n.TelosI18n.t("desktop_update_url_label")) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                DesktopSignedUpdate.saveManifestUrl(settings, updateUrl)
                updateMsg = com.telos.desktop.i18n.TelosI18n.t("desktop_update_url_saved")
            }) {
                Text(com.telos.desktop.i18n.TelosI18n.t("desktop_update_save_url"))
            }
            TextButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    updateUrl = DesktopGitHubReleasesChannel.latestDownloadUrl()
                    DesktopSignedUpdate.saveManifestUrl(settings, updateUrl)
                    updateMsg = com.telos.desktop.i18n.TelosI18n.t("desktop_update_releases_filled")
                }
            }) {
                Text(com.telos.desktop.i18n.TelosI18n.t("desktop_update_use_releases"))
            }
            TextButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    DesktopSignedUpdate.saveManifestUrl(settings, updateUrl)
                    val url = updateUrl.trim().ifEmpty {
                        DesktopSignedUpdate.preferredManifestUrl(settings).orEmpty()
                    }
                    if (url.isEmpty()) {
                        updateOffer = null
                        stagedUpdateJar = null
                        updateMsg = com.telos.desktop.i18n.TelosI18n.t("desktop_update_no_url")
                        return@launch
                    }
                    val result = DesktopSignedUpdate.check(url)
                    when (result.status) {
                        DesktopSignedUpdate.CheckStatus.ReadyForConfirm -> {
                            updateOffer = result.offer
                            stagedUpdateJar = null
                            updateMsg = com.telos.desktop.i18n.TelosI18n.t(
                                "desktop_update_ready",
                                result.offer?.version.orEmpty(),
                            )
                        }
                        DesktopSignedUpdate.CheckStatus.SignatureInvalid -> {
                            updateOffer = null
                            stagedUpdateJar = null
                            updateMsg = com.telos.desktop.i18n.TelosI18n.t("desktop_update_sig_fail")
                        }
                        else -> {
                            updateOffer = null
                            stagedUpdateJar = null
                            updateMsg = result.detail.ifBlank {
                                com.telos.desktop.i18n.TelosI18n.t("desktop_update_no_url")
                            }
                        }
                    }
                }
            }) {
                Text(com.telos.desktop.i18n.TelosI18n.t("desktop_update_check"))
            }
        }
        updateOffer?.let { offer ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val r = DesktopSignedUpdate.installConfirmed(offer, offer.confirmToken)
                        updateOffer = null
                        if (r.isSuccess) {
                            val dest = r.getOrThrow()
                            stagedUpdateJar = dest
                            stagedUpdateVersion = offer.version
                            updateMsg = com.telos.desktop.i18n.TelosI18n.t(
                                "desktop_update_installed",
                                dest.name,
                            )
                        } else {
                            stagedUpdateJar = null
                            updateMsg = r.exceptionOrNull()?.message
                        }
                    }
                }) {
                    Text(com.telos.desktop.i18n.TelosI18n.t("desktop_update_install_confirm"))
                }
                TextButton(onClick = {
                    updateOffer = null
                    updateMsg = com.telos.desktop.i18n.TelosI18n.t("desktop_update_install_cancel")
                }) {
                    Text(com.telos.desktop.i18n.TelosI18n.t("desktop_update_install_cancel"))
                }
            }
        }
        stagedUpdateJar?.let { staged ->
            Text(com.telos.desktop.i18n.TelosI18n.t("desktop_update_restart_hint"), fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        // User-confirmed restart: marker + optional replace script, then exit.
                        val r = DesktopUpdateRestartHelper.prepareRestartAfterUpdate(
                            stagedJar = staged,
                            version = stagedUpdateVersion,
                            exitAfterSpawn = true,
                        )
                        if (r.isFailure) {
                            updateMsg = r.exceptionOrNull()?.message
                                ?: com.telos.desktop.i18n.TelosI18n.t("desktop_update_restart_failed")
                        }
                    }
                }) {
                    Text(com.telos.desktop.i18n.TelosI18n.t("desktop_update_restart_confirm"))
                }
                TextButton(onClick = {
                    stagedUpdateJar = null
                    stagedUpdateVersion = null
                    updateMsg = com.telos.desktop.i18n.TelosI18n.t("desktop_update_restart_later")
                }) {
                    Text(com.telos.desktop.i18n.TelosI18n.t("desktop_update_restart_later"))
                }
            }
        }
        updateMsg?.let { Text(it, fontSize = 11.sp, color = TelosColors.primary) }
    }
}

@Composable
private fun HelixSettingsCard(title: String, body: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = com.telos.desktop.theme.DesktopThemeChrome.resolvedAccent
        .copy(alpha = com.telos.desktop.theme.DesktopThemeChrome.techBorderAlpha)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            // Opaque card — never a second blur / cleared surface.
            .background(TelosSurfaceTokens.cardOpaque)
            .border(1.dp, borderColor, shape)
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
