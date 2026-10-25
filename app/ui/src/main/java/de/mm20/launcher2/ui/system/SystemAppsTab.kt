package de.mm20.launcher2.ui.system

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.store.EmptyMessage
import de.mm20.launcher2.ui.store.AppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import de.mm20.launcher2.ui.system.UadRepository
import de.mm20.launcher2.ui.system.UadInfo
import org.koin.compose.koinInject

@Composable
fun SystemAppsTab(
    onOpenDetails: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val freezeManager: de.mm20.launcher2.freeze.FreezeManager = koinInject()

    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(true) }
    var showVirtual by rememberSaveable { mutableStateOf(true) }
    var frozenOnly by rememberSaveable { mutableStateOf(false) }

        var installedApps by remember { mutableStateOf<List<android.content.pm.ApplicationInfo>>(emptyList()) }
    var uadList by remember { mutableStateOf<Map<String, UadInfo>>(emptyMap()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

        LaunchedEffect(showSystem, showVirtual, refreshTrigger) {
        withContext(Dispatchers.IO) {
            uadList = UadRepository.getList(context)
            val pm = context.packageManager
            val flags = android.content.pm.PackageManager.GET_META_DATA or android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
            val apps = pm.getInstalledApplications(flags)
            val filtered = apps.filter { app ->
                val isSystem = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val isVirtual = app.packageName.startsWith(context.packageName)
                if (app.packageName == context.packageName) return@filter false
                (showSystem || !isSystem) && (showVirtual || !isVirtual)
            }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

            installedApps = filtered
        }
    }

    val visibleApps = remember(installedApps, query, frozenOnly, refreshTrigger) {
        installedApps.filter { app ->
            val label = context.packageManager.getApplicationLabel(app).toString()
            val matchesQuery = query.isBlank() || label.contains(query.trim(), ignoreCase = true) || app.packageName.contains(query.trim(), ignoreCase = true)
            val isFrozen = freezeManager.isFrozen(app.packageName)
            val matchesFrozen = !frozenOnly || isFrozen
            matchesQuery && matchesFrozen
        }
    }

    Column(Modifier.fillMaxSize()) {
        de.mm20.launcher2.ui.component.TelosSearchBar(query, { query = it }, stringResource(R.string.hf_store_search))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = frozenOnly,
                onClick = { frozenOnly = !frozenOnly },
                label = { Text("Frozen Only (❄)") },
            )
            FilterChip(
                selected = showSystem,
                onClick = { showSystem = !showSystem },
                label = { Text(stringResource(R.string.store_show_system_apps)) },
            )
            FilterChip(
                selected = showVirtual,
                onClick = { showVirtual = !showVirtual },
                label = { Text(stringResource(R.string.store_show_virtual_apps)) },
            )
        }

        if (visibleApps.isEmpty()) {
            EmptyMessage(
                stringResource(R.string.hf_store_no_apps_match),
                stringResource(R.string.hf_store_change_filter),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                items(visibleApps, key = { it.packageName }) { app ->
                    val isFrozen = freezeManager.isFrozen(app.packageName)
                    val pm = context.packageManager
                    val label = pm.getApplicationLabel(app).toString()

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable {
                            onOpenDetails(app.packageName)
                        },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.packageName, label, 48, source = null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                val sizeBytes = runCatching { java.io.File(app.sourceDir).length() }.getOrDefault(0L)
                                val sizeText = if (sizeBytes > 0) android.text.format.Formatter.formatFileSize(context, sizeBytes) else null
                                val subtitle = listOfNotNull(app.packageName, sizeText).joinToString(" · ")
                                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (isFrozen) {
                                        Text("❄ Frozen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("Active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    uadList[app.packageName]?.let { uad ->
                                        Text(" · ")
                                        val uadColor = when(uad.removal) {
                                            "Safe", "Recommended" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                            "Unsafe" -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                        Text("${uad.removal} to freeze", style = MaterialTheme.typography.labelSmall, color = uadColor, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            if (isFrozen) freezeManager.unfreeze(app.packageName)
                                            else freezeManager.freeze(app.packageName)
                                            refreshTrigger++
                                        }
                                    }
                                },
                            ) {
                                Text(if (isFrozen) "Thaw" else "Freeze")
                            }
                        }
                    }
                }
            }
        }
    }
}
