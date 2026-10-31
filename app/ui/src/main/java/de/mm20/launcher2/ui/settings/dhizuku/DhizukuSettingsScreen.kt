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
        title = { Text("Dhizuku Device Policy Hub") },
    ) {
        item {
            PreferenceCategory(title = "Dhizuku Status") {
                if (!isAvailable) {
                    Banner(
                        text = "Dhizuku is not running or selected. Install and set up Dhizuku from Store to grant Device Owner delegation.",
                        icon = BaseR.drawable.error_24px,
                    )
                    Preference(
                        icon = BaseR.drawable.download_24px,
                        title = "Get Dhizuku from Telos Store",
                        summary = "Download and install the official Dhizuku app",
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
                        title = "Dhizuku Device Owner Active",
                        summary = if (hasPermission) "Permission Granted (Device Owner Delegated)" else "Tap to grant Dhizuku permission",
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
            PreferenceCategory(title = "Device Owner Delegated Capabilities") {
                Preference(
                    icon = BaseR.drawable.ac_unit_24px,
                    title = "App Suspension & Hiding",
                    summary = "Suspend and hide applications via Dhizuku DevicePolicyManager",
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
