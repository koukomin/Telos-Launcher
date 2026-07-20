package de.mm20.launcher2.ui.settings.applock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable

@Serializable
data object AppLockWebAppsRoute : NavKey

/**
 * Per-web-app-shortcut lock picker, reached from [AppLockSettingsScreen]'s "Locked web apps"
 * row - separate from [AppLockAppsScreen] since web app shortcuts come from
 * [de.mm20.launcher2.webappshortcuts.WebAppShortcutRepository], not [de.mm20.launcher2.applications.AppRepository],
 * and locking them gates [de.mm20.launcher2.webappshortcuts.WebAppShortcutImpl.launch] directly
 * rather than watching for a foreground-package change.
 */
@Composable
fun AppLockWebAppsScreen() {
    val viewModel: AppLockWebAppsScreenVM = viewModel()
    val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
    val lockedShortcuts by viewModel.lockedShortcuts.collectAsStateWithLifecycle()

    PreferenceScreen(
        title = stringResource(R.string.preference_category_app_lock_web_apps),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(shortcuts, key = { _, it -> it.key }) { _, shortcut ->
            val icon by viewModel.getIcon(shortcut, 32.dp.value.toInt()).collectAsStateWithLifecycle(null)
            val locked = shortcut.key in lockedShortcuts
            Preference(
                title = { Text(shortcut.labelOverride ?: shortcut.label) },
                icon = { ShapedLauncherIcon(size = 32.dp, icon = { icon }) },
                onClick = { viewModel.setLocked(shortcut.key, !locked) },
                controls = {
                    Switch(
                        checked = locked,
                        onCheckedChange = { viewModel.setLocked(shortcut.key, it) },
                    )
                },
            )
        }
    }
}
