package de.mm20.launcher2.ui.network
import de.mm20.launcher2.i18n.R as I18nR

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.network.NetState
import de.mm20.launcher2.network.NetworkEngine
import de.mm20.launcher2.network.api.AppDirectory
import de.mm20.launcher2.network.api.NetworkSettings
import de.mm20.launcher2.network.tor.TorController
import de.mm20.launcher2.network.tor.TorStatus
import de.mm20.launcher2.network.web3.Web3Names
import de.mm20.launcher2.network.web3.Web3Resolution
import de.mm20.launcher2.network.web3.Web3Resolver
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.*
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable data object NetworkPrivacyRoute : NavKey
@Serializable data object NetworkTorAppsRoute : NavKey

@Composable
fun NetworkPrivacyScreen() {
    val settings: NetworkSettings = koinInject()
    val engine: NetworkEngine = koinInject()
    val tor: TorController = koinInject()
    val config by settings.values.collectAsState()
    val state by engine.state.collectAsState()
    val status by tor.status.collectAsState()
    val progress by tor.progress.collectAsState()
    val torError by tor.error.collectAsState()
    val backStack = LocalBackStack.current
    PreferenceScreen(title = { Text(stringResource(I18nR.string.nt_title), Modifier.padding(horizontal = 16.dp)) }) {
        item {
            PreferenceCategory(title = "Tor") {
                SwitchPreference(
                    title = stringResource(I18nR.string.nt_onion),
                    value = config.torEnabled, enabled = state !is NetState.Starting,
                    onValueChanged = {
                        settings.update { s -> s.copy(torEnabled = it, torSystemDefault = if (it) s.torSystemDefault else false, torWasActive = if (it) s.torWasActive else false) }; settings.flush()
                        if (state is NetState.On) engine.restart()
                    },
                )
                Preference(title = stringResource(I18nR.string.net_hub_status), summary = stringResource(when (status) {
                    TorStatus.Off -> I18nR.string.nwg_status_off
                    TorStatus.Waiting -> I18nR.string.nt_waiting
                    TorStatus.Starting -> I18nR.string.nwg_status_connecting
                    TorStatus.Ready -> I18nR.string.nwg_status_up
                    TorStatus.Unavailable -> I18nR.string.nwg_status_down
                }))
                if (status == TorStatus.Starting) Text("$progress%", Modifier.padding(16.dp))
                torError?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                SwitchPreference(
                    title = stringResource(I18nR.string.nt_auto), summary = stringResource(I18nR.string.nt_auto_hint),
                    value = config.torAutoStart, enabled = config.torEnabled,
                    onValueChanged = { settings.update { s -> s.copy(torAutoStart = it) } },
                )
                Preference(title = stringResource(I18nR.string.nt_apps), summary = config.torAppIds.size.toString(),
                    enabled = config.torEnabled, onClick = { backStack.add(NetworkTorAppsRoute) })
            }
        }
        item { NetworkRouteChooser() }
        item { Text(stringResource(I18nR.string.nt_scope), Modifier.padding(16.dp)) }
        item { Text(stringResource(I18nR.string.nt_safety), Modifier.padding(16.dp)) }
        item { Web3Lookup() }
    }
}

@Composable
fun NetworkTorAppsScreen() {
    val settings: NetworkSettings = koinInject()
    val engine: NetworkEngine = koinInject()
    val directory: AppDirectory = koinInject()
    val config by settings.values.collectAsState()
    val apps by directory.apps.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(apps, query) {
        apps.filter { it.hasInternet && it.appId != directory.ownAppId }
            .filter { de.mm20.launcher2.comms.search.TelosSearch.matches(query, it.label, it.packageName) }
            .sortedBy { it.label.lowercase() }
    }
    PreferenceScreen(title = { Text(stringResource(I18nR.string.nt_apps), Modifier.padding(horizontal = 16.dp)) }) {
        item { de.mm20.launcher2.ui.component.TelosSearchBar(query, { query = it }, stringResource(I18nR.string.nwg_search)) }
        items(shown, key = { it.appId }) { app ->
            SwitchPreference(title = app.label, summary = app.packages.joinToString("\n"),
                value = app.appId in config.torAppIds, enabled = config.torEnabled,
                onValueChanged = { checked ->
                    settings.update { it.copy(torAppIds = if (checked) it.torAppIds + app.appId else it.torAppIds - app.appId) }
                    settings.flush()
                    engine.closeAllConnections()
                })
        }
    }
}

@Composable
private fun Web3Lookup() {
    val context = LocalContext.current
    val settings: NetworkSettings = koinInject()
    val config by settings.values.collectAsState()
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var proxy by rememberSaveable { mutableStateOf(config.web3UnstoppableProxy) }
    var result by remember { mutableStateOf<Web3Resolution?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Web3", style = MaterialTheme.typography.titleLarge)
        Text(stringResource(I18nR.string.nweb_intro))
        SelectionContainer { Text(".eth · " + Web3Names.unstoppableSuffixes.sorted().joinToString(" · ") { ".$it" }) }
        OutlinedTextField(value = proxy, onValueChange = {
            proxy = it
            generation++
            settings.update { s -> s.copy(web3UnstoppableProxy = it) }
            job?.cancel(); loading = false; result = null; failed = false
        }, label = { Text(stringResource(I18nR.string.nweb_proxy)) }, modifier = Modifier.fillMaxWidth(), singleLine = false)
        OutlinedTextField(value = name, onValueChange = {
            name = it; generation++; job?.cancel(); loading = false; result = null; failed = false
        }, label = { Text(stringResource(I18nR.string.net_dns_detail_address)) },
            placeholder = { Text("alice.eth") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(enabled = !loading && Web3Names.normalize(name) != null && (name.trim().trimEnd('.').endsWith(".eth", true) || Web3Names.httpsUrl(proxy) != null),
            onClick = {
                job?.cancel(); result = null; failed = false; loading = true
                val request = ++generation
                job = scope.launch {
                    try { result = Web3Resolver().resolve(name, proxy) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { if (generation == request) loading = false }
                }
            }) { Text(stringResource(I18nR.string.ts_search)) }
        if (loading) CircularProgressIndicator()
        if (failed) Text(stringResource(I18nR.string.nwg_status_error), color = MaterialTheme.colorScheme.error)
        result?.let { resolution ->
            Text(resolution.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(I18nR.string.nweb_wallets))
            if (resolution.wallets.isEmpty() && !resolution.partialFailure) Text(stringResource(I18nR.string.od_no_results))
            resolution.wallets.forEach { (chain, address) ->
                SelectionContainer { Text("$chain\n$address") }
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(chain, address))
                }) { Text(stringResource(I18nR.string.au8_traktweb_copy_redirect)) }
            }
            resolution.website?.let { url ->
                SelectionContainer { Text(url) }
                Button(onClick = {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    catch (_: Exception) { failed = true }
                }) { Text(stringResource(I18nR.string.au22_stremio_opens_browser)) }
            }
            if (resolution.website == null && !resolution.partialFailure) Text(stringResource(I18nR.string.od_no_results))
            Text(stringResource(I18nR.string.net_dns_detail_provider), style = MaterialTheme.typography.labelLarge)
            SelectionContainer { Text(resolution.providers.joinToString("\n")) }
            if (resolution.partialFailure) Text(stringResource(I18nR.string.nwg_status_error), color = MaterialTheme.colorScheme.error)
        }
    }
}
