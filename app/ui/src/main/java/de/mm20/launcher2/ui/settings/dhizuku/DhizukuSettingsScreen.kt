package de.mm20.launcher2.ui.settings.dhizuku

import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.settings.SettingsActivity
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlinx.serialization.Serializable

@Serializable
data object DhizukuSettingsRoute : NavKey

@Composable
fun DhizukuSettingsScreen() {
    val context = LocalContext.current
    val freezeManager: FreezeManager = koinInject()
    val scope = rememberCoroutineScope()
    var isAvailable by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }

    fun refreshState() {
        scope.launch {
            freezeManager.refreshBackendState()
            isAvailable = freezeManager.activeBackend.value != null
            hasPermission = freezeManager.hasPermission()
        }
    }

    LaunchedEffect(Unit) {
        refreshState()
    }

    PreferenceScreen(
        title = { Text(stringResource(I18nR.string.dhizuku_hub_title)) },
    ) {
        item {
            PreferenceCategory(title = stringResource(I18nR.string.dhizuku_status)) {
                if (!isAvailable) {
                    Banner(
                        text = stringResource(I18nR.string.dhizuku_not_running),
                        icon = BaseR.drawable.error_24px,
                    )
                    Preference(
                        icon = BaseR.drawable.download_24px,
                        title = stringResource(I18nR.string.dhizuku_get_from_store),
                        summary = stringResource(I18nR.string.dhizuku_download_summary),
                        onClick = {
                            context.startActivity(
                                Intent(context, SettingsActivity::class.java)
                                    .putExtra(SettingsActivity.EXTRA_ROUTE, SettingsActivity.ROUTE_STORE)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    )
                } else {
                    Preference(
                        icon = BaseR.drawable.lock_24px,
                        title = stringResource(I18nR.string.dhizuku_active_title),
                        summary = if (hasPermission) stringResource(I18nR.string.freeze_permission_granted) else stringResource(I18nR.string.freeze_permission_not_granted),
                        onClick = {
                            if (!hasPermission) {
                                (context as? AppCompatActivity)?.let {
                                    scope.launch {
                                        freezeManager.requestPermission()
                                        refreshState()
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        item {
            PreferenceCategory(title = stringResource(I18nR.string.dhizuku_capabilities)) {
                Preference(
                    icon = BaseR.drawable.ac_unit_24px,
                    title = stringResource(I18nR.string.dhizuku_suspension_hiding),
                    summary = stringResource(I18nR.string.dhizuku_suspension_summary),
                    onClick = {
                        context.startActivity(
                            Intent(context, SettingsActivity::class.java)
                                .putExtra(SettingsActivity.EXTRA_ROUTE, "settings/freeze")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }
        }
    }
}
