package de.mm20.launcher2.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.helper.StorageAnalysis
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.*
import java.io.File

@Composable
internal fun StorageAnalysisDialog(path: String, onOpenFolder: (String) -> Unit, onDismiss: () -> Unit) {
    var scan by remember(path) { mutableStateOf<StorageAnalysis.Result?>(null) }
    var duplicates by remember(path) { mutableStateOf<List<List<StorageAnalysis.Record>>?>(null) }
    var busy by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(path) {
        try {
            scan = withContext(Dispatchers.IO) { val job = currentCoroutineContext(); StorageAnalysis.scan(File(path)) { job.ensureActive() } }
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { failed = true }
        finally { busy = false }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.telos_files_analyze)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.telos_files_analyzing)) }
                if (failed) Text(stringResource(R.string.telos_files_analysis_failed), color = MaterialTheme.colorScheme.error)
                scan?.let { result ->
                    Text(stringResource(R.string.telos_files_analysis_total, result.files.size, formatSize(result.bytes)), style = MaterialTheme.typography.titleMedium)
                    if (result.truncated || result.unreadable > 0) Text(stringResource(R.string.telos_files_analysis_partial), color = MaterialTheme.colorScheme.tertiary)
                    result.files.groupBy { FileKind.of(it.file.name, false) }.entries.sortedByDescending { it.value.sumOf { f -> f.size } }.forEach { (kind, records) ->
                        val bytes = records.sumOf { it.size }
                        Text("${stringResource(kind.labelRes)} · ${formatSize(bytes)}")
                        LinearProgressIndicator(progress = { if (result.bytes > 0) bytes.toFloat() / result.bytes else 0f }, modifier = Modifier.fillMaxWidth())
                    }
                    Text(stringResource(R.string.telos_files_largest), style = MaterialTheme.typography.titleMedium)
                    result.files.sortedByDescending { it.size }.take(10).forEach { record ->
                        Column(Modifier.fillMaxWidth().clickable { onOpenFolder(record.file.parent.orEmpty()); onDismiss() }.padding(vertical = 4.dp)) {
                            Text(record.file.name, style = MaterialTheme.typography.bodyMedium)
                            Text("${formatSize(record.size)} · ${record.file.parent}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(enabled = !busy && duplicates == null, onClick = {
                        scope.launch {
                            busy = true
                            try {
                                duplicates = withContext(Dispatchers.IO) { val job = currentCoroutineContext(); StorageAnalysis.duplicates(result.files) { job.ensureActive() } }
                            } catch (e: CancellationException) { throw e }
                              catch (_: Exception) { failed = true }
                            finally { busy = false }
                        }
                    }) { Text(stringResource(R.string.telos_files_find_duplicates)) }
                    duplicates?.let { groups ->
                        Text(stringResource(R.string.telos_files_duplicates_count, groups.size), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.telos_files_duplicates_hint), style = MaterialTheme.typography.bodySmall)
                        groups.take(50).forEach { group ->
                            HorizontalDivider()
                            Text(formatSize(group.first().size * (group.size - 1)), style = MaterialTheme.typography.titleSmall)
                            group.forEach { record -> Text(record.file.path, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth().clickable { onOpenFolder(record.file.parent.orEmpty()); onDismiss() }.padding(vertical = 6.dp)) }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.hc_close)) } })
}
