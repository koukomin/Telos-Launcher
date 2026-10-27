package de.mm20.launcher2.ui.system

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.store.AppIcon
import de.mm20.launcher2.ui.store.EmptyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.freeze.BloatDescriptions
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private data class InstalledSystemRow(val pkg: String, val label: String, val size: String, val frozen: Boolean)

@Composable
fun SystemAppsTab(onOpenDetails: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backStack = de.mm20.launcher2.ui.locals.LocalBackStack.current
    val freeze: FreezeManager = koinInject()
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(true) }
    var frozenOnly by rememberSaveable { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<InstalledSystemRow>>(emptyList()) }
    val descriptions: BloatDescriptions = koinInject()
    val settings: FreezeSettings = koinInject()
    val uad by descriptions.data.collectAsStateWithLifecycle()
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<String?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    LaunchedEffect(Unit) {
        descriptions.entries()
        if (settings.bloatAutoUpdate.first()) descriptions.refresh()
    }
    LaunchedEffect(showSystem, revision) {
        loading = true
        apps = withContext(Dispatchers.IO) {
            freeze.refreshBackendState()
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
                .filter { it.packageName != context.packageName && (showSystem || it.flags and ApplicationInfo.FLAG_SYSTEM == 0) }
                .map { app ->
                    InstalledSystemRow(app.packageName, runCatching { app.loadLabel(pm).toString() }.getOrDefault(app.packageName),
                        android.text.format.Formatter.formatFileSize(context, java.io.File(app.sourceDir).length()),
                        freeze.isFrozen(app.packageName))
                }.sortedBy { it.label.lowercase(java.util.Locale.ROOT) }
        }
        loading = false
    }
    val visible = remember(apps, query, frozenOnly) {
        val term = query.trim()
        apps.filter { (!frozenOnly || it.frozen) && (term.isBlank() || it.label.contains(term, true) || it.pkg.contains(term, true)) }
    }
    Column(Modifier.fillMaxSize()) {
        de.mm20.launcher2.ui.component.TelosSearchBar(query, { query = it }, stringResource(R.string.hf_store_search))
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(frozenOnly, { frozenOnly = !frozenOnly }, { Text(stringResource(R.string.hf_freeze_frozen_apps)) })
            FilterChip(showSystem, { showSystem = !showSystem }, { Text(stringResource(R.string.preference_freeze_show_system_apps)) })
        }
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { backStack.add(de.mm20.launcher2.ui.settings.freeze.FreezeSettingsRoute) }) {
                Text(stringResource(R.string.preference_screen_freeze))
            }
            AppOpsNextButton()
        }
        outcome?.let { Text(it, Modifier.padding(horizontal = 16.dp)) }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!loading && visible.isEmpty()) EmptyMessage(stringResource(R.string.hf_store_no_apps_match), stringResource(R.string.hf_store_change_filter))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(visible, key = { it.pkg }) { app ->
                Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { onOpenDetails(app.pkg) }, verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(app.pkg, app.label, 48, source = null)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.titleMedium)
                                Text("${app.pkg} · ${app.size}", style = MaterialTheme.typography.bodySmall)
                                if (app.frozen) Text(stringResource(R.string.hf_freeze_frozen), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        uad[app.pkg]?.let { info ->
                            Text(stringResource(R.string.freeze_hub_uad_help), style = MaterialTheme.typography.labelSmall)
                            Text(info.recommendation, style = MaterialTheme.typography.labelMedium)
                            Text(info.description, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    val ok = withContext(Dispatchers.IO) { if (app.frozen) freeze.unfreeze(app.pkg) else freeze.freeze(app.pkg) }
                                    outcome = context.getString(R.string.freeze_hub_result, if (app.pkg in ok) 1 else 0, 1)
                                    revision++
                                } finally { busy = false }
                            }
                        }) { Text(stringResource(if (app.frozen) R.string.freeze_hub_unfreeze else R.string.freeze_widget_freeze_now)) }
                        OutlinedButton(onClick = {
                            backStack.add(de.mm20.launcher2.ui.network.firewall.NetworkPackageFirewallRoute(app.pkg))
                        }) { Text(stringResource(R.string.netfw_title)) }
                    }
                }
            }
        }
    }
}
