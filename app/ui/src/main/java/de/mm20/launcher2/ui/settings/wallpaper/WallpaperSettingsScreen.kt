package de.mm20.launcher2.ui.settings.wallpaper

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.wallpapers.StaticWallpaperTarget
import kotlinx.serialization.Serializable

@Serializable
data object WallpaperSettingsRoute : NavKey

@Composable
fun WallpaperSettingsScreen() {
    val viewModel: WallpaperSettingsScreenVM = viewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh on every resume: the user comes back from the system live wallpaper preview.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    val pauseOnBatterySaver by viewModel.pauseOnBatterySaver.collectAsStateWithLifecycle()
    val pauseOnThermal by viewModel.pauseOnThermal.collectAsStateWithLifecycle()
    val pauseOnDesktopMode by viewModel.pauseOnDesktopMode.collectAsStateWithLifecycle()
    val videoTransforms by viewModel.videoTransforms.collectAsStateWithLifecycle()
    val videoSpeed by viewModel.videoSpeed.collectAsStateWithLifecycle()
    val videoStartBehavior by viewModel.videoStartBehavior.collectAsStateWithLifecycle()

    val setResultToast: (Boolean) -> Unit = { ok ->
        Toast.makeText(
            context,
            if (ok) R.string.wallpaper_set_success else R.string.wallpaper_set_failure,
            Toast.LENGTH_SHORT,
        ).show()
    }

    var pendingStaticTarget by remember { mutableStateOf(StaticWallpaperTarget.Both) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setStaticWallpaper(uri, pendingStaticTarget, setResultToast)
        }
    }

    var appendToPlaylist by remember { mutableStateOf(false) }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setVideoWallpaper(uri, appendToPlaylist) { ok ->
                if (ok && !viewModel.isVideoWallpaperActive) {
                    context.tryStartActivity(viewModel.getActivationIntent())
                } else {
                    setResultToast(ok)
                }
            }
        }
    }

    PreferenceScreen(title = stringResource(R.string.preference_screen_wallpaper)) {
        item {
            PreferenceCategory(title = stringResource(R.string.preference_wallpaper_category_static)) {
                Preference(
                    icon = R.drawable.wallpaper_24px,
                    title = stringResource(R.string.preference_wallpaper_set_home),
                    onClick = {
                        pendingStaticTarget = StaticWallpaperTarget.Home
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_wallpaper_set_lock),
                    onClick = {
                        pendingStaticTarget = StaticWallpaperTarget.Lock
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_wallpaper_set_both),
                    onClick = {
                        pendingStaticTarget = StaticWallpaperTarget.Both
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_wallpaper_system_picker),
                    summary = stringResource(R.string.preference_wallpaper_system_picker_summary),
                    onClick = {
                        context.tryStartActivity(
                            Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null)
                        )
                    }
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_wallpaper_category_video)) {
                Preference(
                    icon = R.drawable.wallpaper_24px,
                    title = stringResource(R.string.preference_wallpaper_choose_video),
                    summary = stringResource(R.string.preference_wallpaper_choose_video_summary),
                    onClick = {
                        appendToPlaylist = false
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_wallpaper_add_video),
                    onClick = {
                        appendToPlaylist = true
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                )
                if (viewModel.hasVideoWallpaper) {
                    Preference(
                        title = stringResource(R.string.preference_wallpaper_clear_playlist),
                        onClick = { viewModel.clearVideoPlaylist() }
                    )
                    Preference(
                        title = stringResource(
                            if (viewModel.isVideoWallpaperActive) R.string.wallpaper_video_active
                            else R.string.wallpaper_video_inactive
                        ),
                        enabled = !viewModel.isVideoWallpaperActive,
                        onClick = {
                            context.tryStartActivity(viewModel.getActivationIntent())
                        }
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.preference_wallpaper_pause_battery_saver),
                    summary = stringResource(R.string.preference_wallpaper_pause_battery_saver_summary),
                    value = pauseOnBatterySaver == true,
                    onValueChanged = { viewModel.setPauseOnBatterySaver(it) }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_wallpaper_pause_thermal),
                    summary = stringResource(R.string.preference_wallpaper_pause_thermal_summary),
                    value = pauseOnThermal == true,
                    onValueChanged = { viewModel.setPauseOnThermal(it) }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_wallpaper_pause_desktop_mode),
                    summary = stringResource(R.string.preference_wallpaper_pause_desktop_mode_summary),
                    value = pauseOnDesktopMode == true,
                    onValueChanged = { viewModel.setPauseOnDesktopMode(it) }
                )
            }
        }
        if (viewModel.hasVideoWallpaper && videoTransforms != null) {
            val transforms = videoTransforms!!
            item {
                PreferenceCategory(title = stringResource(R.string.websearch_dialog_advanced)) {
                    ListPreference(
                        title = stringResource(R.string.preference_wallpaper_scaling_mode),
                        items = listOf(
                            stringResource(R.string.preference_wallpaper_scaling_mode_fit) to de.mm20.launcher2.preferences.VideoWallpaperScalingMode.Fit,
                            stringResource(R.string.preference_wallpaper_scaling_mode_fill) to de.mm20.launcher2.preferences.VideoWallpaperScalingMode.Fill,
                            stringResource(R.string.preference_wallpaper_scaling_mode_stretch) to de.mm20.launcher2.preferences.VideoWallpaperScalingMode.Stretch,
                        ),
                        value = transforms.scalingMode,
                        onValueChanged = { if (it != null) viewModel.setVideoScalingMode(it) }
                    )
                    SliderPreference(
                        title = stringResource(R.string.preference_wallpaper_brightness),
                        value = transforms.brightness,
                        min = 0f,
                        max = 2f,
                        onValueChanged = { viewModel.setVideoBrightness(it) },
                    )
                    SliderPreference(
                        title = stringResource(R.string.preference_wallpaper_zoom),
                        value = transforms.zoom,
                        min = 0.5f,
                        max = 5f,
                        onValueChanged = { viewModel.setVideoZoom(it) },
                    )
                    SliderPreference(
                        title = stringResource(R.string.preference_wallpaper_position_x),
                        value = transforms.positionX,
                        min = -1f,
                        max = 1f,
                        onValueChanged = { viewModel.setVideoPosition(it, transforms.positionY) },
                    )
                    SliderPreference(
                        title = stringResource(R.string.preference_wallpaper_position_y),
                        value = transforms.positionY,
                        min = -1f,
                        max = 1f,
                        onValueChanged = { viewModel.setVideoPosition(transforms.positionX, it) },
                    )
                    SliderPreference(
                        title = stringResource(R.string.preference_wallpaper_speed),
                        value = videoSpeed ?: 1f,
                        min = 0.25f,
                        max = 3f,
                        onValueChanged = { viewModel.setVideoSpeed(it) },
                    )
                    ListPreference(
                        title = stringResource(R.string.preference_wallpaper_start_behavior),
                        items = listOf(
                            stringResource(R.string.preference_wallpaper_start_behavior_resume) to de.mm20.launcher2.preferences.VideoWallpaperStartBehavior.Resume,
                            stringResource(R.string.preference_wallpaper_start_behavior_restart) to de.mm20.launcher2.preferences.VideoWallpaperStartBehavior.Restart,
                            stringResource(R.string.preference_wallpaper_start_behavior_random) to de.mm20.launcher2.preferences.VideoWallpaperStartBehavior.Random,
                        ),
                        value = videoStartBehavior ?: de.mm20.launcher2.preferences.VideoWallpaperStartBehavior.Resume,
                        onValueChanged = { if (it != null) viewModel.setVideoStartBehavior(it) }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.preference_wallpaper_parallax),
                        value = transforms.parallax,
                        onValueChanged = { viewModel.setVideoParallax(it) }
                    )
                    if (transforms.parallax) {
                        SliderPreference(
                            title = stringResource(R.string.preference_wallpaper_parallax_strength),
                            value = transforms.parallaxStrength,
                            min = 0.1f,
                            max = 1f,
                            onValueChanged = { viewModel.setVideoParallaxStrength(it) },
                        )
                    }
                }
            }
        }
    }
}
