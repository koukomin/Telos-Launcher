package de.mm20.launcher2.ui.network.nextdns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.network.api.DnsController
import de.mm20.launcher2.network.api.DnsKind
import de.mm20.launcher2.network.api.DnsServer
import de.mm20.launcher2.network.nextdns.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class NextDnsEditorState(
    val profileId: String? = null, val connected: Boolean = false, val loading: Boolean = true,
    val logs: List<NextDnsLog> = emptyList(), val cursor: String? = null,
    val allowed: List<NextDnsRule> = emptyList(), val denied: List<NextDnsRule> = emptyList(),
    val error: NextDnsErrorKind? = null, val dnsApplied: Boolean = false,
)

class NextDnsVM : ViewModel(), KoinComponent {
    private val repository: NextDnsRepository by inject()
    private val dns: DnsController by inject()
    private val mutable = MutableStateFlow(NextDnsEditorState())
    val state = mutable.asStateFlow()
    private var running = false
    private var search = ""
    private var status: String? = null

    init { operation {
        val info = repository.credentialsInfo()
        mutable.update { it.copy(profileId = info.profileId, connected = info.hasKey) }
        if (info.hasKey) refreshContent()
    } }

    private fun operation(block: suspend () -> Unit) {
        if (running) return
        running = true
        mutable.update { it.copy(loading = true, error = null, dnsApplied = false) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: NextDnsException) { mutable.update { it.copy(error = e.kind) } }
            catch (_: Exception) { mutable.update { it.copy(error = NextDnsErrorKind.API) } }
            finally { running = false; mutable.update { it.copy(loading = false) } }
        }
    }
    private suspend fun refreshContent() {
        val page = repository.loadLogs(search, status)
        val allowed = repository.loadRules(true)
        val denied = repository.loadRules(false)
        mutable.update { it.copy(logs = page.items, cursor = page.cursor, allowed = allowed, denied = denied) }
    }
    fun connect(profile: String, key: String) = operation {
        val selected = repository.connect(profile.trim(), key.trim())
        mutable.update { NextDnsEditorState(profileId = selected.id, connected = true, loading = true) }
        refreshContent()
    }
    fun disconnect() = operation {
        repository.disconnect()
        mutable.value = NextDnsEditorState(loading = true)
    }
    fun refresh(query: String, filter: String?) = operation {
        search = query.trim().take(250); status = filter
        refreshContent()
    }
    fun more() = operation {
        val cursor = mutable.value.cursor ?: return@operation
        if (mutable.value.logs.size >= 1000) return@operation
        val page = repository.loadLogs(search, status, cursor)
        mutable.update { it.copy(logs = (it.logs + page.items).take(1000), cursor = if (it.logs.size + page.items.size >= 1000) null else page.cursor) }
    }
    fun put(allow: Boolean, domain: String) = operation { repository.putRule(allow, domain); refreshContent() }
    fun toggle(allow: Boolean, domain: String, active: Boolean) = operation { repository.setRuleActive(allow, domain, active); refreshContent() }
    fun delete(allow: Boolean, domain: String) = operation { repository.deleteRule(allow, domain); refreshContent() }
    fun applyDns() = operation {
        val profile = mutable.value.profileId ?: return@operation
        val url = "https://dns.nextdns.io/$profile"
        val server = dns.servers.value.firstOrNull { it.kind == DnsKind.Doh && it.url == url }
            ?: dns.addCustom(DnsServer("", DnsKind.Doh, "NextDNS · $profile", url = url, provider = "NextDNS")).getOrThrow()
        dns.select(server.id).getOrThrow()
        mutable.update { it.copy(dnsApplied = true) }
    }
}
