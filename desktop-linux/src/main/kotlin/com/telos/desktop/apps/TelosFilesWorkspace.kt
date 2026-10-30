package com.telos.desktop.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.files.CloudPlacesStore
import com.telos.desktop.files.FilesTabState
import com.telos.desktop.files.FilesWorkspaceState
import com.telos.desktop.files.FilesWorkspaceStore
import com.telos.desktop.files.NetworkPlace
import com.telos.desktop.files.NetworkPlaces
import com.telos.desktop.i18n.TelosI18n.t
import com.telos.desktop.ui.TelosColors
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun TelosFilesWorkspace(
    start: File,
    showHiddenFiles: Boolean,
    configDir: File,
) {
    val wsFile = remember(configDir) { File(configDir, "files-workspace.properties") }
    val cloudFile = remember(configDir) { File(configDir, "files-cloud.properties") }
    var state by remember {
        mutableStateOf(FilesWorkspaceStore.load(wsFile, start.absolutePath))
    }
    var clouds by remember { mutableStateOf(CloudPlacesStore.load(cloudFile)) }
    var uriDraft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun persist(next: FilesWorkspaceState) {
        state = next
        scope.launch(Dispatchers.IO) { FilesWorkspaceStore.save(wsFile, next) }
    }
    val leftTab = state.tabs.firstOrNull { it.id == state.left } ?: state.tabs.first()
    val rightTab = state.tabs.firstOrNull { it.id == state.right } ?: leftTab
    Column(Modifier.fillMaxSize().background(TelosColors.surface)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
            state.tabs.forEach { tab ->
                val selected = tab.id == state.left || (state.dual && tab.id == state.right)
                FilterChip(
                    selected = selected,
                    onClick = {
                        persist(
                            if (state.dual && state.activeRight) state.copy(right = tab.id)
                            else state.copy(left = tab.id, activeRight = false),
                        )
                    },
                    label = { Text(File(tab.path).name.ifBlank { tab.path }, fontSize = 12.sp) },
                )
                if (state.tabs.size > 1) {
                    Text(
                        t("widget_action_remove"),
                        color = TelosColors.secondary,
                        fontSize = 10.sp,
                        modifier = Modifier.clickable {
                            val remain = state.tabs.filter { it.id != tab.id }
                            persist(
                                state.copy(
                                    tabs = remain,
                                    left = if (state.left == tab.id) remain.first().id else state.left,
                                    right = if (state.right == tab.id) remain.first().id else state.right,
                                ),
                            )
                        }.padding(4.dp),
                    )
                }
            }
            TextButton(
                onClick = {
                    if (state.tabs.size >= FilesWorkspaceStore.MaxTabs) return@TextButton
                    val id = UUID.randomUUID().toString()
                    persist(state.copy(tabs = state.tabs + FilesTabState(id, leftTab.path), left = id))
                },
            ) { Text(t("hc_add")) }
            FilterChip(
                selected = state.dual,
                onClick = { persist(state.copy(dual = !state.dual, right = leftTab.id, activeRight = false)) },
                label = { Text(t("preference_screen_desktop_mode"), fontSize = 11.sp) },
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            OutlinedTextField(
                uriDraft,
                { uriDraft = it },
                modifier = Modifier.weight(1f),
                label = { Text(t("telos_system_network")) },
                singleLine = true,
            )
            TextButton(onClick = {
                val uri = uriDraft.trim()
                if (!CloudPlacesStore.allowedUri(uri)) return@TextButton
                val place = NetworkPlace(uri.substringAfterLast('/').ifBlank { uri }, uri, null)
                clouds = clouds + place
                scope.launch(Dispatchers.IO) { CloudPlacesStore.save(cloudFile, clouds) }
                NetworkPlaces.open(place)
                uriDraft = ""
            }) { Text(t("hc_add")) }
        }
        if (!state.dual) {
            TelosFilesApp(
                start = File(leftTab.path),
                showHiddenFiles = showHiddenFiles,
                extraPlaces = clouds,
                onPathChange = { file -> persist(state.copy(tabs = state.tabs.map { if (it.id == leftTab.id) it.copy(path = file.absolutePath) else it })) },
            )
        } else {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    TelosFilesApp(
                        start = File(leftTab.path),
                        showHiddenFiles = showHiddenFiles,
                        extraPlaces = clouds,
                        onPathChange = { file ->
                            persist(state.copy(activeRight = false, tabs = state.tabs.map { if (it.id == leftTab.id) it.copy(path = file.absolutePath) else it }))
                        },
                    )
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    TelosFilesApp(
                        start = File(rightTab.path),
                        showHiddenFiles = showHiddenFiles,
                        extraPlaces = clouds,
                        onPathChange = { file ->
                            persist(state.copy(activeRight = true, tabs = state.tabs.map { if (it.id == rightTab.id) it.copy(path = file.absolutePath) else it }))
                        },
                    )
                }
            }
        }
    }
}
