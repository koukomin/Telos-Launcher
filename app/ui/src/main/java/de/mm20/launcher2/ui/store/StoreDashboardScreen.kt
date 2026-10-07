package de.mm20.launcher2.ui.store

import android.content.Intent
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.store.catalog.TelosApp
import de.mm20.launcher2.store.catalog.TelosApps
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.StoreItem
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.media.MediaFrame
import de.mm20.launcher2.ui.media.MediaSearchBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** [addUrl] is an address or obtainium:// link to add right away (from a link that opened the Store). */
@Serializable
data class StoreDashboardRoute(val addUrl: String = "") : NavKey

@Composable
fun StoreDashboardScreen(initialLink: String = "") {
    val viewModel: StoreViewModel = viewModel()
    val backStack = LocalBackStack.current
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val rows by viewModel.visibleRows.collectAsStateWithLifecycle()
    val allRows by viewModel.rows.collectAsStateWithLifecycle()
    val installStates by viewModel.installStates.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val disabledTelos by viewModel.disabledTelosApps.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var addPrefill by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showInstalled by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(initialLink) {
        viewModel.handleLink(initialLink) { url ->
            addPrefill = url
            showAdd = true
        }
    }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri)
    }

    MediaFrame("Store", askNotifications = true, actions = {
        if (tab == 0) {
            IconButton(onClick = { viewModel.checkAll() }, enabled = !checking) {
                if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(painterResource(R.drawable.download_24px), contentDescription = "Check for updates")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(R.drawable.more_vert_24px), contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Add installed apps") }, onClick = { menuOpen = false; showInstalled = true })
                    DropdownMenuItem(text = { Text("Import (Obtainium file)") }, onClick = { menuOpen = false; importPicker.launch(arrayOf("*/*")) })
                    DropdownMenuItem(text = { Text("Export (Obtainium file)") }, onClick = { menuOpen = false; exportPicker.launch("telos-store-export.json") })
                    DropdownMenuItem(text = { Text("Store settings") }, onClick = { menuOpen = false; showSettings = true })
                }
            }
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Apps") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Telos apps") })
                }
                if (tab == 0) {
                    AppsTab(
                        rows = rows,
                        totalCount = allRows.size,
                        updateCount = allRows.count { it.updateAvailable },
                        installStates = installStates,
                        query = query,
                        filter = filter,
                        category = category,
                        categories = categories,
                        onQuery = { viewModel.query.value = it },
                        onFilter = { viewModel.filter.value = it },
                        onCategory = { viewModel.category.value = it },
                        onOpen = { backStack.add(AppDetailsRoute(itemId = it.item.id)) },
                        onAction = { viewModel.onAppActionClicked(it.item) },
                        onUpdateAll = { viewModel.updateAll() },
                    )
                } else {
                    TelosAppsTab(
                        disabled = disabledTelos,
                        onToggle = { app, enabled -> viewModel.setTelosAppEnabled(app.key, enabled) },
                        onOpen = { app ->
                            context.startActivity(
                                Intent().setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                                    .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, app.route)
                                    .apply { app.commsTab?.let { putExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB, it) } }
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                    )
                }
            }
            if (tab == 0) {
                FloatingActionButton(
                    onClick = { addPrefill = ""; showAdd = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                ) {
                    Icon(painterResource(R.drawable.add_24px), contentDescription = "Add app")
                }
            }
        }
    }

    if (showAdd) AddAppDialog(viewModel, addPrefill) { showAdd = false }
    if (showSettings) StoreSettingsDialog(viewModel) { showSettings = false }
    if (showInstalled) InstalledAppsDialog(viewModel) { showInstalled = false }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun AppsTab(
    rows: List<StoreRow>,
    totalCount: Int,
    updateCount: Int,
    installStates: Map<String, StoreInstallUiState>,
    query: String,
    filter: StoreFilter,
    category: String?,
    categories: List<String>,
    onQuery: (String) -> Unit,
    onFilter: (StoreFilter) -> Unit,
    onCategory: (String?) -> Unit,
    onOpen: (StoreRow) -> Unit,
    onAction: (StoreRow) -> Unit,
    onUpdateAll: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        MediaSearchBar(query, onQuery, "Search apps")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(StoreFilter.values().toList()) { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { onFilter(f) },
                    label = {
                        Text(
                            when (f) {
                                StoreFilter.All -> "All"
                                StoreFilter.Updates -> "Updates ($updateCount)"
                                StoreFilter.Installed -> "Installed"
                                StoreFilter.NotInstalled -> "Not installed"
                                StoreFilter.TrackOnly -> "Track only"
                            }
                        )
                    },
                )
            }
            items(categories) { c ->
                FilterChip(selected = category == c, onClick = { onCategory(if (category == c) null else c) }, label = { Text(c) })
            }
        }
        if (updateCount > 0) {
            FilledTonalButton(onClick = onUpdateAll, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Update all ($updateCount)")
            }
        }
        if (rows.isEmpty()) {
            EmptyMessage(
                if (totalCount == 0) "No apps yet" else "No apps match",
                if (totalCount == 0) "Add an app with the + button: paste a GitHub, GitLab, Codeberg, F-Droid or SourceForge address or any page with APK links. You can also import an Obtainium export or add the apps you already have." else "Change the search or the filter.",
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                items(rows, key = { it.item.id }) { row ->
                    StoreItemRow(
                        row = row,
                        installState = installStates[row.item.id] ?: StoreInstallUiState.Idle,
                        onClick = { onOpen(row) },
                        onActionClick = { onAction(row) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMessage(title: String, text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StoreItemRow(
    row: StoreRow,
    installState: StoreInstallUiState,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
) {
    val item = row.item
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(item.packageName, item.displayName, 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.versionLine(row.updateAvailable), style = MaterialTheme.typography.bodySmall, color = if (row.updateAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(item.source.summary(), row.options.category.ifBlank { null }, if (row.options.trackOnly) "track only" else null, if (row.options.pinned) "pinned" else null).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            StoreActionButton(row, installState, onActionClick)
        }
    }
}

private fun StoreItem.versionLine(update: Boolean): String {
    val installed = installedVersionName ?: installedVersionCode?.toString()
    val latest = latestRelease?.version?.takeIf { it != "unknown" }
    return when {
        installed == null && latest != null -> "v$latest available"
        installed == null -> "Not installed"
        update && latest != null -> "v$installed → v$latest"
        else -> "v$installed"
    }
}

/** The icon of the installed app, or a letter before it is installed */
@Composable
internal fun AppIcon(packageName: String, name: String, size: Int) {
    val context = LocalContext.current
    val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val d: Drawable = context.packageManager.getApplicationIcon(packageName)
                d.toBitmap(size * 3, size * 3).asImageBitmap()
            }.getOrNull()
        }
    }
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = null, modifier = Modifier.size(size.dp))
    } else {
        Surface(Modifier.size(size.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Text(name.firstOrNull()?.uppercaseChar()?.toString() ?: "?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
internal fun StoreActionButton(row: StoreRow, state: StoreInstallUiState, onClick: () -> Unit) {
    when (state) {
        StoreInstallUiState.Downloading, StoreInstallUiState.Installing -> {
            OutlinedButton(onClick = {}, enabled = false) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(if (state == StoreInstallUiState.Downloading) "Downloading" else "Installing")
            }
        }
        StoreInstallUiState.Failed -> OutlinedButton(onClick = onClick) { Text("Retry") }
        StoreInstallUiState.Idle, StoreInstallUiState.Installed -> {
            val label = row.actionLabel()
            if (label == "Open") OutlinedButton(onClick = onClick) { Text(label) } else Button(onClick = onClick) { Text(label) }
        }
    }
}

/** "Install", "Update", "Open", or "Get" for apps that are only tracked (their page opens) */
internal fun StoreRow.actionLabel(): String = when {
    item.installedVersionCode == null && options.trackOnly -> "Page"
    item.installedVersionCode == null -> "Install"
    updateAvailable && options.trackOnly -> "Page"
    updateAvailable -> "Update"
    else -> "Open"
}

internal fun AppSource.summary(): String = when (this) {
    is AppSource.GitHub -> "$owner/$repo · GitHub"
    is AppSource.GitLab -> "$path · GitLab"
    is AppSource.Gitea -> "$owner/$repo · $host"
    is AppSource.FDroid -> if (repoUrl.contains("izzysoft")) "IzzyOnDroid" else if (repoUrl.contains("f-droid.org")) "F-Droid" else repoUrl.substringAfter("://").substringBefore('/')
    is AppSource.SourceForge -> "$project · SourceForge"
    is AppSource.Html -> pageUrl.substringAfter("://").substringBefore('/')
    is AppSource.DirectApk -> "Direct download"
    is AppSource.AffiliatePlayStore -> "Play Store"
    is AppSource.AffiliateDirect -> "Direct download"
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun TelosAppsTab(disabled: Set<String>, onToggle: (TelosApp, Boolean) -> Unit, onOpen: (TelosApp) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "The apps that are part of Telos. Installing one shows its icon in the app grid and in search, removing it hides the icon again. Nothing is downloaded: the apps stay inside Telos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(TelosApps.all, key = { it.key }) { app ->
            val installed = app.key !in disabled
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(48.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(painterResource(app.iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(if (installed) "Installed" else "Not installed", style = MaterialTheme.typography.labelMedium, color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(app.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                    Text(
                        app.features.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (installed) {
                            OutlinedButton(onClick = { onOpen(app) }) { Text("Open") }
                            if (app.removable) TextButton(onClick = { onToggle(app, false) }) { Text("Remove") }
                        } else {
                            Button(onClick = { onToggle(app, true) }) { Text("Install") }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun AddAppDialog(viewModel: StoreViewModel, prefill: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var url by remember {
        mutableStateOf(prefill.ifBlank { clipboard.getText()?.text?.trim()?.takeIf { it.startsWith("http", true) }.orEmpty() })
    }
    var pkg by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Add an app") },
        text = {
            Column {
                Text(
                    "GitHub, GitLab, Codeberg / Forgejo / Gitea, F-Droid, IzzyOnDroid, SourceForge, a direct APK link, or any page that links to APK files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(url, { url = it; error = null }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), isError = error != null)
                OutlinedTextField(
                    pkg, { pkg = it }, label = { Text("Package name (optional)") }, singleLine = true,
                    supportingText = { Text("Fill this in if the app is already installed, so it shows as installed") },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp), strokeWidth = 2.dp)
            }
        },
        confirmButton = {
            TextButton(enabled = url.isNotBlank() && !busy, onClick = {
                busy = true
                viewModel.addFromUrl(url, pkg) { result ->
                    busy = false
                    if (result == null) onDismiss() else error = result
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StoreSettingsDialog(viewModel: StoreViewModel, onDismiss: () -> Unit) {
    val g by viewModel.global.collectAsStateWithLifecycle()
    var token by remember { mutableStateOf(g.githubToken) }
    AlertDialog(
        onDismissRequest = { viewModel.setGlobal { it.copy(githubToken = token.trim()) }; onDismiss() },
        title = { Text("Store settings") },
        text = {
            Column {
                Text("Check for updates", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    items(listOf(0 to "Never", 1 to "1 h", 3 to "3 h", 6 to "6 h", 12 to "12 h", 24 to "Daily")) { (hours, label) ->
                        FilterChip(selected = g.checkIntervalHours == hours, onClick = { viewModel.setGlobal { it.copy(checkIntervalHours = hours) } }, label = { Text(label) })
                    }
                }
                ToggleRow("Only on Wi-Fi", g.wifiOnly) { v -> viewModel.setGlobal { it.copy(wifiOnly = v) } }
                ToggleRow("Notify about updates", g.notifyUpdates) { v -> viewModel.setGlobal { it.copy(notifyUpdates = v) } }
                ToggleRow("Install updates automatically", g.autoInstall) { v -> viewModel.setGlobal { it.copy(autoInstall = v) } }
                Text(
                    "Automatic installs only happen when Shizuku or root allows installing without asking. Otherwise you are notified and install with a tap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    token, { token = it }, label = { Text("GitHub token (optional)") }, singleLine = true,
                    supportingText = { Text("Raises GitHub's limit from 60 to 5000 requests per hour") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.setGlobal { it.copy(githubToken = token.trim()) }; onDismiss() }) { Text("Done") } },
    )
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun InstalledAppsDialog(viewModel: StoreViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var candidates by remember { mutableStateOf<List<InstalledCandidate>?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { candidates = viewModel.installedCandidates() }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Add installed apps") },
        text = {
            Column {
                Text(
                    "Choose apps you already have. Telos looks them up on F-Droid and IzzyOnDroid and keeps the ones it finds up to date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val list = candidates
                if (list == null) {
                    CircularProgressIndicator(Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.height(320.dp)) {
                        items(list, key = { it.packageName }) { app ->
                            Row(
                                Modifier.fillMaxWidth().clickable { if (app.packageName in selected) selected.remove(app.packageName) else selected.add(app.packageName) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = app.packageName in selected, onCheckedChange = { if (it) selected.add(app.packageName) else selected.remove(app.packageName) })
                                Column {
                                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp), strokeWidth = 2.dp)
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty() && !busy, onClick = {
                busy = true
                val chosen = candidates.orEmpty().filter { it.packageName in selected }
                viewModel.importInstalled(chosen) { found ->
                    Toast.makeText(context, "$found of ${chosen.size} found and added", Toast.LENGTH_LONG).show()
                    onDismiss()
                }
            }) { Text("Add (${selected.size})") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}
