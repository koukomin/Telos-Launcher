package de.mm20.launcher2.ui.downloads

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.media.video.torrent.TorrentSession
import de.mm20.launcher2.downloads.DownloadManager
import de.mm20.launcher2.downloads.DownloadRequest
import de.mm20.launcher2.downloads.DownloadType
import de.mm20.launcher2.downloads.TorrentFile
import de.mm20.launcher2.downloads.TorrentRequest
import de.mm20.launcher2.downloads.engine.TorrentMetadata
import de.mm20.launcher2.downloads.engine.TorrentPreview
import de.mm20.launcher2.downloads.logic.FileSelection
import de.mm20.launcher2.downloads.logic.Formatting
import de.mm20.launcher2.downloads.logic.SeedRules
import de.mm20.launcher2.downloads.logic.TorrentSourceKind
import de.mm20.launcher2.downloads.logic.TorrentSources
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.launch

internal val RATIO_CHOICES = listOf(0, 50, 100, 150, 200, 300, 500)
internal val MINUTE_CHOICES = listOf(0, 30, 60, 180, 720, 1440, 4320)

@Composable
internal fun ratioLabel(x100: Int) = if (x100 <= 0) stringResource(R.string.dl_t_none) else SeedRules.formatRatio(x100 / 100.0)

@Composable
internal fun minutesLabel(m: Int) = when {
    m <= 0 -> stringResource(R.string.dl_t_none)
    m < 60 -> stringResource(R.string.dl_t_minutes, m)
    else -> stringResource(R.string.dl_t_hours, m / 60)
}

/**
 * Adds torrents: a magnet link, a .torrent address or a .torrent file. The metadata is read first, so the file
 * list with sizes can be shown and files can be chosen before anything is downloaded.
 */
