package de.mm20.launcher2.ui.settings.desktopmode
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.DesktopModeOrientation
import de.mm20.launcher2.preferences.DesktopWallpaperMode
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.component.preferences.TextPreference
import kotlinx.serialization.Serializable

@Serializable
data object DesktopModeSettingsRoute : NavKey

@Composable
fun DesktopModeSettingsScreen() {
    val viewModel: DesktopModeSettingsScreenVM = viewModel(factory = DesktopModeSettingsScreenVM.Factory)

    val enabled by viewModel.enabled.collectAsStateWithLifecycle(false)
    val orientation by viewModel.orientation.collectAsStateWithLifecycle(DesktopModeOrientation.Auto)
    val externalDisplay by viewModel.externalDisplayConnected.collectAsStateWithLifecycle(null)
    val freeformPreferenceEnabled by viewModel.freeformPreferenceEnabled.collectAsStateWithLifecycle(false)
    val freeformActiveInSystem by viewModel.freeformActiveInSystem.collectAsStateWithLifecycle(false)
    val freeformShizukuUnavailable by viewModel.freeformShizukuUnavailable.collectAsStateWithLifecycle(false)
    val freeformShizukuPermissionDenied by viewModel.freeformShizukuPermissionDenied.collectAsStateWithLifecycle(false)
    val wallpaperMode by viewModel.wallpaperMode.collectAsStateWithLifecycle(DesktopWallpaperMode.SolidColor)
    val wallpaperImageUri by viewModel.wallpaperImageUri.collectAsStateWithLifecycle(null)
    val gridIconSize by viewModel.gridIconSize.collectAsStateWithLifecycle(48)
    val keyboardShortcuts by viewModel.hardwareKeyboardShortcuts.collectAsStateWithLifecycle(true)

    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setWallpaperImage(uri) { ok ->
                Toast.makeText(
                    context,
                    if (ok) I18nR.string.wallpaper_set_success else I18nR.string.wallpaper_set_failure,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    PreferenceScreen(title = stringResource(I18nR.string.preference_screen_desktop_mode)) {
        item {
            PreferenceCategory {
                if (!viewModel.isSupportedOnThisDevice) {
                    TextPreference(
                        title = stringResource(I18nR.string.desktop_mode_unsupported),
                        value = stringResource(I18nR.string.desktop_mode_unsupported_summary),
                        summary = stringResource(I18nR.string.desktop_mode_unsupported_summary),
                        enabled = false,
                        onValueChanged = {},
                    )
                } else {
                    SwitchPreference(
                        title = stringResource(I18nR.string.preference_desktop_mode_enabled),
                        summary = stringResource(I18nR.string.preference_desktop_mode_enabled_summary),
                        value = enabled,
                        onValueChanged = { viewModel.setEnabled(it) },
                    )
                    TextPreference(
                        title = stringResource(I18nR.string.desktop_mode_display_status),
                        value = stringResource(
                            if (externalDisplay != null) I18nR.string.desktop_mode_display_connected
                            else I18nR.string.desktop_mode_display_not_connected
                        ),
                        summary = stringResource(
                            if (externalDisplay != null) I18nR.string.desktop_mode_display_connected
                            else I18nR.string.desktop_mode_display_not_connected
                        ),
                        enabled = false,
                        onValueChanged = {},
                    )
                    val kdePkg = "org.kde.kdeconnect_tp"
                    val isKdeInstalled = remember(context) {
                        runCatching { context.packageManager.getApplicationInfo(kdePkg, 0) }.isSuccess
                    }
                    Preference(
                        title = "KDE Connect Integration",
                        summary = if (isKdeInstalled) "Open KDE Connect to pair with PC or Tablet" else "Tap to install KDE Connect from Telos Store",
                        onClick = {
                            val intent = context.packageManager.getLaunchIntentForPackage(kdePkg)
                                ?: android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$kdePkg")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        }
                    )
                }
            }
        }
        if (enabled && viewModel.isSupportedOnThisDevice) {
            item {
                PreferenceCategory(title = stringResource(I18nR.string.preference_category_appearance)) {
                    ListPreference(
                        title = stringResource(I18nR.string.desktop_mode_orientation),
                        items = listOf(
                            ListPreferenceItem(
                                stringResource(I18nR.string.desktop_mode_orientation_auto),
                                DesktopModeOrientation.Auto
                            ),
                            ListPreferenceItem(
                                stringResource(I18nR.string.desktop_mode_orientation_portrait),
                                DesktopModeOrientation.Portrait
                            ),
                            ListPreferenceItem(
                                stringResource(I18nR.string.desktop_mode_orientation_landscape),
                                DesktopModeOrientation.Landscape
                            ),
                        ),
                        value = orientation,
                        onValueChanged = { viewModel.setOrientation(it) },
                    )
                    SliderPreference(
                        title = stringResource(I18nR.string.desktop_mode_icon_size),
                        value = gridIconSize,
                        step = 8,
                        min = 32,
                        max = 80,
                        onValueChanged = { viewModel.setGridIconSize(it) },
                    )
                    SwitchPreference(
                        title = stringResource(I18nR.string.preference_keyboard_shortcuts),
                        summary = stringResource(I18nR.string.preference_keyboard_shortcuts_summary),
                        value = keyboardShortcuts,
                        onValueChanged = { viewModel.setHardwareKeyboardShortcuts(it) },
                    )
                }
            }
            item {
                PreferenceCategory(title = stringResource(I18nR.string.desktop_mode_wallpaper_category)) {
                    ListPreference(
                        title = stringResource(I18nR.string.desktop_mode_wallpaper_mode),
                        items = listOf(
                            ListPreferenceItem(
                                stringResource(I18nR.string.desktop_mode_wallpaper_mode_solid_color),
                                DesktopWallpaperMode.SolidColor,
                            ),
                            ListPreferenceItem(
                                stringResource(I18nR.string.desktop_mode_wallpaper_mode_static_image),
                                DesktopWallpaperMode.StaticImage,
                            ),
                        ),
                        value = wallpaperMode,
                        onValueChanged = { viewModel.setWallpaperMode(it) },
                    )
                    Preference(
                        title = stringResource(I18nR.string.desktop_mode_wallpaper_choose_image),
                        summary = wallpaperImageUri?.let {
                            stringResource(I18nR.string.desktop_mode_wallpaper_image_set)
                        },
                        onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }
            }
            item {
                PreferenceCategory(title = stringResource(I18nR.string.desktop_mode_window_management_category)) {
                    if (!viewModel.isFreeformPotentiallySupported) {
                        TextPreference(
                            title = stringResource(I18nR.string.desktop_mode_freeform_unsupported),
                            value = stringResource(I18nR.string.desktop_mode_freeform_unsupported_summary),
                            summary = stringResource(I18nR.string.desktop_mode_freeform_unsupported_summary),
                            enabled = false,
                            onValueChanged = {},
                        )
                    } else {
                        SwitchPreference(
                            title = stringResource(I18nR.string.desktop_mode_freeform_enabled),
                            summary = stringResource(
                                if (freeformPreferenceEnabled && freeformActiveInSystem)
                                    I18nR.string.desktop_mode_freeform_enabled_summary_active
                                else if (freeformPreferenceEnabled)
                                    I18nR.string.desktop_mode_freeform_enabled_summary_pending
                                else
                                    I18nR.string.desktop_mode_freeform_enabled_summary
                            ),
                            value = freeformPreferenceEnabled,
                            onValueChanged = { viewModel.setFreeformEnabled(it) },
                        )
                        AnimatedVisibility(freeformPreferenceEnabled) {
                            Banner(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                text = stringResource(I18nR.string.desktop_mode_freeform_warning),
                                icon = BaseR.drawable.error_24px,
                            )
                        }
                        AnimatedVisibility(freeformShizukuUnavailable) {
                            Banner(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                text = stringResource(I18nR.string.desktop_mode_freeform_shizuku_unavailable),
                                icon = BaseR.drawable.error_24px,
                            )
                        }
                        AnimatedVisibility(freeformShizukuPermissionDenied) {
                            Banner(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                text = stringResource(I18nR.string.desktop_mode_freeform_shizuku_denied),
                                icon = BaseR.drawable.error_24px,
                            )
                        }
                    }
                }
            }
        }
    }
}
