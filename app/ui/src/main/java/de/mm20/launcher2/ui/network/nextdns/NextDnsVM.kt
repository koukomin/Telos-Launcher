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
    val rewrites: List<NextDnsRewrite> = emptyList(),
    val profileSettings: Map<NextDnsSettingsPage, NextDnsSettings> = emptyMap(),
    val analyticsMetric: NextDnsAnalyticsMetric = NextDnsAnalyticsMetric.TRAFFIC,
    val analyticsPeriod: NextDnsAnalyticsPeriod = NextDnsAnalyticsPeriod.HOURS_24,
    val analyticsDeviceId: String? = null,
    val analyticsDevices: List<NextDnsAnalyticsDevice> = emptyList(),
    val analytics: NextDnsAnalyticsSnapshot? = null,
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
    fun put(allow: Boolean, domain: String) = operation {
        val normalized = NextDnsValidation.domain(domain)
        val existing = (if (allow) mutable.value.allowed else mutable.value.denied).firstOrNull { it.id == normalized }
        if (existing == null) repository.putRule(allow, normalized)
        else if (!existing.active) repository.setRuleActive(allow, normalized, true)
        refreshContent()
    }
    fun toggle(allow: Boolean, domain: String, active: Boolean) = operation { repository.setRuleActive(allow, domain, active); refreshContent() }
    fun delete(allow: Boolean, domain: String) = operation { repository.deleteRule(allow, domain); refreshContent() }
    fun loadRewrites() = operation {
        val rewrites = repository.loadRewrites()
        mutable.update { it.copy(rewrites = rewrites) }
    }
    fun addRewrite(name: String, content: String) = operation {
        repository.createRewrite(name, content)
        val rewrites = repository.loadRewrites()
        mutable.update { it.copy(rewrites = rewrites) }
    }
    fun deleteRewrite(rewriteId: String) = operation {
        repository.deleteRewrite(rewriteId)
        val rewrites = repository.loadRewrites()
        mutable.update { it.copy(rewrites = rewrites) }
    }
    fun loadSettings(page: NextDnsSettingsPage) = operation {
        val settings = repository.loadSettings(page)
        mutable.update { it.copy(profileSettings = it.profileSettings + (page to settings)) }
    }
    fun loadAnalytics(
        metric: NextDnsAnalyticsMetric = mutable.value.analyticsMetric,
        period: NextDnsAnalyticsPeriod = mutable.value.analyticsPeriod,
        deviceId: String? = mutable.value.analyticsDeviceId,
    ) = operation {
        mutable.update {
            it.copy(
                analyticsMetric = metric,
                analyticsPeriod = period,
                analyticsDeviceId = deviceId,
                analytics = null,
            )
        }
        val devices = if (mutable.value.analyticsDevices.isEmpty()) {
            repository.loadAnalyticsDevices()
        } else {
            mutable.value.analyticsDevices
        }
        val snapshot = repository.loadAnalytics(metric, period, deviceId)
        mutable.update { it.copy(analyticsDevices = devices, analytics = snapshot) }
    }
    fun moreAnalytics() = operation {
        val current = mutable.value.analytics ?: return@operation
        val cursor = current.nextCursor ?: return@operation
        if (current.rows.size >= 500) return@operation
        val page = repository.loadAnalytics(
            current.metric,
            mutable.value.analyticsPeriod,
            mutable.value.analyticsDeviceId,
            cursor,
        )
        mutable.update {
            it.copy(
                analytics = current.copy(
                    rows = (current.rows + page.rows).take(500),
                    nextCursor = page.nextCursor,
                ),
            )
        }
    }
    fun toggleSetting(page: NextDnsSettingsPage, setting: NextDnsBooleanSetting, value: Boolean) = operation {
        repository.updateSettings(page, NextDnsSettingsValues.patch(setting.path, value))
        val settings = repository.loadSettings(page)
        mutable.update { it.copy(profileSettings = it.profileSettings + (page to settings)) }
    }
    fun applyDns() = operation {
        val profile = mutable.value.profileId ?: return@operation
        val url = "https://dns.nextdns.io/$profile"
        val server = dns.servers.value.firstOrNull { it.kind == DnsKind.Doh && it.url == url }
            ?: dns.addCustom(DnsServer("", DnsKind.Doh, "NextDNS · $profile", url = url, provider = "NextDNS")).getOrThrow()
        dns.select(server.id).getOrThrow()
        mutable.update { it.copy(dnsApplied = true) }
    }
}
