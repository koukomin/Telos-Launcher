package de.mm20.launcher2.ui.store

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.store.catalog.RecommendedApp
import de.mm20.launcher2.store.catalog.RecommendedApps
import de.mm20.launcher2.store.catalog.RecommendedCategory
import de.mm20.launcher2.store.catalog.TelosApp
import de.mm20.launcher2.store.catalog.TelosApps
import de.mm20.launcher2.store.model.projectPageUrl
import de.mm20.launcher2.store.parser.StoreUrlParser
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.media.MediaFrame
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** [addUrl] is an address or obtainium:// link to add right away (from a link that opened the Store). */
@Serializable
data class StoreDashboardRoute(val addUrl: String = "", val initialQuery: String = "") : NavKey

@Composable
fun StoreDashboardScreen(initialLink: String = "", initialQuery: String = "") {
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
    val installationError by viewModel.installationError.collectAsStateWithLifecycle()
    val disabledTelos by viewModel.disabledTelosApps.collectAsStateWithLifecycle()
    val trackedUrls by viewModel.trackedUrls.collectAsStateWithLifecycle()
    val playStoreInstalled = viewModel.playStoreInstalled
    val playApps by viewModel.visiblePlayApps.collectAsStateWithLifecycle()
    val allPlayApps by viewModel.playApps.collectAsStateWithLifecycle()
    val playChecking by viewModel.playChecking.collectAsStateWithLifecycle()
    val playQuery by viewModel.playQuery.collectAsStateWithLifecycle()
    val playFilterUpdates by viewModel.playFilterUpdates.collectAsStateWithLifecycle()
    val global by viewModel.global.collectAsStateWithLifecycle()
    val playTabIndex = if (playStoreInstalled) 3 else -1

    var tab by de.mm20.launcher2.ui.component.rememberPersistentUiState("store.tab", 0)
    var showAdd by remember { mutableStateOf(false) }
    var addPrefill by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showInstalled by remember { mutableStateOf(false) }
    var showTerminal by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // an address that opened the Store is handled once, not again after a rotation
    var linkHandled by rememberSaveable(initialLink) { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.checkAll() }
    LaunchedEffect(playStoreInstalled) {
        if (playStoreInstalled) {
            viewModel.playFilterUpdates.value = true
            viewModel.refreshPlayApps()
            viewModel.checkPlayUpdates()
        }
    }
    LaunchedEffect(initialLink, initialQuery) {
        if (linkHandled) return@LaunchedEffect
        linkHandled = true
        if (initialQuery.isNotBlank()) {
            viewModel.query.value = initialQuery
        } else {
            viewModel.handleLink(initialLink) { url ->
                addPrefill = url
                showAdd = true
            }
        }
    }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    installationError?.let { error ->
        AlertDialog(onDismissRequest = viewModel::consumeInstallationError,
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au_store_msg_install_failed))  } },
            text = { androidx.compose.foundation.text.selection.SelectionContainer {
                Text(error, modifier = Modifier.verticalScroll(rememberScrollState()))
            } },
            confirmButton = { TextButton(onClick = viewModel::consumeInstallationError) {
                Text(stringResource(I18nR.string.hc_close))
            } })
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri)
    }

    MediaFrame(stringResource(I18nR.string.hf_store_title), askNotifications = true, actions = {
        if (tab == 0 || tab == playTabIndex) {
            IconButton(
                onClick = {
                    if (tab == playTabIndex) viewModel.checkPlayUpdates() else viewModel.checkAll()
                },
                enabled = if (tab == playTabIndex) !playChecking else !checking,
            ) {
                if ((tab == playTabIndex && playChecking) || (tab == 0 && checking)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(painterResource(BaseR.drawable.download_24px), contentDescription = stringResource(I18nR.string.hc_check_for_updates))
                }
            }
            IconButton(onClick = { showTerminal = true }) {
                Icon(painterResource(BaseR.drawable.terminal_24px), contentDescription = "Terminal Logs", tint = Color(0xFF00F2FE))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(BaseR.drawable.more_vert_24px), contentDescription = stringResource(I18nR.string.hc_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (tab == 0) {
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_add_installed_apps)) }, onClick = { menuOpen = false; showInstalled = true })
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_import_obtainium_file)) }, onClick = { menuOpen = false; importPicker.launch(arrayOf("*/*")) })
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_export_obtainium_file)) }, onClick = { menuOpen = false; exportPicker.launch("telos-store-export.json") })
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_store_settings)) }, onClick = { menuOpen = false; showSettings = true })
                    } else {
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.store_play_open_my_apps)) }, onClick = {
                            menuOpen = false
                            PlayStoreInventory.openPlayUpdates(context)
                        })
                    }
                }
            }
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (checking || playChecking) {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp)
                    )
                    Text(
                        text = stringResource(I18nR.string.store_checking_updates),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
                                val freezeManager: de.mm20.launcher2.freeze.FreezeManager = koinInject()
                val preferredStorePkg = PlayStoreInventory.getPreferredStorePackage(context)
                val preferredStoreTitle = PlayStoreInventory.getPreferredStoreTitle(context)
                val showStoreTab = preferredStorePkg != null
                val playTabIndex = if (showStoreTab) 3 else -1

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TelosTechPill(stringResource(I18nR.string.hc_apps), tab == 0, { tab = 0 }, modifier = Modifier.weight(1f), accentColor = TelosCyber.NeonCyan, compact = true)
                    TelosTechPill(stringResource(I18nR.string.hc_telos_apps), tab == 1, { tab = 1 }, modifier = Modifier.weight(1f), accentColor = TelosCyber.NeonGreen, compact = true)
                    TelosTechPill(stringResource(I18nR.string.recommendation_tab_label), tab == 2, { tab = 2 }, modifier = Modifier.weight(1f), accentColor = TelosCyber.NeonOrange, compact = true)
                    if (showStoreTab) {
                        TelosTechPill(
                            preferredStoreTitle,
                            false,
                            {
                                scope.launch {
                                    PlayStoreInventory.openPreferredStoreUpdates(context, freezeManager)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            accentColor = TelosCyber.NeonMagenta,
                            compact = true,
                        )
                    }
                }
                if (tab == 0 && categories.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            de.mm20.launcher2.ui.component.Tooltip(stringResource(I18nR.string.hf_store_filter_all)) {
                                TelosTechPill(
                                    text = "ALL",
                                    isSelected = category == null,
                                    onClick = { viewModel.category.value = null },
                                    accentColor = TelosCyber.NeonCyan,
                                )
                            }
                        }
                        items(categories) { cat ->
                            val initial = remember(cat) { cat.trim().take(2).uppercase().ifBlank { "#" } }
                            de.mm20.launcher2.ui.component.Tooltip(cat) {
                                TelosTechPill(
                                    text = initial,
                                    isSelected = category == cat,
                                    onClick = { viewModel.category.value = if (category == cat) null else cat },
                                    accentColor = TelosCyber.accentFor(cat.hashCode()),
                                )
                            }
                        }
                    }
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
                        checking = checking,
                        onRefresh = { viewModel.checkAll() },
                        lastCheck = global.lastCheckMillis,
                    )
                } else if (tab == 1) {
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
                } else if (tab == 2) {
                    RecommendedTab(
                        tracked = trackedUrls,
                        onAdd = { app, done ->
                            viewModel.addRecommended(app) { error ->
                                done()
                                if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                }
            }
            if (tab == 0) {
                FloatingActionButton(
                    onClick = { addPrefill = ""; showAdd = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                ) {
                    Icon(painterResource(BaseR.drawable.add_24px), contentDescription = stringResource(I18nR.string.hc_add_app))
                }
            } else if (playStoreInstalled && tab == playTabIndex) {
                FloatingActionButton(
                    onClick = { viewModel.updateAllPlayApps() },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                ) {
                    Icon(painterResource(BaseR.drawable.download_24px), contentDescription = stringResource(I18nR.string.store_play_unfreeze_update))
                }
            }
        }
    }

    StoreUpdateConsent(viewModel)
    CyberTerminalSheet(showTerminal, onDismiss = { showTerminal = false })
    if (showAdd) AddAppDialog(viewModel, addPrefill) { showAdd = false }
    if (showSettings) StoreSettingsDialog(viewModel) { showSettings = false }
    if (showInstalled) InstalledAppsDialog(viewModel) { showInstalled = false }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun StoreSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun StoreCommandHud(
    query: String,
    onQuery: (String) -> Unit,
    filter: StoreFilter,
    updateCount: Int,
    onFilter: (StoreFilter) -> Unit,
    onUpdateAll: () -> Unit,
    filters: List<StoreFilter> = StoreFilter.entries.toList(),
    status: String? = null,
) {
    val actions = buildList {
        if (updateCount > 0) {
            add(
                de.mm20.launcher2.ui.component.TelosHudAction(
                    id = "update-all",
                    icon = BaseR.drawable.ic_glyph_sync,
                    label = stringResource(I18nR.string.hc_update_all_count, updateCount),
                    selected = true,
                    badge = "$updateCount",
                    accent = TelosCyber.NeonOrange,
                    onClick = onUpdateAll,
                )
            )
        }
        filters.forEach { f ->
            add(
                de.mm20.launcher2.ui.component.TelosHudAction(
                    id = f.name,
                    icon = when (f) {
                        StoreFilter.All -> BaseR.drawable.apps_24px
                        StoreFilter.Updates -> BaseR.drawable.download_24px
                        StoreFilter.Installed -> BaseR.drawable.check_circle_24px
                        StoreFilter.NotInstalled -> BaseR.drawable.apk_document_24px
                        StoreFilter.TrackOnly -> BaseR.drawable.visibility_24px
                    },
                    label = when (f) {
                        StoreFilter.All -> stringResource(I18nR.string.hf_store_filter_all)
                        StoreFilter.Updates -> stringResource(I18nR.string.hf_store_filter_updates, updateCount)
                        StoreFilter.Installed -> stringResource(I18nR.string.hf_store_installed)
                        StoreFilter.NotInstalled -> stringResource(I18nR.string.hf_store_not_installed)
                        StoreFilter.TrackOnly -> stringResource(I18nR.string.hf_store_track_only)
                    },
                    selected = filter == f,
                    accent = when (f) {
                        StoreFilter.Updates -> TelosCyber.NeonOrange
                        StoreFilter.Installed -> TelosCyber.NeonGreen
                        StoreFilter.NotInstalled -> TelosCyber.NeonMagenta
                        else -> TelosCyber.NeonCyan
                    },
                    onClick = { onFilter(f) },
                )
            )
        }
    }
    de.mm20.launcher2.ui.component.TelosCommandHud(
        actions = actions,
        searchQuery = query,
        onSearchQueryChange = onQuery,
        searchPlaceholder = stringResource(I18nR.string.hf_store_search),
        status = status,
        fitWidth = true,
    )
}

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
    checking: Boolean = false,
    onRefresh: () -> Unit = {},
    lastCheck: Long = 0L,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val freezeManager: de.mm20.launcher2.freeze.FreezeManager = koinInject()

    Column(Modifier.fillMaxSize()) {
        StoreCommandHud(
            query = query,
            onQuery = onQuery,
            filter = filter,
            updateCount = updateCount,
            onFilter = onFilter,
            onUpdateAll = onUpdateAll,
            status = when {
                checking -> stringResource(I18nR.string.store_checking_updates)
                lastCheck > 0L -> stringResource(
                    I18nR.string.store_last_check,
                    android.text.format.DateUtils.getRelativeTimeSpanString(lastCheck),
                )
                else -> null
            },
        )
        if (rows.isEmpty() && query.isNotBlank()) {
            de.mm20.launcher2.ui.component.SearchEmptyState(query.trim())
        } else if (rows.isEmpty()) {
            EmptyMessage(
                if (totalCount == 0) stringResource(I18nR.string.hf_store_no_apps_yet) else stringResource(I18nR.string.hf_store_no_apps_match),
                if (totalCount == 0) stringResource(I18nR.string.hf_store_empty_hint) else stringResource(I18nR.string.hf_store_change_filter),
            )
        } else {
            PullToRefreshBox(isRefreshing = checking, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            val freezeManager: de.mm20.launcher2.freeze.FreezeManager = koinInject()
            val updates = rows.filter { it.updateAvailable }
            val rest = rows.filter { !it.updateAvailable }

            val telosUpdates = updates.filter { row ->
                val installer = runCatching {
                    val pm = context.packageManager
                    if (android.os.Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(row.item.packageName).installingPackageName
                    else @Suppress("DEPRECATION") pm.getInstallerPackageName(row.item.packageName)
                }.getOrNull()
                installer == context.packageName || installer.isNullOrBlank()
            }

            val otherStoreUpdates = updates.filter { row ->
                val installer = runCatching {
                    val pm = context.packageManager
                    if (android.os.Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(row.item.packageName).installingPackageName
                    else @Suppress("DEPRECATION") pm.getInstallerPackageName(row.item.packageName)
                }.getOrNull()
                installer != null && installer != context.packageName && installer != "com.android.vending"
            }

            val playStoreUpdates = updates.filter { row ->
                val installer = runCatching {
                    val pm = context.packageManager
                    if (android.os.Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(row.item.packageName).installingPackageName
                    else @Suppress("DEPRECATION") pm.getInstallerPackageName(row.item.packageName)
                }.getOrNull()
                installer == "com.android.vending"
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                // Top Priority: Telos Store Updates (Support Auto Update)
                if (telosUpdates.isNotEmpty()) {
                    item(key = "hdr-telos-updates") {
                        StoreSectionHeader(stringResource(I18nR.string.store_section_updates, telosUpdates.size) + " (Telos Store)")
                    }
                    items(telosUpdates, key = { it.item.id }) { row ->
                        StoreItemRow(
                            row = row,
                            installState = installStates[row.item.id] ?: StoreInstallUiState.Idle,
                            onClick = { onOpen(row) },
                            onDetailsClick = { onOpen(row) },
                            onActionClick = { onAction(row) },
                        )
                    }
                }

                // Middle: Updates from Other Stores (F-Droid, Aurora, Obtainium, etc.)
                if (otherStoreUpdates.isNotEmpty()) {
                    item(key = "hdr-other-updates") {
                        StoreSectionHeader("Updates from Other Stores (${otherStoreUpdates.size})")
                    }
                    items(otherStoreUpdates, key = { it.item.id }) { row ->
                        val installerPkg: String? = remember(row.item.packageName) {
                            runCatching {
                                val pm = context.packageManager
                                if (android.os.Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(row.item.packageName).installingPackageName
                                else @Suppress("DEPRECATION") pm.getInstallerPackageName(row.item.packageName)
                            }.getOrNull()
                        }
                        StoreItemRow(
                            row = row,
                            installState = installStates[row.item.id] ?: StoreInstallUiState.Idle,
                            onClick = { onOpen(row) },
                            onDetailsClick = { onOpen(row) },
                            onActionClick = {
                                val pkg = installerPkg
                                if (pkg != null && pkg.length > 0) {
                                    scope.launch {
                                        freezeManager.unfreeze(listOf(pkg), fromUser = true)
                                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                                        if (launchIntent != null) {
                                            context.startActivity(launchIntent)
                                        } else {
                                            onAction(row)
                                        }
                                    }
                                } else {
                                    onAction(row)
                                }
                            },
                        )
                    }
                }

                // Bottom: Google Play Store Updates
                if (playStoreUpdates.isNotEmpty()) {
                    item(key = "hdr-play-updates") {
                        StoreSectionHeader("Google Play Store Updates (${playStoreUpdates.size})")
                    }
                    items(playStoreUpdates, key = { it.item.id }) { row ->
                        StoreItemRow(
                            row = row,
                            installState = installStates[row.item.id] ?: StoreInstallUiState.Idle,
                            onClick = { onOpen(row) },
                            onDetailsClick = { onOpen(row) },
                            onActionClick = {
                                scope.launch {
                                    freezeManager.unfreeze(listOf("com.android.vending"), fromUser = true)
                                    PlayStoreInventory.openPlayUpdates(context)
                                }
                            },
                        )
                    }
                }

                if (rest.isNotEmpty()) {
                    if (updates.isNotEmpty()) {
                        item(key = "hdr-rest") {
                            StoreSectionHeader(stringResource(I18nR.string.store_section_uptodate, rest.size))
                        }
                    }
                    items(rest, key = { it.item.id }) { row ->
                        StoreItemRow(
                            row = row,
                            installState = installStates[row.item.id] ?: StoreInstallUiState.Idle,
                            onClick = { onOpen(row) },
                            onDetailsClick = { onOpen(row) },
                            onActionClick = { onAction(row) },
                        )
                    }
                }
            }
            }
        }
    }
}


/** Suggested free apps in four groups; "Add" puts one into the tracked apps (it is then installed and updated like any other) */
@Composable
private fun RecommendedTab(tracked: Set<String>, onAdd: (RecommendedApp, () -> Unit) -> Unit) {
    val context = LocalContext.current
    var adding by remember { mutableStateOf(setOf<String>()) }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
        for (category in RecommendedCategory.values()) {
            item(key = "header_${category.name}") {
                Text(
                    stringResource(
                        when (category) {
                            RecommendedCategory.Stores -> I18nR.string.au21_store_cat_stores
                            RecommendedCategory.Tools -> I18nR.string.hc_tools
                            RecommendedCategory.Browsers -> I18nR.string.recommendation_category_browsers
                            RecommendedCategory.Media -> I18nR.string.dl_m_tab
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(RecommendedApps.forCategory(category), key = { "${category.name}_${it.name}" }) { app ->
                val isTracked = StoreUrlParser.toUrl(app.source).lowercase() in tracked
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(app.source.projectPageUrl()))) }
                    },
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        AppIcon(app.packageName, app.name, 44, source = app.source)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(app.source.summary(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box(Modifier.align(Alignment.End)) {
                        if (isTracked) {
                            OutlinedButton(onClick = {}, enabled = false) { Text(stringResource(I18nR.string.dl_detail_created)) }
                        } else if (app.name in adding) {
                            OutlinedButton(onClick = {}, enabled = false) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) }
                        } else {
                            Button(onClick = {
                                adding = adding + app.name
                                // the button comes back when adding is done (it then shows "Added", or "Add" again after an error)
                                onAdd(app) { adding = adding - app.name }
                            }) { Text(stringResource(I18nR.string.hc_add)) }
                        }
                    }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun TelosAppsTab(disabled: Set<String>, onToggle: (TelosApp, Boolean) -> Unit, onOpen: (TelosApp) -> Unit) {
    var tq by remember { mutableStateOf("") }
    val appContext = LocalContext.current
    val appConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
    val shownApps = remember(tq, appConfiguration) { TelosApps.all.filter { de.mm20.launcher2.comms.search.TelosSearch.matches(tq, it.localizedName(appContext.resources), it.name, it.description, it.features.joinToString(" "), it.keywords.joinToString(" ")) } }
    Column(Modifier.fillMaxSize()) {
    de.mm20.launcher2.ui.component.TelosSearchBar(tq, { tq = it }, stringResource(I18nR.string.hf_store_search))
    if (shownApps.isEmpty()) de.mm20.launcher2.ui.component.SearchEmptyState(tq.trim())
    else LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                stringResource(I18nR.string.hc_the_apps_that_are_part_of_telos_installi),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(shownApps, key = { it.key }) { app ->
            val installed = app.key !in disabled
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(48.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(contentAlignment = Alignment.Center) {
                                androidx.compose.foundation.Image(painterResource(app.iconRes), contentDescription = null, modifier = Modifier.size(40.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.localizedName(appContext.resources), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(if (installed) stringResource(I18nR.string.hf_store_installed) else stringResource(I18nR.string.hf_store_not_installed), style = MaterialTheme.typography.labelMedium, color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val context = androidx.compose.ui.platform.LocalContext.current
                    if (!installed && de.mm20.launcher2.base.VirtualAppGuard.isTripped(context, app.key)) {
                        Text(
                            stringResource(I18nR.string.hc_switched_off_automatically_because_it_cr),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    Text(when (app.key) {
                        "telos_youtube_app://youtube", "telos_youtube_music_app://youtube_music" -> stringResource(I18nR.string.telos_youtube_store_description)
                        "telos_media_app://media" -> stringResource(I18nR.string.telos_media_store_description)
                        else -> app.description
                    }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                    Text(
                        if (app.key.startsWith("telos_youtube")) stringResource(I18nR.string.telos_youtube_store_features)
                        else app.features.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (installed) {
                            if (app.key.substringBefore("://") != "telos_store_app") OutlinedButton(onClick = { onOpen(app) }) { Text(stringResource(I18nR.string.hc_open)) }
                            if (app.removable) TextButton(onClick = { onToggle(app, false) }) { Text(stringResource(I18nR.string.hc_remove)) }
                        } else {
                            Button(onClick = { onToggle(app, true) }) { Text(stringResource(I18nR.string.hc_install)) }
                        }
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
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_add_an_app))  } },
        text = {
            Column {
                Text(
                    stringResource(I18nR.string.hc_github_gitlab_codeberg_forgejo_gitea_f_d),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(url, { url = it; error = null }, label = { Text(stringResource(I18nR.string.hc_address)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), isError = error != null)
                OutlinedTextField(
                    pkg, { pkg = it }, label = { Text(stringResource(I18nR.string.hc_package_name_optional)) }, singleLine = true,
                    supportingText = { Text(stringResource(I18nR.string.hc_fill_this_in_if_the_app_is_already_insta)) },
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
            }) { Text(stringResource(I18nR.string.hc_add)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}

@Composable
private fun StoreSettingsDialog(viewModel: StoreViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uiSettings: de.mm20.launcher2.preferences.ui.UiSettings = org.koin.compose.koinInject()
    val artPostInstall by uiSettings.artOptimizationStorePostInstall.collectAsStateWithLifecycle(false)
    val g by viewModel.global.collectAsStateWithLifecycle()
    var token by remember { mutableStateOf(g.githubToken) }
    val neverLabel = stringResource(I18nR.string.hf_store_never)
    val dailyLabel = stringResource(I18nR.string.hf_store_daily)
    val hoursLabel = { h: Int -> context.getString(I18nR.string.au_store_hours_short, h) }
    AlertDialog(
        onDismissRequest = { viewModel.setGlobal { it.copy(githubToken = token.trim()) }; onDismiss() },
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_store_settings))  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(I18nR.string.hc_check_for_updates), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    items(listOf(0 to neverLabel, 1 to hoursLabel(1), 3 to hoursLabel(3), 6 to hoursLabel(6), 12 to hoursLabel(12), 24 to dailyLabel)) { (hours, label) ->
                        FilterChip(selected = g.checkIntervalHours == hours, onClick = { viewModel.setGlobal { it.copy(checkIntervalHours = hours) } }, label = { Text(label) })
                    }
                }
                Text(stringResource(I18nR.string.store_installer_method), style = MaterialTheme.typography.titleSmall)
                val choices = listOf(
                    de.mm20.launcher2.store.installer.InstallerChoice("session", stringResource(I18nR.string.store_installer_session)),
                    de.mm20.launcher2.store.installer.InstallerChoice("shizuku", "Shizuku"),
                    de.mm20.launcher2.store.installer.InstallerChoice("root", "Root"),
                ) + viewModel.externalInstallers()
                choices.forEach { choice ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = g.installer == choice.id, onClick = { viewModel.setGlobal { it.copy(installer = choice.id) } })
                        Text(choice.label)
                    }
                }
                Text(stringResource(I18nR.string.store_installer_external_summary), style = MaterialTheme.typography.bodySmall)
                ToggleRow(stringResource(I18nR.string.hc_only_on_wi_fi), g.wifiOnly) { v -> viewModel.setGlobal { it.copy(wifiOnly = v) } }
                ToggleRow(stringResource(I18nR.string.hf_store_notify), g.notifyUpdates) { v -> viewModel.setGlobal { it.copy(notifyUpdates = v) } }
                ToggleRow(stringResource(I18nR.string.store_update_frozen), g.updateFrozenApps) { v -> viewModel.setGlobal { it.copy(updateFrozenApps = v) } }
                ToggleRow(stringResource(I18nR.string.store_play_block_network), g.playUpdateBlockNetwork) { v ->
                    viewModel.setGlobal { it.copy(playUpdateBlockNetwork = v) }
                }
                Text(
                    stringResource(I18nR.string.store_play_block_network_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
                ToggleRow(stringResource(I18nR.string.hf_store_auto_install), g.autoInstall) { v -> viewModel.setGlobal { it.copy(autoInstall = v) } }
                ToggleRow("Allow Version Downgrades (-d)", g.allowDowngrade) { v -> viewModel.setGlobal { it.copy(allowDowngrade = v) } }
                Text(
                    "Allow installing updates even if the package version code is lower than installed (default: Disabled)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
                ToggleRow(stringResource(I18nR.string.store_parallel_updates), g.parallelUpdates) { v ->
                    viewModel.setGlobal { it.copy(parallelUpdates = v) }
                }
                Text(
                    stringResource(I18nR.string.store_parallel_updates_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
                ToggleRow(stringResource(I18nR.string.art_optimizer_store_post_install), artPostInstall) { v ->
                    uiSettings.setArtOptimizationStorePostInstall(v)
                }
                Text(
                    stringResource(I18nR.string.art_optimizer_store_post_install_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
                Text(
                    stringResource(I18nR.string.freeze_backend_shizuku) + " / " + stringResource(I18nR.string.freeze_backend_root) +
                        " / " + stringResource(I18nR.string.freeze_backend_device_owner),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    token, { token = it }, label = { Text(stringResource(I18nR.string.hc_github_token_optional)) }, singleLine = true,
                    supportingText = { Text(stringResource(I18nR.string.hc_raises_github_s_limit_from_60_to_5000_re)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.setGlobal { it.copy(githubToken = token.trim()) }; onDismiss() }) { Text(stringResource(I18nR.string.hc_done)) } },
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
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_add_installed_apps))  } },
        text = {
            Column {
                Text(
                    stringResource(I18nR.string.hc_choose_apps_you_already_have_telos_looks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var iq by remember { mutableStateOf("") }
                de.mm20.launcher2.ui.component.TelosSearchBar(iq, { iq = it }, stringResource(I18nR.string.hf_store_search))
                val list = candidates?.let { c -> if (iq.isBlank()) c else c.filter { de.mm20.launcher2.comms.search.TelosSearch.matches(iq, it.label, it.packageName) } }
                if (list == null) {
                    CircularProgressIndicator(Modifier.padding(16.dp))
                } else {
                    if (list.isEmpty() && iq.isNotBlank()) de.mm20.launcher2.ui.component.SearchEmptyState(iq.trim())
                    else if (list.isEmpty()) Text(stringResource(I18nR.string.au_store_no_installed_candidates), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    else LazyColumn(Modifier.heightIn(max = 320.dp)) {
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
                    Toast.makeText(context, context.getString(I18nR.string.au_store_msg_installed_found, found, chosen.size), Toast.LENGTH_LONG).show()
                    onDismiss()
                }
            }) { Text(stringResource(I18nR.string.hc_add_count, selected.size)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}
