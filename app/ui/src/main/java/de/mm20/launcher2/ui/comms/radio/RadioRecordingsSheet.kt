package de.mm20.launcher2.ui.comms.radio

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.comms.radio.RadioRecorder
import de.mm20.launcher2.comms.radio.RadioRecording
import de.mm20.launcher2.comms.radio.RadioRecordings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LauncherCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Shows the outcome of a finished recording once (toast). Place it once per screen. */
@Composable
fun RadioRecordingResultEffect() {
    val context = LocalContext.current
    val result by RadioRecorder.result.collectAsStateWithLifecycle()
    LaunchedEffect(result) {
        val r = result ?: return@LaunchedEffect
        val text = when (r.kind) {
            RadioRecorder.Kind.SAVED -> context.getString(R.string.au2_radio2_saved, r.fileName)
            RadioRecorder.Kind.SAVED_STREAM_ENDED -> context.getString(R.string.au2_radio2_saved_dropped, r.fileName)
            RadioRecorder.Kind.SAVED_STORAGE_FULL -> context.getString(R.string.au2_radio2_saved_full, r.fileName)
            RadioRecorder.Kind.NOTHING -> context.getString(R.string.au2_radio2_nothing_recorded)
            RadioRecorder.Kind.HLS -> context.getString(R.string.au2_radio2_hls)
            RadioRecorder.Kind.FAILED -> context.getString(R.string.au2_radio2_failed)
            RadioRecorder.Kind.SAVE_FAILED -> context.getString(R.string.au2_radio2_save_failed)
        }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        RadioRecorder.consumeResult()
    }
}

/** Row for the Collection tab that opens the list of recordings made by Telos Radio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioRecordingsEntry() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recState by RadioRecorder.state.collectAsStateWithLifecycle()
    val isRecording = recState is RadioRecorder.State.Recording
    var recordings by remember { mutableStateOf<List<RadioRecording>>(emptyList()) }
    var showSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<RadioRecording?>(null) }

    // reload when a recording has just finished (isRecording turns false) and when the sheet opens
    LaunchedEffect(isRecording, showSheet) {
        recordings = withContext(Dispatchers.IO) { RadioRecordings.list(context) }
    }

    LauncherCard(modifier = Modifier.fillMaxWidth().clickable { showSheet = true }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painterResource(R.drawable.folder_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.au2_radio2_recordings), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.au2_radio2_recordings_summary, recordings.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
                Text(
                    stringResource(R.string.au2_radio2_recordings),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (recordings.isEmpty()) {
                    Text(
                        stringResource(R.string.au2_radio2_recordings_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn {
                        items(recordings, key = { it.id }) { rec ->
                            RecordingRow(
                                rec = rec,
                                onOpen = { openRecording(context, rec) },
                                onShare = { shareRecording(context, rec) },
                                onDelete = { deleteTarget = rec },
                            )
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { rec ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.au2_radio2_delete_title)) },
            text = { Text(stringResource(R.string.au2_radio2_delete_message, rec.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { RadioRecordings.delete(context, rec) }
                        if (ok) {
                            recordings = recordings.filterNot { it.id == rec.id }
                        } else {
                            Toast.makeText(context, context.getString(R.string.au2_radio2_delete_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text(stringResource(R.string.hc_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.hc_cancel)) } },
        )
    }
}

@Composable
private fun RecordingRow(rec: RadioRecording, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(rec.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                Formatter.formatShortFileSize(context, rec.sizeBytes) + " · " +
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(rec.dateMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onShare) {
            Icon(painterResource(R.drawable.share_24px), contentDescription = stringResource(R.string.hc_share))
        }
        IconButton(onClick = onDelete) {
            Icon(painterResource(R.drawable.delete_24px), contentDescription = stringResource(R.string.hc_delete))
        }
    }
}

private fun openRecording(context: Context, rec: RadioRecording) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(rec.uri, rec.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.au2_radio2_no_app), Toast.LENGTH_SHORT).show()
    }
}

private fun shareRecording(context: Context, rec: RadioRecording) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(rec.mimeType)
        .putExtra(Intent.EXTRA_STREAM, rec.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, rec.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.au2_radio2_no_app), Toast.LENGTH_SHORT).show()
    }
}
