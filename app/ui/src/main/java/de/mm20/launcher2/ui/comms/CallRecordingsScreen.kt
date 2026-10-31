package de.mm20.launcher2.ui.comms
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val comms: de.mm20.launcher2.preferences.comms.CommsSettings = org.koin.compose.koinInject()
    val lock by comms.lockCallRecordings.collectAsStateWithLifecycle(true)
    val unlocked by de.mm20.launcher2.comms.privacy.PrivacySession.recordingsUnlocked.collectAsStateWithLifecycle()
    val backStack = de.mm20.launcher2.ui.locals.LocalBackStack.current
    PreferenceScreen(title = { Text(stringResource(I18nR.string.hc_call_recordings)) }) {
        item {
            de.mm20.launcher2.ui.component.AuthGate(
                enabled = lock,
                title = stringResource(I18nR.string.auth_gate_recordings),
                unlocked = unlocked,
                onUnlocked = { de.mm20.launcher2.comms.privacy.PrivacySession.unlockRecordings() },
                onCancelled = { backStack.removeLastOrNull() },
            ) {
                CallRecordingsList(embeddedSearch = true)
            }
        }
    }
}

/** Call recordings list for Phone settings and the Dialer Recordings tab. */
@Composable
fun CallRecordingsList(
    searchQuery: String? = null,
    embeddedSearch: Boolean = false,
) {
    val context = LocalContext.current
    var files by remember { mutableStateOf(emptyList<CallRecordingFile>()) }
    val loadScope = androidx.compose.runtime.rememberCoroutineScope()
    val reload: () -> Unit = {
        loadScope.launch { files = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CallAudioRecorder.list(context) } }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { RecordingCrypto.clearSharedCopies(context) }
        files = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CallAudioRecorder.list(context) }
    }
    val directory: de.mm20.launcher2.comms.repository.ContactDirectoryRepository = org.koin.compose.koinInject()
    val contacts by remember(directory) { directory.observeContacts() }.collectAsStateWithLifecycle(emptyList())
    val names by androidx.compose.runtime.produceState(emptyMap<String, String?>(), files, contacts) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            files.associate { rec ->
                rec.file.absolutePath to contacts.firstOrNull { person -> person.phoneNumbers.any {
                    de.mm20.launcher2.comms.recording.recordingNumberMatches(it, rec.number)
                } }?.displayName?.takeIf { it.isNotBlank() }
            }
        }
    }
    var recQuery by remember { mutableStateOf("") }
    val query = searchQuery ?: recQuery
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()) }
    val shownFiles = remember(files, query, names) {
        de.mm20.launcher2.comms.search.TelosSearch.filter(files, query) {
            listOf(it.number, names[it.file.absolutePath].orEmpty(), dateFormat.format(Date(it.modifiedAtMillis)))
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (embeddedSearch) Modifier else Modifier.fillMaxSize().verticalScroll(rememberScrollState())),
    ) {
        if (embeddedSearch && files.isNotEmpty()) {
            TelosSearchBar(recQuery, { recQuery = it }, stringResource(I18nR.string.tsp_search_call_recordings))
        }
        if (shownFiles.isEmpty() && query.isNotBlank()) {
            SearchEmptyState(query)
        } else if (files.isEmpty()) {
            Text(stringResource(I18nR.string.hc_no_recordings_yet), modifier = Modifier.padding(16.dp))
        } else {
            shownFiles.forEach { rec ->
                RecordingRow(rec, names[rec.file.absolutePath], onChanged = reload)
            }
        }
    }
}

@Composable
private fun RecordingRow(rec: CallRecordingFile, contactName: String?, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    val whenStr = remember(rec.file) { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(rec.modifiedAtMillis)) }
    // the recorder names a recording without a number "Unknown"
    val title = contactName ?: if (rec.number == "Unknown") stringResource(I18nR.string.widget_name_unknown) else rec.number
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                if (contactName != null) Text(rec.number)
                Text(whenStr)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                scope.launch {
                    // decrypting the recording is file work, not for the main thread
                    val opened = runCatching {
                        // the player gets a temporary readable copy, removed later
                        val readable = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            RecordingCrypto.readableCopy(context, rec.file)
                        } ?: error("no readable copy")
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
                    }.getOrDefault(false)
                    if (!opened) {
                        Toast.makeText(context, context.getString(I18nR.string.hc_cannot_play_recording), Toast.LENGTH_SHORT).show()
                    }
                }
            },
        trailingContent = {
            IconButton(onClick = { confirmDelete = true }) {
                Icon(painterResource(BaseR.drawable.delete_24px), contentDescription = stringResource(I18nR.string.hc_delete))
            }
        },
    )
    if (confirmDelete) de.mm20.launcher2.ui.trash.UriDeleteDialog(android.net.Uri.fromFile(rec.file),
        onDeleted = onChanged, onDismiss = { confirmDelete = false })

}
