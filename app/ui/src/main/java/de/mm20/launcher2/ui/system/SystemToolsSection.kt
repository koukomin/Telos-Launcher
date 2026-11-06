package de.mm20.launcher2.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.applock.AppLockSettingsRoute
import de.mm20.launcher2.ui.settings.art.ArtOptimizerRoute
import de.mm20.launcher2.ui.settings.backup.BackupSettingsRoute
import de.mm20.launcher2.ui.settings.contextprofiles.ContextProfilesSettingsRoute
import de.mm20.launcher2.ui.settings.debug.DebugSettingsRoute
import de.mm20.launcher2.ui.settings.performance.PerformanceSettingsRoute
import de.mm20.launcher2.ui.settings.plugins.PluginsSettingsRoute
import de.mm20.launcher2.ui.settings.stealth.StealthCloakRoute
import de.mm20.launcher2.ui.settings.freeze.FreezeSettingsRoute
import de.mm20.launcher2.ui.settings.workprofile.WorkProfileSettingsRoute

/**
 * Launcher tools that used to live under Settings → Advanced.
 * Graphics, saved Wi-Fi, Shizuku and Privacy Shield stay on their existing System tabs.
 */
@Composable
fun SystemToolsSection() {
    val backStack = LocalBackStack.current
    PreferenceCategory(title = stringResource(I18nR.string.preference_screen_advanced)) {
        Preference(
            icon = BaseR.drawable.ac_unit_24px,
            title = stringResource(I18nR.string.preference_screen_freeze),
            summary = stringResource(I18nR.string.preference_screen_freeze_summary),
            onClick = { backStack.add(FreezeSettingsRoute) },
        )
        Preference(
            icon = BaseR.drawable.speed_24px,
            title = stringResource(I18nR.string.preference_screen_performance),
            summary = stringResource(I18nR.string.preference_screen_performance_summary),
            onClick = { backStack.add(PerformanceSettingsRoute) },
        )
        Preference(
            icon = BaseR.drawable.bolt_24px,
            title = stringResource(I18nR.string.art_optimizer_title),
            summary = stringResource(I18nR.string.art_optimizer_summary),
            onClick = { backStack.add(ArtOptimizerRoute) },
        )
        Preference(
            icon = BaseR.drawable.lock_24px,
            title = stringResource(I18nR.string.stealth_cloak_title),
            summary = stringResource(I18nR.string.stealth_cloak_summary),
            onClick = { backStack.add(StealthCloakRoute) },
        )
        Preference(
            icon = BaseR.drawable.dashboard_2_24px,
            title = stringResource(I18nR.string.preference_screen_context_profiles),
            summary = stringResource(I18nR.string.preference_screen_context_profiles_summary),
            onClick = { backStack.add(ContextProfilesSettingsRoute) },
        )
        Preference(
            icon = BaseR.drawable.extension_24px,
            title = stringResource(I18nR.string.preference_screen_plugins),
            summary = stringResource(I18nR.string.preference_screen_plugins_summary),
            onClick = { backStack.add(PluginsSettingsRoute) },
        )
        Preference(
            icon = BaseR.drawable.lock_24px,
            title = stringResource(I18nR.string.preference_screen_app_lock),
            summary = stringResource(I18nR.string.preference_screen_app_lock_summary),
            onClick = { backStack.add(AppLockSettingsRoute) },
        )
        Preference(
            icon = BaseR.drawable.enterprise_24px,
            title = stringResource(I18nR.string.preference_screen_work_profile),
            summary = stringResource(I18nR.string.preference_screen_work_profile_summary),
            onClick = { backStack.add(WorkProfileSettingsRoute) },
        )
        Preference(
            icon = BaseR.drawable.settings_backup_restore_24px,
            title = stringResource(I18nR.string.preference_screen_backup),
            summary = stringResource(I18nR.string.preference_screen_backup_summary),
            onClick = { backStack.add(BackupSettingsRoute) },
        )
        Preference(
            icon = BaseR.drawable.bug_report_24px,
            title = stringResource(I18nR.string.preference_screen_debug),
            summary = stringResource(I18nR.string.preference_screen_debug_summary),
            onClick = { backStack.add(DebugSettingsRoute) },
        )
    }
}
