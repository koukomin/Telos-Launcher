package com.telos.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.apps.DesktopEntry
import com.telos.desktop.apps.DesktopFileLauncher
import com.telos.desktop.apps.LinuxAppScanner
import com.telos.desktop.apps.TelosAppCategory
import com.telos.desktop.icons.LinuxIconThemeResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppDrawer(
    icons: LinuxIconThemeResolver,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<TelosAppCategory?>(null) }
    var apps by remember { mutableStateOf<List<DesktopEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { LinuxAppScanner.scan() }
    }
    val filtered = remember(apps, query, category) {
        apps.filter { entry ->
            (category == null || entry.telosCategory == category) &&
                (query.isBlank() || entry.name.contains(query, true) || entry.comment.contains(query, true))
        }
    }
    Column(modifier.background(TelosColors.surface.copy(alpha = 0.92f)).padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search applications") },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
            TelosAppCategory.entries.forEach { cat ->
                FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat.name) })
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(filtered, key = { it.file.absolutePath }) { entry ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { DesktopFileLauncher.launch(entry) }
                        .padding(8.dp)
                        .background(TelosColors.card),
                ) {
                    DiskIcon(icons.resolve(entry.icon), entry.name, 56.dp)
                    Text(entry.name, color = TelosColors.onSurface, fontSize = 12.sp, maxLines = 2)
                }
            }
        }
    }
}
