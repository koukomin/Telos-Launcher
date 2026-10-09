// === TELOS_PENDING_REVIEW_START: smart_freeze_ui_and_actions ===
package de.mm20.launcher2.ui.settings.freeze

import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.search.GreekFold
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.TelosSearchBar
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import de.mm20.launcher2.icons.IconService

@Serializable
data object SmartFreezeDashboardRoute : NavKey

private enum class FreezeFilter(@androidx.annotation.StringRes val labelRes: Int) {
    All(R.string.filter_all),
    User(R.string.au_freeze_filter_user),
    System(R.string.au_freeze_filter_system),
    Frozen(R.string.hf_freeze_frozen),
}

@Composable
fun SmartFreezeDashboardScreen() {
    val freezeManager: FreezeManager = koinInject()
    val appRepository: AppRepository = koinInject()
    val iconService: IconService = koinInject()
    val scope = rememberCoroutineScope()

    val apps by appRepository.findMany().collectAsStateWithLifecycle(emptyList())
    val candidates by freezeManager.autoFreezeCandidates.collectAsStateWithLifecycle(emptySet())
    val activeBackend by freezeManager.activeBackend.collectAsStateWithLifecycle()
    
    val stats by freezeManager.stats.collectAsStateWithLifecycle(emptyMap())
    val lastFrozenAt = stats.values.maxOfOrNull { it.lastFrozenAt ?: 0L } ?: 0L

    // Bumped after every freeze/unfreeze so the live OS state shown below is re-read.
    var stateVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            freezeManager.refreshBackendState()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SmartFreezeDashboard", "backend refresh failed", e)
        }
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var currentFilter by remember { mutableStateOf(FreezeFilter.All) }
    
    val selectedApps = remember { mutableStateMapOf<String, Boolean>() }
    
    val filteredApps = remember(apps, searchQuery, currentFilter, freezeManager, stateVersion) {
        apps.filter { app ->
            val pkg = app.componentName.packageName
            val matchesQuery = GreekFold.contains(app.label, searchQuery)
            val matchesFilter = when (currentFilter) {
                FreezeFilter.All -> true
                FreezeFilter.User -> app.canUninstall
                FreezeFilter.System -> !app.canUninstall
                FreezeFilter.Frozen -> freezeManager.isFrozen(pkg)
            }
            matchesQuery && matchesFilter
        }.sortedBy { it.label }
    }

    Scaffold(
        floatingActionButton = {
            AnimatedVisibility(
                visible = selectedApps.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it * 2 }),
                exit = slideOutVertically(targetOffsetY = { it * 2 })
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val toggle = selectedApps.keys.toList()
                        scope.launch {
                            try {
                                toggle.forEach { pkg ->
                                    if (freezeManager.isFrozen(pkg)) freezeManager.unfreeze(pkg) else freezeManager.freeze(pkg)
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.w("SmartFreezeDashboard", "toggle failed", e)
                            }
                            selectedApps.clear()
                            stateVersion++
                        }
                    },
                    icon = { Icon(painterResource(R.drawable.ac_unit_24px), contentDescription = null) },
                    text = { Text(stringResource(R.string.hf_freeze_toggle, selectedApps.size)) }
                )
            }
        }
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            PreferenceScreen(title = stringResource(R.string.hc_smart_freeze_dashboard)) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.hf_freeze_backend_status), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(
                                text = if (activeBackend != null) stringResource(R.string.hf_freeze_connected_via, activeBackend?.name.orEmpty()) else stringResource(R.string.hf_freeze_disconnected),
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (activeBackend == null) {
                                Button(onClick = {
                                    scope.launch {
                                        try {
                                            freezeManager.refreshBackendState()
                                            freezeManager.requestPermission()
                                            freezeManager.refreshBackendState()
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            android.util.Log.w("SmartFreezeDashboard", "permission request failed", e)
                                        }
                                    }
                                }) {
                                    Text(stringResource(R.string.hf_freeze_grant))
                                }
                            }
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(stringResource(R.string.hf_freeze_frozen_apps), style = MaterialTheme.typography.labelSmall)
                            Text("${remember(apps, stateVersion) { apps.count { freezeManager.isFrozen(it.componentName.packageName) } }}", style = MaterialTheme.typography.titleLarge)
                        }
                        Column {
                            Text(stringResource(R.string.hf_freeze_auto_list), style = MaterialTheme.typography.labelSmall)
                            Text("${candidates.size}", style = MaterialTheme.typography.titleLarge)
                        }
                        Column {
                            Text(stringResource(R.string.hf_freeze_last_execution), style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (lastFrozenAt > 0L) android.text.format.DateUtils.getRelativeTimeSpanString(lastFrozenAt).toString()
                                else stringResource(R.string.hf_freeze_monitoring),
                                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item {
                    TelosSearchBar(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = stringResource(R.string.hf_freeze_search_apps),
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(FreezeFilter.entries.toList()) { filter ->
                            FilterChip(
                                selected = currentFilter == filter,
                                onClick = { currentFilter = filter },
                                label = { Text(stringResource(filter.labelRes)) }
                            )
                        }
                    }
                }
                
                items(filteredApps.size, key = { filteredApps[it].key }) { index ->
                    val app = filteredApps[index]
                    val pkg = app.componentName.packageName
                    val isFrozen = remember(pkg, stateVersion, apps) { freezeManager.isFrozen(pkg) }
                    val isCandidate = candidates.contains(pkg)
                    val isSelected = selectedApps.containsKey(pkg)
                    val icon by iconService.getIcon(app, 48.dp.value.toInt()).collectAsStateWithLifecycle(null)
                    
                    LauncherCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable {
                                if (isSelected) selectedApps.remove(pkg) else selectedApps[pkg] = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null // handled by row click
                            )
                            
                            ShapedLauncherIcon(
                                size = 48.dp,
                                icon = { icon },
                                grayscale = isFrozen
                            )
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                Text(if (isFrozen) stringResource(R.string.hf_freeze_frozen) else stringResource(R.string.hf_freeze_active), color = if (isFrozen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            
                            Column(horizontalAlignment = Alignment.End) {
                                Text(stringResource(R.string.hf_freeze_auto), style = MaterialTheme.typography.labelSmall)
                                Switch(
                                    checked = isCandidate,
                                    onCheckedChange = { freezeManager.setAutoFreezeCandidate(pkg, it) },
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
// === TELOS_PENDING_REVIEW_END: smart_freeze_ui_and_actions ===
