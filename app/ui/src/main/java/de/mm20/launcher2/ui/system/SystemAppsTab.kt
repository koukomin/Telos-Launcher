package de.mm20.launcher2.ui.system

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.animation.AnimatedVisibility
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
        // Search and Toolbar - Google Apps 2026 Style
        var searchExpanded by remember { mutableStateOf(false) }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            tonalElevation = 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (searchExpanded) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).height(56.dp),
                        placeholder = { Text(stringResource(R.string.hc_search)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        trailingIcon = {
                            IconButton(onClick = { query = ""; searchExpanded = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Search")
                            }
                        }
                    )
                } else {
                    Text(
                        text = stringResource(R.string.hc_apps),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    IconButton(onClick = { searchExpanded = true }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.hc_search))
                    }
                    IconButton(onClick = { frozenOnly = !frozenOnly }) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(if (frozenOnly) R.drawable.ac_unit_24px else R.drawable.apps_24px),
                            contentDescription = "Frozen Only",
                            tint = if (frozenOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showSystem = !showSystem }) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(if (showSystem) R.drawable.visibility_24px else R.drawable.visibility_off_24px),
                            contentDescription = "System Apps"
                        )
                    }
                    // Global AppOps Next Button (Only in top bar)
                    AppOpsNextButton()
                }
            }
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
                            Spacer(Modifier.height(8.dp))
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(stringResource(R.string.freeze_hub_uad_help), style = MaterialTheme.typography.labelSmall)
                                    Text(info.recommendation, style = MaterialTheme.typography.labelMedium)
                                    Text(info.description, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = {
                                    backStack.add(de.mm20.launcher2.ui.network.firewall.NetworkPackageFirewallRoute(app.pkg))
                                }
                            ) {
                                Icon(androidx.compose.ui.res.painterResource(de.mm20.launcher2.base.R.drawable.lock_24px), contentDescription = "Firewall")
                            }
                            IconButton(
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        try {
                                            val ok = withContext(Dispatchers.IO) { if (app.frozen) freeze.unfreeze(app.pkg) else freeze.freeze(app.pkg) }
                                            outcome = context.getString(R.string.freeze_hub_result, if (app.pkg in ok) 1 else 0, 1)
                                            revision++
                                        } finally { busy = false }
                                    }
                                },
                                enabled = !busy
                            ) {
                                Icon(
                                    androidx.compose.ui.res.painterResource(if (app.frozen) R.drawable.play_arrow_24px else R.drawable.ac_unit_24px),
                                    contentDescription = "Freeze/Unfreeze",
                                    tint = if (app.frozen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
