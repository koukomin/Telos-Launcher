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

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setVideoWallpaper(uri) { ok ->
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
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                )
                if (viewModel.hasVideoWallpaper) {
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
            }
        }
    }
}
