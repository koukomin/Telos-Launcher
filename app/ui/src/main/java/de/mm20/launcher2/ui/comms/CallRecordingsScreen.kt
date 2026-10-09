package de.mm20.launcher2.ui.comms

import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.recording.CallAudioRecorder
import de.mm20.launcher2.comms.recording.CallRecordingFile
import de.mm20.launcher2.comms.recording.RecordingCrypto
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.SearchEmptyState
import de.mm20.launcher2.ui.component.TelosSearchBar
import kotlinx.coroutines.launch
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data object CallRecordingsRoute : NavKey

@Composable
fun CallRecordingsScreen() {
    val context = LocalContext.current
    var files by remember { mutableStateOf(emptyList<CallRecordingFile>()) }
    val loadScope = androidx.compose.runtime.rememberCoroutineScope()
    // listing also encrypts recordings of older versions: not on the main thread
    val reload: () -> Unit = {
        loadScope.launch { files = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CallAudioRecorder.list(context) } }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { RecordingCrypto.clearSharedCopies(context) }
        files = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CallAudioRecorder.list(context) }
    }
    var recQuery by remember { mutableStateOf("") }
    val shownFiles = de.mm20.launcher2.comms.search.TelosSearch.filter(files, recQuery) {
        listOf(it.number, SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(Date(it.file.lastModified())))
    }
    PreferenceScreen(title = { Text(stringResource(R.string.hc_call_recordings)) }) {
        if (files.isNotEmpty()) {
            item { TelosSearchBar(recQuery, { recQuery = it }, stringResource(R.string.tsp_search_call_recordings)) }
            if (shownFiles.isEmpty() && recQuery.isNotBlank()) item { SearchEmptyState(recQuery) }
        }
        if (files.isEmpty()) {
            item {
                Text(stringResource(R.string.hc_no_recordings_yet), modifier = Modifier.padding(16.dp))
            }
        }
        shownFiles.forEach { rec ->
            item {
                RecordingRow(rec, onChanged = reload)
            }
        }
    }
}

@Composable
private fun RecordingRow(rec: CallRecordingFile, onChanged: () -> Unit) {
    val context = LocalContext.current
    val whenStr = SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(rec.file.lastModified()))
    ListItem(
        headlineContent = { Text(rec.number) },
        supportingContent = { Text("$whenStr · ${rec.file.length() / 1024} KB") },
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    // the player gets a temporary readable copy, removed later
                    val readable = RecordingCrypto.readableCopy(context, rec.file)!!
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        readable,
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "audio/mp4")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.tryStartActivity(intent)
                }.onFailure {
                    Toast.makeText(context, context.getString(R.string.hc_cannot_play_recording), Toast.LENGTH_SHORT).show()
                }
            },
        trailingContent = {
            IconButton(onClick = {
                CallAudioRecorder.delete(rec.file)
                onChanged()
            }) {
                Icon(painterResource(R.drawable.delete_24px), contentDescription = null)
            }
        },
    )
}
