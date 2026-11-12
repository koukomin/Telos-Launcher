package de.mm20.launcher2.ui.settings.freeze
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.system.SystemDashboardRoute
import kotlinx.serialization.Serializable

/** Legacy route retained for navigation/backups; opens Freeze Manager Rules entry. */
@Serializable
data object SmartFreezeDashboardRoute : NavKey

/**
 * Thin Rules alias for Freeze Manager tab 0.
 * Per-app freeze lives on Telos System → Apps (Freeze Desk).
 */
@Composable
fun SmartFreezeDashboardScreen(tabs: @Composable () -> Unit = {}) {
    val vm: FreezeSettingsScreenVM = viewModel()
    val backend by vm.activeBackend.collectAsStateWithLifecycle()
    val system by vm.showSystemApps.collectAsStateWithLifecycle()
    val iconless by vm.showIconlessApps.collectAsStateWithLifecycle()
    val backStack = LocalBackStack.current
    LifecycleResumeEffect(Unit) {
        vm.refreshBackendState()
        onPauseOrDispose { }
    }
    PreferenceScreen(title = stringResource(I18nR.string.preference_screen_freeze)) {
        item { tabs() }
        item {
            Preference(
                title = stringResource(I18nR.string.freeze_desk_title),
                summary = stringResource(I18nR.string.freeze_desk_open_summary),
                onClick = { backStack.add(SystemDashboardRoute("apps")) },
            )
            Preference(
                title = stringResource(I18nR.string.hf_freeze_backend_status),
                summary = backend?.name ?: stringResource(I18nR.string.hf_freeze_disconnected),
                onClick = { vm.refreshBackendState() },
            )
            Text(
                stringResource(I18nR.string.freeze_desk_rules_help),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            SwitchPreference(
                title = stringResource(I18nR.string.preference_freeze_show_system_apps),
                value = system,
                onValueChanged = vm::setShowSystemApps,
            )
            SwitchPreference(
                title = stringResource(I18nR.string.preference_freeze_show_iconless_apps),
                value = iconless,
                onValueChanged = vm::setShowIconlessApps,
            )
        }
    }
}
