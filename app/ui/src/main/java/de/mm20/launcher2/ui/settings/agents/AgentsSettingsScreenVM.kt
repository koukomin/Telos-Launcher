package de.mm20.launcher2.ui.settings.agents

import androidx.lifecycle.ViewModel
import de.mm20.launcher2.preferences.ui.AgentSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AgentsSettingsScreenVM : ViewModel(), KoinComponent {
    private val settings: AgentSettings by inject()

    val enabled = settings.enabled
    fun setEnabled(enabled: Boolean) = settings.setEnabled(enabled)

    val agentId = settings.agentId
    fun setAgentId(id: String) = settings.setAgentId(id)

    val transport = settings.transport
    fun setTransport(transport: String) = settings.setTransport(transport)

    val sendSentences = settings.sendSentences
    fun setSendSentences(send: Boolean) = settings.setSendSentences(send)

    val searchBarVoice = settings.searchBarVoice
    fun setSearchBarVoice(enabled: Boolean) = settings.setSearchBarVoice(enabled)
}
