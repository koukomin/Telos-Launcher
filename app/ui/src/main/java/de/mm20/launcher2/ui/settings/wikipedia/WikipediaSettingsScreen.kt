package de.mm20.launcher2.ui.settings.wikipedia
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.component.preferences.TextPreference
import kotlinx.serialization.Serializable

@Serializable
data object WikipediaSettingsRoute: NavKey

@Composable
fun WikipediaSettingsScreen() {
    val viewModel: WikipediaSettingsScreenVM = viewModel()
    PreferenceScreen(title = stringResource(I18nR.string.preference_search_wikipedia)) {
        item {
            PreferenceCategory {
                val wikipedia by viewModel.wikipedia.collectAsState()
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_search_wikipedia),
                    summary = stringResource(I18nR.string.preference_search_wikipedia_summary),
                    value = wikipedia == true,
                    onValueChanged = {
                        viewModel.setWikipedia(it)
                    }
                )
                val customUrl by viewModel.customUrl.collectAsState()
                TextPreference(
                    title = stringResource(I18nR.string.preference_wikipedia_customurl),
                    value = customUrl ?: "",
                    placeholder = stringResource(id = I18nR.string.wikipedia_url),
                    summary = customUrl.takeIf { !it.isNullOrBlank() }
                        ?: stringResource(id = I18nR.string.wikipedia_url),
                    onValueChanged = {
                        viewModel.setCustomUrl(it)
                    })
            }
        }
    }
}