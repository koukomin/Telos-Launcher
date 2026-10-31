package de.mm20.launcher2.ui.settings.agents
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.agents.AgentCatalog
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable
import androidx.compose.ui.res.stringResource

@Serializable
data object AgentsSettingsRoute : NavKey

@Composable
fun AgentsSettingsScreen() {
    val viewModel: AgentsSettingsScreenVM = viewModel()
    val enabled by viewModel.enabled.collectAsStateWithLifecycle(false)
    val agentId by viewModel.agentId.collectAsStateWithLifecycle("grok")
    val transport by viewModel.transport.collectAsStateWithLifecycle("auto")
    val sendSentences by viewModel.sendSentences.collectAsStateWithLifecycle(true)
    val voice by viewModel.searchBarVoice.collectAsStateWithLifecycle(true)

    PreferenceScreen(title = stringResource(I18nR.string.preference_screen_agents)) {
        item {
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_agents_enabled),
                    summary = stringResource(I18nR.string.preference_agents_enabled_summary),
                    value = enabled,
                    onValueChanged = viewModel::setEnabled,
                )
            }
        }
        item {
            AnimatedVisibility(enabled) {
                PreferenceCategory {
                    ListPreference(
                        title = stringResource(I18nR.string.preference_agents_provider),
                        items = AgentCatalog.all.map {
                            ListPreferenceItem(stringResource(it.labelRes), it.id)
                        },
                        value = agentId,
                        onValueChanged = { viewModel.setAgentId(it) },
                    )
                    ListPreference(
                        title = stringResource(I18nR.string.preference_agents_transport),
                        summary = stringResource(I18nR.string.preference_agents_transport_summary),
                        items = listOf(
                            ListPreferenceItem(stringResource(I18nR.string.agent_transport_auto), "auto"),
                            ListPreferenceItem(stringResource(I18nR.string.agent_transport_app), "app"),
                            ListPreferenceItem(stringResource(I18nR.string.agent_transport_web), "web"),
                            ListPreferenceItem(stringResource(I18nR.string.agent_transport_termux), "termux"),
                        ),
                        value = transport,
                        onValueChanged = { viewModel.setTransport(it) },
                    )
                    SwitchPreference(
                        title = stringResource(I18nR.string.preference_agents_send_sentences),
                        summary = stringResource(I18nR.string.preference_agents_send_sentences_summary),
                        value = sendSentences,
                        onValueChanged = viewModel::setSendSentences,
                    )
                }
            }
        }
        item {
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_search_bar_voice),
                    summary = stringResource(I18nR.string.preference_search_bar_voice_summary),
                    value = voice,
                    onValueChanged = viewModel::setSearchBarVoice,
                )
            }
        }
    }
}
