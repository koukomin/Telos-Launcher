package de.mm20.launcher2.ui.network.nextdns

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.network.nextdns.NextDnsErrorKind
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable

@Serializable
data object NextDnsRoute : NavKey

@Composable
fun NextDnsScreen() {
    val vm: NextDnsVM = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var profile by rememberSaveable { mutableStateOf("") }
    // Account-wide API keys never enter saved instance state or the ViewModel.
    var key by remember { mutableStateOf("") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var blockedOnly by rememberSaveable { mutableStateOf(false) }
    var editDomain by remember { mutableStateOf<String?>(null) }
    var editAllow by remember { mutableStateOf(true) }
    var remove by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var forget by remember { mutableStateOf(false) }
    LaunchedEffect(state.profileId) { state.profileId?.let { profile = it } }
    val rules = if (tab == 1) state.allowed else state.denied
    val visibleRules = remember(rules, query) { rules.filter { query.isBlank() || it.id.contains(query.trim(), true) } }
    PreferenceScreen(title = { Text(stringResource(R.string.nd_title)) }, topBarActions = {
        IconButton(enabled = state.connected && !state.loading, onClick = { vm.refresh(query, if (blockedOnly) "blocked" else null) }) {
            Icon(painterResource(R.drawable.refresh_24px), stringResource(R.string.hc_refresh))
        }
    }) {
        item {
            ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.nd_intro), style = MaterialTheme.typography.bodyMedium)
                    if (state.connected) {
                        Text("${stringResource(R.string.nd_profile)}: ${state.profileId.orEmpty()}", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !state.loading, onClick = { settings = !settings }) { Text(stringResource(R.string.nd_key)) }
                            TextButton(enabled = !state.loading, onClick = { forget = true }) { Text(stringResource(R.string.nd_disconnect)) }
                            OutlinedButton(enabled = !state.loading, onClick = vm::applyDns) { Text(stringResource(R.string.nd_apply)) }
                        }
                        Text(stringResource(R.string.net_dns_vpn_only), style = MaterialTheme.typography.bodySmall)
                    }
                    if (!state.connected || settings) {
                        Text(stringResource(R.string.nd_help), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(profile, { profile = it.trim().take(6) }, enabled = !state.loading,
                            label = { Text(stringResource(R.string.nd_profile)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(key, { key = it }, enabled = !state.loading,
                            label = { Text(stringResource(R.string.nd_key)) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !state.loading && profile.length == 6 && key.isNotBlank(), onClick = { vm.connect(profile, key); key = "" }) {
                                Text(stringResource(R.string.nd_connect))
                            }
                            if (!state.connected) TextButton(enabled = !state.loading, onClick = { forget = true }) { Text(stringResource(R.string.nd_disconnect)) }
                        }
                    }
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.dnsApplied) Text(stringResource(R.string.nd_applied), color = MaterialTheme.colorScheme.primary)
                    state.error?.let { Text(stringResource(errorString(it)), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (state.connected) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(R.string.nd_logs, R.string.nd_allowlist, R.string.nd_denylist).forEachIndexed { index, label ->
                            FilterChip(tab == index, onClick = { tab = index }, label = { Text(stringResource(label)) })
                        }
                    }
                    OutlinedTextField(query, { query = it.take(250) }, label = { Text(stringResource(R.string.hc_search)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (tab == 0) FilterChip(blockedOnly, onClick = { blockedOnly = !blockedOnly; vm.refresh(query, if (blockedOnly) "blocked" else null) }, label = { Text(stringResource(R.string.nd_denylist)) })
                        TextButton(enabled = !state.loading, onClick = { vm.refresh(query, if (blockedOnly) "blocked" else null) }) { Text(stringResource(R.string.hc_search)) }
                        if (tab != 0) TextButton(enabled = !state.loading, onClick = { editAllow = tab == 1; editDomain = "" }) { Text(stringResource(R.string.hc_add)) }
                    }
                }
            }
            if (tab == 0) {
                if (state.logs.isEmpty() && !state.loading) item { Text(stringResource(R.string.nd_empty), Modifier.padding(20.dp)) }
                itemsIndexed(state.logs, key = { index, log -> "${log.timestamp}:${log.domain}:$index" }) { _, log ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(log.domain, style = MaterialTheme.typography.titleMedium)
                            val label = when (log.status) { "blocked" -> R.string.nd_deny; "allowed" -> R.string.nd_allow; else -> R.string.nd_logs }
                            Text(stringResource(label), color = if (log.status == "blocked") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(listOfNotNull(log.timestamp, log.deviceName, log.protocol).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            if (log.reasons.isNotEmpty()) Text(log.reasons.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = !state.loading, onClick = { editAllow = true; editDomain = log.domain }) { Text(stringResource(R.string.nd_allow)) }
                                TextButton(enabled = !state.loading, onClick = { editAllow = false; editDomain = log.domain }) { Text(stringResource(R.string.nd_deny)) }
                            }
                        }
                    }
                }
                if (state.cursor != null) item { TextButton(enabled = !state.loading, onClick = vm::more, modifier = Modifier.padding(16.dp)) { Text(stringResource(R.string.nd_more)) } }
            } else {
                if (visibleRules.isEmpty() && !state.loading) item { Text(stringResource(R.string.nd_empty), Modifier.padding(20.dp)) }
                items(visibleRules, key = { "${tab}:${it.id}" }) { rule ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(rule.id, style = MaterialTheme.typography.titleMedium)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Switch(rule.active, onCheckedChange = { vm.toggle(tab == 1, rule.id, it) }, enabled = !state.loading)
                                TextButton(enabled = !state.loading, onClick = { remove = (tab == 1) to rule.id }) { Text(stringResource(R.string.hc_remove)) }
                            }
                        }
                    }
                }
            }
        }
    }
    editDomain?.let { initial ->
        var domain by remember(initial) { mutableStateOf(initial) }
        AlertDialog(onDismissRequest = { editDomain = null }, title = { Text(stringResource(R.string.nd_rule_title)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${stringResource(R.string.nd_profile)}: ${state.profileId.orEmpty()}")
                Text(stringResource(R.string.nd_intro))
                OutlinedTextField(domain, { domain = it }, label = { Text(stringResource(R.string.nd_domain)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(editAllow, onClick = { editAllow = true }, label = { Text(stringResource(R.string.nd_allow)) })
                    FilterChip(!editAllow, onClick = { editAllow = false }, label = { Text(stringResource(R.string.nd_deny)) })
                }
            }
        }, confirmButton = { TextButton(enabled = !state.loading && domain.isNotBlank(), onClick = { vm.put(editAllow, domain); editDomain = null }) { Text(stringResource(R.string.hc_save)) } },
            dismissButton = { TextButton(onClick = { editDomain = null }) { Text(stringResource(R.string.hc_cancel)) } })
    }
    remove?.let { target -> AlertDialog(onDismissRequest = { remove = null }, title = { Text(stringResource(R.string.hc_remove)) }, text = {
        Text("${state.profileId.orEmpty()} · ${target.second}\n${stringResource(if (target.first) R.string.nd_allowlist else R.string.nd_denylist)}\n${stringResource(R.string.nd_intro)}")
    }, confirmButton = { TextButton(enabled = !state.loading, onClick = { vm.delete(target.first, target.second); remove = null }) { Text(stringResource(R.string.hc_remove)) } }, dismissButton = { TextButton(onClick = { remove = null }) { Text(stringResource(R.string.hc_cancel)) } }) }
    if (forget) AlertDialog(onDismissRequest = { forget = false }, title = { Text(stringResource(R.string.nd_disconnect)) }, text = { Text(state.profileId.orEmpty()) },
        confirmButton = { TextButton(enabled = !state.loading, onClick = { vm.disconnect(); key = ""; settings = false; forget = false }) { Text(stringResource(R.string.hc_remove)) } }, dismissButton = { TextButton(onClick = { forget = false }) { Text(stringResource(R.string.hc_cancel)) } })
}

private fun errorString(kind: NextDnsErrorKind): Int = when (kind) {
    NextDnsErrorKind.AUTH -> R.string.nd_error_auth
    NextDnsErrorKind.INVALID -> R.string.nd_error_invalid
    NextDnsErrorKind.RATE_LIMIT -> R.string.nd_error_rate
    NextDnsErrorKind.NETWORK -> R.string.nd_error_network
    NextDnsErrorKind.API -> R.string.nd_error_api
    NextDnsErrorKind.UNCONFIGURED -> R.string.nd_error_unconfigured
}
