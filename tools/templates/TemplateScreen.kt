package de.mm20.launcher2.ui.settings.template

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPref

/**
 * Standard Cyber-Tech Preference Screen Template for AI Agents.
 */
@Composable
fun TemplateSettingsScreen() {
    val viewModel = viewModel<TemplateSettingsScreenVM>()
    val enabledState by viewModel.enabledState.collectAsStateWithLifecycle()

    PreferenceScreen(
        title = stringResource(I18nR.string.action_settings)
    ) {
        item {
            PreferenceCategory(
                title = stringResource(I18nR.string.preference_search_apps)
            ) {
                SwitchPref(
                    title = stringResource(I18nR.string.preference_search_apps),
                    summary = stringResource(I18nR.string.preference_search_apps_summary),
                    value = enabledState == true,
                    onValueChanged = viewModel::setEnabledState
                )
            }
        }
    }
}
