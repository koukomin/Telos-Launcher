package de.mm20.launcher2.ui.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.downloads.TransferUploader
import de.mm20.launcher2.downloads.UploadService
import de.mm20.launcher2.i18n.R
import de.mm20.launcher2.ui.component.virtualAppPopup
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CloudUploadDialog(entry: FsEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    CloudUploadDialog(entry.name, onDismiss) { FileActions.uriFor(context, entry.path, rootMode = false) }
}

@Composable
internal fun CloudUploadDialog(fileName: String, onDismiss: () -> Unit, openUri: suspend () -> Uri?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf<UploadService?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = virtualAppPopup(),
        title = { Text(stringResource(R.string.files_cloud_upload)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(fileName)
                Text(stringResource(R.string.files_cloud_warning))
                if (link == null) {
                    UploadService.entries.forEach { candidate ->
                        TextButton(enabled = !busy, onClick = { provider = candidate; failed = false }) {
                            Text(if (provider == candidate) "✓ ${candidate.host}" else candidate.host)
                        }
                    }
                }
                if (busy) Text(stringResource(R.string.files_cloud_uploading))
                if (failed) Text(stringResource(R.string.files_cloud_failed))
                link?.let { Text(stringResource(R.string.files_cloud_copied)); Text(it) }
            }
        },
        confirmButton = {
            val selected = provider
            TextButton(
                enabled = !busy && selected != null && link == null,
                onClick = {
                    if (selected == null) return@TextButton
                    busy = true
                    failed = false
                    scope.launch {
                        try {
                            val uri = openUri()
                            val url = uri?.let { TransferUploader.upload(context, it, selected).getOrNull()?.downloadUrl }
                            if (url == null) {
                                failed = true
                            } else {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(null, url))
                                link = url
                            }
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.files_cloud_upload)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(if (link == null) android.R.string.cancel else R.string.hc_close)) }
        },
    )
}

@Composable
internal fun CloudUploadUriDialog(uri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val name by produceState<String?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        }
    }
    name?.let { CloudUploadDialog(it, onDismiss) { uri } }
}

/** Use Android's picker so Telos receives access to exactly the chosen file. */
@Composable
internal fun CloudUploadPicker(open: Boolean, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) onDismiss() else selected = uri
    }
    androidx.compose.runtime.LaunchedEffect(open) {
        if (open) picker.launch("*/*")
    }
    selected?.let { uri ->
        CloudUploadUriDialog(uri) {
            selected = null
            onDismiss()
        }
    }
}