@Composable
internal fun AddTorrentSheet(initialText: String, manager: DownloadManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by manager.settings.values.collectAsState()
    val cfg by TorrentSession.config.collectAsState()
    var text by rememberSaveable { mutableStateOf(initialText) }
    var preview by remember { mutableStateOf<TorrentPreview?>(null) }
    var previewSource by remember { mutableStateOf("") }
    var files by remember { mutableStateOf<List<TorrentFile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var sequential by remember(cfg.sequentialByDefault) { mutableStateOf(cfg.sequentialByDefault) }
    var stopAtDone by remember(cfg.stopAtDone) { mutableStateOf(cfg.stopAtDone) }
    var ratioX100 by remember(cfg.seedRatioX100) { mutableStateOf(cfg.seedRatioX100) }
    var minutes by remember(cfg.seedMinutes) { mutableStateOf(cfg.seedMinutes) }
    var startPaused by rememberSaveable { mutableStateOf(false) }

    val sources = remember(text) {
        val extracted = TorrentSources.extract(text)
        if (extracted.isNotEmpty()) extracted
        else text.trim().takeIf { TorrentSources.classify(it) == TorrentSourceKind.TorrentFile }?.let { listOf(it) } ?: emptyList()
    }

    // only the newest request counts: an older one that finishes later must not replace the preview
    var fetchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun fetch(source: String) {
        fetchJob?.cancel()
        loading = true
        error = null
        preview = null
        fetchJob = scope.launch {
            val me = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
            try {
                val p = TorrentMetadata.load(context, source)
                preview = p
                previewSource = source
                files = p.files
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                if (fetchJob === me) loading = false
            }
        }
    }

    // a .torrent file or address is cheap to read, a magnet link asks the swarm and waits for the button
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val only = TorrentSources.extract(initialText).ifEmpty { listOf(initialText.trim()) }.singleOrNull()
        if (only != null && TorrentSources.classify(only).let { it == TorrentSourceKind.TorrentFile || it == TorrentSourceKind.TorrentUrl }) fetch(only)
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            text = uri.toString()
            fetch(uri.toString())
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            folder = uri.toString()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.dl_t_add_title), style = MaterialTheme.typography.titleLarge)
            val wgRoute by manager.torrentRoute.collectAsState()
            val wgText = torrentWgStatusText(wgRoute)
            if (wgText != null) {
                Text(wgText, style = MaterialTheme.typography.bodyMedium)
            } else if (settings.proxyType != de.mm20.launcher2.downloads.ProxyType.None) {
                Text(
                    stringResource(if (settings.proxyType == de.mm20.launcher2.downloads.ProxyType.Http) R.string.au4_torrentproxy_note_http else R.string.au4_torrentproxy_note_socks),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; if (preview != null && it != previewSource) preview = null },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 5,
                label = { Text(stringResource(R.string.dl_t_source_hint)) },
                isError = text.isNotBlank() && sources.isEmpty(),
                supportingText = {
                    if (text.isNotBlank() && sources.isEmpty()) Text(stringResource(R.string.dl_t_source_invalid))
                    else if (sources.size > 1) Text(pluralStringResource(R.plurals.dl_t_sources_found, sources.size, sources.size))
                },
                trailingIcon = {
                    IconButton(onClick = {
                        val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                            ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        if (clip.isNotBlank()) text = if (text.isBlank()) clip else text.trimEnd() + "\n" + clip
                    }) { Icon(painterResource(R.drawable.content_copy_24px), stringResource(R.string.dl_paste)) }
                },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { filePicker.launch(arrayOf("application/x-bittorrent", "application/octet-stream", "*/*")) }) {
                    Text(stringResource(R.string.dl_t_choose_file))
                }
                if (sources.size == 1 && preview == null) {
                    Button(onClick = { fetch(sources.first()) }, enabled = !loading) { Text(stringResource(R.string.dl_t_fetch)) }
                }
            }
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.dl_t_fetching), style = MaterialTheme.typography.bodyMedium)
                }
            }
            error?.let { Text(stringResource(R.string.dl_t_fetch_failed, it), color = MaterialTheme.colorScheme.error) }

            val p = preview
            if (p != null) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                val selected = FileSelection.selectedCount(files)
                Text(
                    stringResource(R.string.dl_t_files_selected, selected, files.size, Formatting.size(FileSelection.selectedBytes(files))),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { files = FileSelection.select(files, files.map { it.index }.toSet()) }) { Text(stringResource(R.string.dl_t_select_all)) }
                    TextButton(onClick = { files = FileSelection.select(files, emptySet()) }) { Text(stringResource(R.string.dl_t_select_none)) }
                }
                TorrentFileList(
                    files = files,
                    onChange = { changes -> files = FileSelection.apply(files, changes) },
                    showProgress = false,
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                )
                if (selected == 0) Text(stringResource(R.string.dl_t_no_files_selected), color = MaterialTheme.colorScheme.error)
            }

            Text(stringResource(R.string.dl_folder), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.folder_24px), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    folder?.let { Uri.parse(it).lastPathSegment?.substringAfterLast(':') ?: it }
                        ?: settings.defaultFolder.takeIf { it.isNotEmpty() }?.let { Uri.parse(it).lastPathSegment?.substringAfterLast(':') }
                        ?: stringResource(R.string.dl_folder_default),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                )
                TextButton(onClick = { folderPicker.launch(null) }) { Text(stringResource(R.string.dl_folder_choose)) }
                if (folder != null) TextButton(onClick = { folder = null }) { Text(stringResource(R.string.dl_reset)) }
            }

            SwitchRow(stringResource(R.string.dl_t_sequential), sequential) { sequential = it }
            Text(stringResource(R.string.dl_t_sequential_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(stringResource(R.string.dl_t_seed_options), style = MaterialTheme.typography.labelLarge)
            SwitchRow(stringResource(R.string.dl_t_stop_at_done), stopAtDone) { stopAtDone = it }
            if (!stopAtDone) {
                Text(stringResource(R.string.dl_t_ratio_limit), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in RATIO_CHOICES) FilterChip(selected = ratioX100 == r, onClick = { ratioX100 = r }, label = { Text(ratioLabel(r)) })
                }
                Text(stringResource(R.string.dl_t_time_limit), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in MINUTE_CHOICES) FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text(minutesLabel(m)) })
                }
            }
            SwitchRow(stringResource(R.string.dl_start_paused), startPaused) { startPaused = it }

            val canAdd = if (p != null) FileSelection.selectedCount(files) > 0 else sources.isNotEmpty() && sources.none { TorrentSources.classify(it) != TorrentSourceKind.Magnet && TorrentSources.classify(it) != TorrentSourceKind.TorrentUrl }
            Button(
                enabled = canAdd && !loading,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    var duplicates = 0
                    val before = manager.tasks.value.size
                    if (p != null) {
                        manager.add(
                            DownloadRequest(
                                url = previewSource, type = DownloadType.Torrent, name = p.name, treeUri = folder, startPaused = startPaused,
                                torrent = TorrentRequest(
                                    torrentFile = p.torrentBytes, files = files, sequential = sequential, seedRatioX100 = ratioX100,
                                    seedMinutes = minutes, stopAtDone = stopAtDone, infoHash = p.infoHash, pieceLength = p.pieceLength,
                                    numPieces = p.numPieces, isPrivate = p.isPrivate, rootFolder = p.rootFolder,
                                ),
                            )
                        )
                        if (manager.tasks.value.size == before) duplicates++
                    } else {
                        // without the file list: everything is downloaded, files can be chosen later in the details
                        for (s in sources) {
                            val n = manager.tasks.value.size
                            manager.add(
                                DownloadRequest(
                                    url = s, type = DownloadType.Torrent, treeUri = folder, startPaused = startPaused,
                                    torrent = TorrentRequest(sequential = sequential, seedRatioX100 = ratioX100, seedMinutes = minutes, stopAtDone = stopAtDone),
                                )
                            )
                            if (manager.tasks.value.size == n) duplicates++
                        }
                    }
                    if (duplicates > 0) Toast.makeText(context, R.string.dl_t_duplicate, Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
            ) {
                Text(stringResource(if (p != null) R.string.dl_t_download_button else R.string.dl_t_add_without_list))
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
