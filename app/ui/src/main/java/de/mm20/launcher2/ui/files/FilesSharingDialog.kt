package de.mm20.launcher2.ui.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.mm20.launcher2.comms.files.FilesSharingServer
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Visible-session sharing; leaving the foreground closes all sockets immediately. */
@Composable
fun FilesSharingDialog(path: String, onDismiss: () -> Unit) {
    var server by remember { mutableStateOf<FilesSharingServer?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val disposed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val pending = remember { java.util.concurrent.atomic.AtomicReference<FilesSharingServer?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { server?.close(); server = null }
        }
        lifecycle.addObserver(observer)
        onDispose { disposed.set(true); pending.getAndSet(null)?.close(); lifecycle.removeObserver(observer); server?.close(); server = null }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.telos_files_sharing_title)) },
        text = { Column {
            Text(stringResource(R.string.telos_files_sharing_description))
            Text(path, Modifier.padding(top = 8.dp))
            server?.let { sharing -> SelectionContainer { Text(sharing.url, Modifier.padding(top = 12.dp)) } }
            if (failed) Text(stringResource(R.string.telos_files_sharing_failed), color = MaterialTheme.colorScheme.error)
            if (busy) LinearProgressIndicator()
        } },
        confirmButton = {
            if (server != null) TextButton(onClick = { server?.close(); server = null }) { Text(stringResource(R.string.telos_files_sharing_stop)) }
            else TextButton(enabled = !busy, onClick = {
                busy = true; failed = false
                scope.launch {
                    val opened = withContext(Dispatchers.IO) { runCatching {
                        val address = FilesSharingServer.lanAddresses().firstOrNull() ?: error("No LAN")
                        FilesSharingServer(File(path), address).also { created ->
                            pending.set(created)
                            if (disposed.get()) pending.getAndSet(null)?.close()
                        }
                    }.getOrNull() }
                    pending.compareAndSet(opened, null)
                    if (disposed.get() || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) opened?.close()
                    else { server = opened; failed = opened == null }
                    busy = false
                }
            }) { Text(stringResource(R.string.telos_files_sharing_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.telos_files_sharing_close)) } },
    )
}
