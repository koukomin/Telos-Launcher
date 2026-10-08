package de.mm20.launcher2.ui.files.remote

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data object ConnectionsRoute : NavKey

private typealias Icons = de.mm20.launcher2.base.R.drawable

/** Where the saved network and cloud storages are managed. Used in the settings and from Telos Files. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val store = remember { ConnectionStore(context) }
    var list by remember { mutableStateOf(store.all().sortedBy { it.name.lowercase() }) }
    var editing by remember { mutableStateOf<RemoteConnection?>(null) }
    var picking by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Cloud and network storage") },
                navigationIcon = { IconButton(onClick = { backStack.removeLastOrNull() }) { Icon(painterResource(Icons.arrow_back_24px), contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { picking = true }) { Icon(painterResource(Icons.add_24px), contentDescription = "Add") }
                DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                    RemoteType.values().forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            onClick = { picking = false; editing = RemoteConnection(UUID.randomUUID().toString(), type, type.label, port = type.defaultPort) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(painterResource(Icons.cloud_20px), contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                Text("Add a Nextcloud, ownCloud, WebDAV, SFTP, SMB or FTP server, a Dropbox, Google Drive or OneDrive account, or the cloud app of the phone (it uses the phone's account). They show up in Telos Files.", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(list, key = { it.id }) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text(c.type.label + if (c.host.isNotEmpty()) " · ${c.host}" else if (c.user.isNotEmpty()) " · ${c.user}" else "") },
                    leadingContent = { Icon(painterResource(if (c.type.cloud) Icons.cloud_20px else Icons.storage_24px), contentDescription = null) },
                    modifier = Modifier.clickable { editing = c },
                )
            }
        }
    }

    editing?.let { draft ->
        ConnectionEditor(
            initial = draft,
            isNew = list.none { it.id == draft.id },
            onSave = { store.save(it); list = store.all().sortedBy { c -> c.name.lowercase() }; editing = null },
            onDelete = { store.delete(draft.id); list = store.all().sortedBy { c -> c.name.lowercase() }; editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ConnectionEditor(initial: RemoteConnection, isNew: Boolean, onSave: (RemoteConnection) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var c by remember { mutableStateOf(initial) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val type = c.type

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New ${type.label} connection" else c.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(type.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Field("Name", c.name) { c = c.copy(name = it) }
                if (type == RemoteType.System) {
                    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
                        if (uri != null) {
                            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            c = c.copy(host = uri.toString(), name = c.name.ifBlank { Uri.decode(uri.toString()).substringAfterLast(':').substringAfterLast('/').ifBlank { "Cloud" } })
                        }
                    }
                    Text(if (c.host.isBlank()) "No folder chosen" else Uri.decode(c.host).substringAfter("tree/"), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    OutlinedButton(onClick = { picker.launch(null) }) { Text(if (c.host.isBlank()) "Choose a folder" else "Choose another folder") }
                } else if (!type.cloud) {
                    Field("Server", c.host, "cloud.example.com") { c = c.copy(host = it) }
                    Field("Port", if (c.port > 0) c.port.toString() else "", type.defaultPort.toString()) { c = c.copy(port = it.filter(Char::isDigit).toIntOrNull() ?: 0) }
                    Field("User name", c.user) { c = c.copy(user = it) }
                    Field(if (type == RemoteType.Sftp && c.privateKey.isNotBlank()) "Password for the key (if any)" else "Password", c.password, password = true) { c = c.copy(password = it) }
                    Field(
                        when (type) { RemoteType.Smb -> "Shared folder name"; RemoteType.Sftp, RemoteType.Ftp -> "Start folder (optional)"; else -> "Folder on the server (optional)" },
                        c.path,
                    ) { c = c.copy(path = it) }
                    if (type != RemoteType.Sftp && type != RemoteType.Smb) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (type == RemoteType.Ftp) "Encrypt with TLS (FTPS)" else "Use https", Modifier.weight(1f))
                            Switch(checked = c.tls, onCheckedChange = { c = c.copy(tls = it) })
                        }
                    }
                    if (type == RemoteType.Sftp) {
                        Field("Private key (optional, paste the key file)", c.privateKey, minLines = 3) { c = c.copy(privateKey = it) }
                        if (c.fingerprint.isNotEmpty()) Text("Server key: ${c.fingerprint}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    CloudFields(c, onChange = { c = it }, onStatus = { status = it })
                }
                status?.let { Text(it, color = if (it.startsWith("Connected")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = c.name.isNotBlank() && !busy && (type != RemoteType.System || c.host.isNotBlank()), onClick = { onSave(c) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(enabled = !busy, onClick = {
                    busy = true; status = "Connecting…"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                // a saved fingerprint is only remembered with a saved connection: test with the draft as it is
                                ClientFactory.create(context, c).use { it.list("/").size }
                            }
                        }
                        busy = false
                        status = result.fold({ "Connected, $it items in the start folder" }, { "Could not connect: ${it.message}" })
                    }
                }) { Text("Test") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun Field(label: String, value: String, placeholder: String = "", password: Boolean = false, minLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = minLines == 1, minLines = minLines,
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}
