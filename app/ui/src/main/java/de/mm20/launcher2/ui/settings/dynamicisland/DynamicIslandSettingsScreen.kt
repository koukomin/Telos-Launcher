package de.mm20.launcher2.ui.settings.dynamicisland

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.islandoverlay.DynamicIslandService
import kotlinx.serialization.Serializable

@Serializable
data object DynamicIslandSettingsRoute : NavKey

@Composable
fun DynamicIslandSettingsScreen() {
    val viewModel: DynamicIslandSettingsScreenVM =
        viewModel(factory = DynamicIslandSettingsScreenVM.Factory)
    val context = LocalContext.current

    val hasOverlayPermission by viewModel.hasOverlayPermission.collectAsStateWithLifecycle(null)
    val hasPhoneStatePermission by viewModel.hasPhoneStatePermission.collectAsStateWithLifecycle(null)
    val enabled by viewModel.enabled.collectAsStateWithLifecycle(false)
    val showCalls by viewModel.showCalls.collectAsStateWithLifecycle(true)

    PreferenceScreen(title = stringResource(R.string.preference_screen_dynamic_island)) {
        item {
            PreferenceCategory {
                GuardedPreference(
                    locked = hasOverlayPermission == false,
                    description = stringResource(R.string.missing_permission_dynamic_island),
                    onUnlock = {
                        viewModel.requestOverlayPermission(context as AppCompatActivity)
                    }
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_dynamic_island),
                        summary = stringResource(R.string.preference_dynamic_island_summary),
                        enabled = hasOverlayPermission != false,
                        value = enabled && hasOverlayPermission == true,
                        onValueChanged = {
                            viewModel.setEnabled(it)
                            val serviceIntent = Intent(context, DynamicIslandService::class.java)
                            if (it) {
                                ContextCompat.startForegroundService(context, serviceIntent)
                            } else {
                                context.stopService(serviceIntent)
                            }
                        }
                    )
                }
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_dynamic_island_sources)) {
                GuardedPreference(
                    locked = hasPhoneStatePermission == false,
                    description = stringResource(R.string.missing_permission_dynamic_island_calls),
                    onUnlock = {
                        viewModel.requestPhoneStatePermission(context as AppCompatActivity)
                    }
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_dynamic_island_calls),
                        summary = stringResource(R.string.preference_dynamic_island_calls_summary),
                        value = showCalls && hasPhoneStatePermission == true,
                        enabled = hasPhoneStatePermission != false,
                        onValueChanged = { viewModel.setShowCalls(it) },
                    )
                }
            }
        }
    }
}
