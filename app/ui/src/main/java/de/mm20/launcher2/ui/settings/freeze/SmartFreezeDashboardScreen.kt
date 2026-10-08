// === TELOS_PENDING_REVIEW_START: smart_freeze_ui_and_actions ===
package de.mm20.launcher2.ui.settings.freeze

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
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import de.mm20.launcher2.icons.IconService

@Serializable
data object SmartFreezeDashboardRoute : NavKey

private enum class FreezeFilter(val label: String) {
    All("All"),
    User("User"),
    System("System"),
    Frozen("Frozen"),
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
    
    var searchQuery by remember { mutableStateOf("") }
    var currentFilter by remember { mutableStateOf(FreezeFilter.All) }
    
    val selectedApps = remember { mutableStateMapOf<String, Boolean>() }
    
    val filteredApps = remember(apps, searchQuery, currentFilter, freezeManager) {
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
                        scope.launch {
                            selectedApps.keys.forEach { pkg ->
                                if (freezeManager.isFrozen(pkg)) freezeManager.unfreeze(pkg) else freezeManager.freeze(pkg)
                            }
                            selectedApps.clear()
                        }
                    },
                    icon = { Icon(painterResource(R.drawable.ac_unit_24px), contentDescription = null) },
                    text = { Text("Toggle Freeze (${selectedApps.size})") }
                )
            }
        }
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            PreferenceScreen(title = "Smart Freeze Dashboard") {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Backend Status", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(
                                text = if (activeBackend != null) "Connected via ${activeBackend?.name}" else "Disconnected",
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (activeBackend == null) {
                                Button(onClick = {
                                    scope.launch { freezeManager.requestPermission() }
                                }) {
                                    Text("Grant Permission")
                                }
                            }
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Frozen Apps", style = MaterialTheme.typography.labelSmall)
                            Text("${apps.count { freezeManager.isFrozen(it.componentName.packageName) }}", style = MaterialTheme.typography.titleLarge)
                        }
                        Column {
                            Text("Auto-Freeze List", style = MaterialTheme.typography.labelSmall)
                            Text("${candidates.size}", style = MaterialTheme.typography.titleLarge)
                        }
                        Column {
                            Text("Last Execution", style = MaterialTheme.typography.labelSmall)
                            Text("Monitoring", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search Apps") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                        leadingIcon = { Icon(painterResource(R.drawable.search_24px), contentDescription = null) }
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
                                label = { Text(filter.label) }
                            )
                        }
                    }
                }
                
                items(filteredApps.size, key = { filteredApps[it].key }) { index ->
                    val app = filteredApps[index]
                    val pkg = app.componentName.packageName
                    val isFrozen = freezeManager.isFrozen(pkg)
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
                                Text(if (isFrozen) "Frozen" else "Active", color = if (isFrozen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Auto-Freeze", style = MaterialTheme.typography.labelSmall)
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
