package com.telos.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.apps.DesktopEntry
import com.telos.desktop.apps.DesktopFileLauncher
import com.telos.desktop.apps.LinuxAppScanner
import com.telos.desktop.apps.TelosAppCategory
import com.telos.desktop.apps.TelosFileHandler
import com.telos.desktop.apps.TelosVirtualApp
import com.telos.desktop.apps.TelosVirtualApps
import com.telos.desktop.search.DesktopSearch
import com.telos.desktop.search.SearchHit
import com.telos.desktop.search.SearchKind
import com.telos.desktop.icons.CandyCyberIcon
import com.telos.desktop.icons.LinuxIconThemeResolver
import com.telos.desktop.i18n.TelosI18n.t
import com.telos.desktop.overlay.SessionPower
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlasmaAppGrid(
    icons: LinuxIconThemeResolver,
    onDismiss: () -> Unit,
    onOpenTelos: (TelosVirtualApp) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Telos") }
    var cat by remember { mutableStateOf<TelosAppCategory?>(null) }
    var apps by remember { mutableStateOf<List<DesktopEntry>>(emptyList()) }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { LinuxAppScanner.scan() } }
    LaunchedEffect(query, apps) {
        if (query.isBlank()) {
            hits = emptyList()
        } else {
            tab = "All"
            hits = withContext(Dispatchers.Default) { DesktopSearch.search(query, apps = apps) }
        }
    }
    val telos = remember(query) {
        TelosVirtualApps.all.filter { query.isBlank() || it.label.contains(query, true) }
    }
    val linux = remember(apps, query, cat) {
        apps.filter {
            (cat == null || it.telosCategory == cat) &&
                (query.isBlank() || it.name.contains(query, true))
        }
    }
    Box(
        Modifier.fillMaxSize().background(TelosColors.background.copy(alpha = 0.82f)).clickable { onDismiss() },
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.78f)
                .clip(RoundedCornerShape(24.dp))
                .background(TelosColors.surface.copy(alpha = 0.96f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                .padding(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Telos", color = TelosColors.secondary, fontSize = 22.sp)
                Text(t("action_quit"), color = TelosColors.primary, modifier = Modifier.clickable {
                    SessionPower.open.value = true
                    onDismiss()
                })
            }
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(t("search_bar_placeholder")) }, singleLine = true)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == "All", onClick = { tab = "All" }, label = { Text(t("search_bar_placeholder")) })
                FilterChip(selected = tab == "Telos", onClick = { tab = "Telos" }, label = { Text(t("hc_apps")) })
                FilterChip(selected = tab == "Linux", onClick = { tab = "Linux" }, label = { Text("Linux") })
                if (tab == "Linux") {
                    TelosAppCategory.entries.take(6).forEach { c ->
                        FilterChip(selected = cat == c, onClick = { cat = if (cat == c) null else c }, label = { Text(c.name) })
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tab == "All") {
                    items(hits, key = { it.id }) { hit ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(TelosColors.card).clickable {
                                when (hit.kind) {
                                    SearchKind.TelosApp -> hit.telos?.let { onOpenTelos(it) }
                                    SearchKind.Desktop -> hit.desktop?.let { DesktopFileLauncher.launch(it) }
                                    SearchKind.File -> hit.file?.let { TelosFileHandler.open(it) }
                                }
                                onDismiss()
                            }.padding(12.dp),
                        ) {
                            CandyCyberIcon(hit.telos?.icon ?: com.telos.desktop.icons.CandyIcon.AppGrid, 48.dp)
                            Text(hit.title, color = TelosColors.onSurface, fontSize = 12.sp, maxLines = 2)
                        }
                    }
                } else if (tab == "Telos") {
                    items(telos, key = { it.domain }) { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(TelosColors.card).clickable {
                                onOpenTelos(app)
                                onDismiss()
                            }.padding(12.dp),
                        ) {
                            CandyCyberIcon(app.icon, 48.dp)
                            Text(app.label, color = TelosColors.onSurface, fontSize = 12.sp, maxLines = 2)
                        }
                    }
                } else {
                    items(linux, key = { it.file.absolutePath }) { entry ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(TelosColors.card).clickable {
                                DesktopFileLauncher.launch(entry)
                                onDismiss()
                            }.padding(12.dp),
                        ) {
                            DiskIcon(icons.resolve(entry.icon), entry.name, 48.dp)
                            Text(entry.name, color = TelosColors.onSurface, fontSize = 12.sp, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}
