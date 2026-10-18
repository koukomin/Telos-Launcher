package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class AgentSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val enabled
        get() = dataStore.data.map { it.agents.agentsEnabled }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(agents = it.agents.copy(agentsEnabled = enabled)) }
    }

    val agentId
        get() = dataStore.data.map { it.agents.agentId }.distinctUntilChanged()

    fun setAgentId(id: String) {
        dataStore.update { it.copy(agents = it.agents.copy(agentId = id)) }
    }

    val transport
        get() = dataStore.data.map { it.agents.agentTransport }.distinctUntilChanged()

    fun setTransport(transport: String) {
        dataStore.update { it.copy(agents = it.agents.copy(agentTransport = transport)) }
    }

    val sendSentences
        get() = dataStore.data.map { it.agents.agentsSendSentences }.distinctUntilChanged()

    fun setSendSentences(send: Boolean) {
        dataStore.update { it.copy(agents = it.agents.copy(agentsSendSentences = send)) }
    }

    val searchBarVoice
        get() = dataStore.data.map { it.agents.searchBarVoice }.distinctUntilChanged()

    fun setSearchBarVoice(enabled: Boolean) {
        dataStore.update { it.copy(agents = it.agents.copy(searchBarVoice = enabled)) }
    }
}
