package de.mm20.launcher2.ui.network.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.network.NetState
import de.mm20.launcher2.network.NetworkEngine
import de.mm20.launcher2.network.api.DnsController
import de.mm20.launcher2.network.api.DnsServer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NetworkDnsVM : ViewModel(), KoinComponent {
    private val controller: DnsController by inject()
    private val engine: NetworkEngine by inject()

    val servers: StateFlow<List<DnsServer>> = controller.servers
    val selected: StateFlow<DnsServer> = controller.selected
    val engineState: StateFlow<NetState> = engine.state

    fun select(id: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            controller.select(id).onFailure { onError(it.message ?: it.toString()) }
        }
    }

    fun validate(server: DnsServer): Result<Unit> = controller.validate(server)

    suspend fun test(server: DnsServer): Result<Long> = controller.test(server)

    fun add(server: DnsServer, done: (Result<DnsServer>) -> Unit) {
        viewModelScope.launch { done(controller.addCustom(server)) }
    }

    fun update(server: DnsServer, done: (Result<Unit>) -> Unit) {
        viewModelScope.launch { done(controller.updateCustom(server)) }
    }

    fun remove(id: String) {
        viewModelScope.launch { controller.remove(id) }
    }
}
