package de.mm20.launcher2.ui.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.helper.TelosArchiveBundle
import de.mm20.launcher2.i18n.R
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

@Composable
internal fun ArchiveBundleDialog(source: File, onDismiss: () -> Unit, onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember(source) { mutableStateOf(source.nameWithoutExtension.take(200)) }
    var notes by remember(source) { mutableStateOf("") }
    var license by remember(source) { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.telos_archive_bundle)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.telos_archive_bundle_info))
                OutlinedTextField(title, { title = it.take(200) }, enabled = !working,
                    label = { Text(stringResource(R.string.telos_archive_title)) })
                OutlinedTextField(notes, { notes = it.take(2000) }, enabled = !working,
                    label = { Text(stringResource(R.string.telos_archive_notes)) })
                for (item in listOf("CC0-1.0", "CC-BY-4.0", "CC-BY-SA-4.0")) {
                    FilterChip(license == item, { license = if (license == item) null else item },
                        label = { Text(item) }, enabled = !working)
                }
                if (error) Text(stringResource(R.string.telos_archive_bundle_failed))
            }
        },
        confirmButton = {
            TextButton(enabled = !working && title.isNotBlank(), onClick = {
                val archiveTitle = title
                val archiveNotes = notes
                val archiveLicense = license
                working = true
                error = false
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val staging = File(context.cacheDir, "archive-bundles/${UUID.randomUUID()}")
                            check(staging.mkdirs())
                            try {
                                val execution = currentCoroutineContext()
                                val output = File(source.parentFile, "${source.name}.telos-archive.zip")
                                TelosArchiveBundle.create(source, output, staging, archiveTitle, archiveNotes, archiveLicense) {
                                    execution.ensureActive()
                                }
                            } finally {
                                staging.deleteRecursively()
                            }
                        }
                        onComplete()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        error = true
                    } finally {
                        working = false
                    }
                }
            }) {
                Text(stringResource(R.string.telos_archive_bundle))
            }
        },
        dismissButton = {
            TextButton(enabled = !working, onClick = onDismiss) { Text(stringResource(R.string.hc_cancel)) }
        },
    )
}
