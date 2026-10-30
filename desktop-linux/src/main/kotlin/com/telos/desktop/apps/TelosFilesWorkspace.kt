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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.files.CloudPlacesStore
import com.telos.desktop.files.DavSecrets
import com.telos.desktop.files.NextcloudLoginFlow
import com.telos.desktop.files.WebDavClient
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var userDraft by remember { mutableStateOf("") }
    var passDraft by remember { mutableStateOf("") }
    var flowStatus by remember { mutableStateOf("") }
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
                modifier = Modifier.weight(1.4f),
                label = { Text(t("telos_system_network")) },
                singleLine = true,
            )
            OutlinedTextField(
                userDraft,
                { userDraft = it },
                modifier = Modifier.weight(0.7f).padding(start = 4.dp),
                label = { Text(t("hc_user")) },
                singleLine = true,
            )
            OutlinedTextField(
                passDraft,
                { passDraft = it },
                modifier = Modifier.weight(0.7f).padding(start = 4.dp),
                label = { Text(t("hc_password")) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            TextButton(onClick = {
                val uri = uriDraft.trim()
                if (!CloudPlacesStore.allowedUri(uri)) return@TextButton
                val user = userDraft.trim()
                val normalized = WebDavClient.normalize(uri, user)
                val place = NetworkPlace(
                    uri.substringAfterLast('/').ifBlank { uri },
                    uri,
                    null,
                    user.ifBlank { null },
                )
                if (passDraft.isNotEmpty()) DavSecrets.put(uri, passDraft)
                if (passDraft.isNotEmpty()) DavSecrets.put(normalized, passDraft)
                passDraft = ""
                clouds = clouds + place
                scope.launch(Dispatchers.IO) { CloudPlacesStore.save(cloudFile, clouds) }
                if (uri.startsWith("smb") || uri.startsWith("sftp") || uri.startsWith("file")) {
                    NetworkPlaces.open(place)
                }
                uriDraft = ""
                userDraft = ""
            }) { Text(t("hc_add")) }
            TextButton(onClick = {
                val server = uriDraft.trim().ifBlank { return@TextButton }
                if (!server.startsWith("http://") && !server.startsWith("https://") && !server.startsWith("dav")) return@TextButton
                scope.launch(Dispatchers.IO) {
                    val session = NextcloudLoginFlow.start(server)
                    if (session == null) {
                        withContext(Dispatchers.Main) { flowStatus = t("dl_p3_extract_failed") }
                        return@launch
                    }
                    withContext(Dispatchers.Main) { flowStatus = session.loginUrl }
                    NextcloudLoginFlow.openBrowser(session.loginUrl)
                    repeat(90) {
                        delay(2000)
                        val result = NextcloudLoginFlow.poll(session) ?: return@repeat
                        DavSecrets.put(result.server, result.appPassword)
                        val dav = WebDavClient.normalize(result.server, result.loginName)
                        DavSecrets.put(dav, result.appPassword)
                        val place = NetworkPlace(result.loginName, result.server, null, result.loginName)
                        val snapshot = withContext(Dispatchers.Main) {
                            val next = clouds + place
                            clouds = next
                            flowStatus = result.loginName
                            uriDraft = ""
                            next
                        }
                        CloudPlacesStore.save(cloudFile, snapshot)
                        return@launch
                    }
                }
            }) { Text(t("preference_screen_integrations")) }
        }
        if (flowStatus.isNotEmpty()) {
            Text(flowStatus, color = TelosColors.secondary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp))
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
