// === TELOS_PENDING_REVIEW_START: smart_freeze_ui_and_actions ===
package de.mm20.launcher2.ui.settings.freeze

import androidx.compose.foundation.layout.*
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
import de.mm20.launcher2.freeze.AppFreezeState
import de.mm20.launcher2.preferences.FreezeProfile
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import kotlinx.coroutines.launch

@Serializable
data object SmartFreezeDashboardRoute : NavKey

@Composable
fun SmartFreezeDashboardScreen() {
    val freezeManager: FreezeManager = koinInject()
    val appRepository: AppRepository = koinInject()
    val scope = rememberCoroutineScope()

    val apps by appRepository.findMany().collectAsStateWithLifecycle(emptyList())
    val candidates by freezeManager.autoFreezeCandidates.collectAsStateWithLifecycle(emptySet())
    val activeBackend by freezeManager.activeBackend.collectAsStateWithLifecycle()
    
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredApps = remember(apps, searchQuery) {
        apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    PreferenceScreen(title = { Text("Smart Freeze Dashboard") }) {
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
        }
        
        items(filteredApps, key = { it.key }) { app ->
            val pkg = app.componentName.packageName
            val isFrozen = freezeManager.isFrozen(pkg)
            val isCandidate = candidates.contains(pkg)
            
            ListItem(
                headlineContent = { Text(app.label) },
                supportingContent = { 
                    Text(if (isFrozen) "Frozen" else "Active", color = if (isFrozen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) 
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = isCandidate,
                            onCheckedChange = { freezeManager.setAutoFreezeCandidate(pkg, it) }
                        )
                        IconButton(onClick = {
                            scope.launch {
                                if (isFrozen) freezeManager.unfreeze(pkg) else freezeManager.freeze(pkg)
                            }
                        }) {
                            Icon(
                                painterResource(if (isFrozen) R.drawable.play_arrow_24px else R.drawable.ac_unit_24px),
                                contentDescription = if (isFrozen) "Unfreeze" else "Freeze",
                                tint = if (isFrozen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    }
}
// === TELOS_PENDING_REVIEW_END: smart_freeze_ui_and_actions ===
