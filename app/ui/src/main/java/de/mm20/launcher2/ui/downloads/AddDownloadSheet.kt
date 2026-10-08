package de.mm20.launcher2.ui.downloads

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.downloads.DownloadCategory
import de.mm20.launcher2.downloads.DownloadManager
import de.mm20.launcher2.downloads.DownloadRequest
import de.mm20.launcher2.downloads.logic.Checksums
import de.mm20.launcher2.downloads.logic.LinkKind
import de.mm20.launcher2.downloads.logic.LinkParser
import de.mm20.launcher2.downloads.logic.MediaUrls
import de.mm20.launcher2.downloads.logic.TorrentSources
import de.mm20.launcher2.ui.R
import kotlin.math.roundToInt

/** Parses "Name: value" lines into headers */
internal fun parseHeaders(text: String): Map<String, String> =
    text.lines().mapNotNull { line ->
        val i = line.indexOf(':')
        if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
    }.filter { it.first.isNotEmpty() }.toMap()

@Composable
internal fun AddDownloadSheet(initialText: String, manager: DownloadManager, onDismiss: () -> Unit, onTorrent: (String) -> Unit = {}, onMedia: (String) -> Unit = {}) {
    val context = LocalContext.current
    val settings by manager.settings.values.collectAsState()
    var text by remember { mutableStateOf(initialText) }
    var folder by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf<DownloadCategory?>(null) }
    var connections by remember { mutableStateOf(0) }
    var advanced by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }
    var referer by remember { mutableStateOf("") }
    var cookies by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("") }
    var checksum by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    var mirrors by remember { mutableStateOf("") }
    var startPaused by remember { mutableStateOf(false) }
    var clip by remember { mutableStateOf<String?>(null) }

    // the clipboard is only read on its own when the user switched "detect links in the clipboard" on; the paste button always works
    LaunchedEffect(settings.detectClipboard) { clip = if (settings.detectClipboard) clipboardLink(context) else null }
    // magnet links and .torrent addresses go to the torrent sheet, where the files can be chosen
    LaunchedEffect(text) {
        if (TorrentSources.containsTorrent(text)) onTorrent(text)
        // a single link to a video site goes to the media sheet, where quality and extras can be chosen
        else if (MediaUrls.classify(text) == LinkKind.Media) onMedia(text.trim())
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            folder = uri.toString()
        }
    }

    val links = remember(text) { LinkParser.extractHttp(text) }
    val checksumInvalid = checksum.isNotBlank() && Checksums.parse(checksum) == null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.dl_add_title), style = MaterialTheme.typography.titleLarge)

            clip?.let { c ->
                if (!text.contains(c)) {
                    AssistChip(
                        onClick = { text = if (text.isBlank()) c else text.trimEnd() + "\n" + c },
                        label = { Text(stringResource(R.string.dl_clipboard_link, c.take(48)), maxLines = 1) },
                        leadingIcon = { Icon(painterResource(R.drawable.link_24px), null, Modifier.size(18.dp)) },
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 6,
                label = { Text(stringResource(R.string.dl_links_hint)) },
                isError = text.isNotBlank() && links.isEmpty(),
                supportingText = {
                    if (text.isNotBlank() && links.isEmpty()) Text(stringResource(R.string.dl_invalid_links))
                    else if (links.size > 1) Text(pluralStringResource(R.plurals.dl_links_found, links.size, links.size))
                },
                trailingIcon = {
                    IconButton(onClick = {
                        val c = clipboardLink(context)
                        if (c != null) text = if (text.isBlank()) c else text.trimEnd() + "\n" + c
                    }) { Icon(painterResource(R.drawable.content_copy_24px), stringResource(R.string.dl_paste)) }
                },
            )

            TextButton(onClick = { onMedia(links.firstOrNull().orEmpty()) }) { Text(stringResource(R.string.dl_m_open)) }

            Text(stringResource(R.string.dl_folder), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.folder_24px), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    folder?.let { Uri.parse(it).lastPathSegment?.substringAfterLast(':') ?: it }
                        ?: settings.defaultFolder.takeIf { it.isNotEmpty() }?.let { Uri.parse(it).lastPathSegment?.substringAfterLast(':') }
                        ?: stringResource(R.string.dl_folder_default),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                TextButton(onClick = { picker.launch(null) }) { Text(stringResource(R.string.dl_folder_choose)) }
                if (folder != null) TextButton(onClick = { folder = null }) { Text(stringResource(R.string.dl_reset)) }
            }

            Text(stringResource(R.string.dl_category), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.dl_category_auto)) })
                for (c in DownloadCategory.entries) {
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(categoryLabel(c)) })
                }
            }

            Text(
                if (connections == 0) stringResource(R.string.dl_connections_default, settings.connections)
                else stringResource(R.string.dl_connections_count, connections),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = connections.toFloat(),
                onValueChange = { connections = it.roundToInt() },
                valueRange = 0f..16f,
                steps = 15,
            )

            TextButton(onClick = { advanced = !advanced }) { Text(stringResource(R.string.dl_advanced)) }
            AnimatedVisibility(advanced) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (links.size <= 1) Field(name, { name = it }, R.string.dl_name)
                    Field(userAgent, { userAgent = it }, R.string.dl_user_agent)
                    Field(referer, { referer = it }, R.string.dl_referer)
                    Field(cookies, { cookies = it }, R.string.dl_cookies)
                    Field(headers, { headers = it }, R.string.dl_headers, singleLine = false)
                    Field(mirrors, { mirrors = it }, R.string.dl_mirrors, singleLine = false)
                    Field(checksum, { checksum = it }, R.string.dl_checksum, error = checksumInvalid)
                    Field(limit, { limit = it.filter(Char::isDigit) }, R.string.dl_speed_limit_task)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.Switch(checked = startPaused, onCheckedChange = { startPaused = it })
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.dl_start_paused))
                    }
                }
            }

            Button(
                onClick = {
                    val common = DownloadRequest(
                        url = "",
                        treeUri = folder,
                        category = category,
                        connections = connections,
                        headers = parseHeaders(headers),
                        userAgent = userAgent,
                        referer = referer,
                        cookies = cookies,
                        checksum = checksum.takeIf { !checksumInvalid },
                        speedLimitBps = (limit.toLongOrNull() ?: 0L) * 1024,
                        startPaused = startPaused,
                        mirrors = LinkParser.extractHttp(mirrors),
                    )
                    manager.addAll(links.map { common.copy(url = it, name = if (links.size == 1) name else "", mirrors = if (links.size == 1) common.mirrors else emptyList(), checksum = if (links.size == 1) common.checksum else null) })
                    onDismiss()
                },
                enabled = links.isNotEmpty() && !checksumInvalid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(pluralStringResource(R.plurals.dl_add_count, links.size.coerceAtLeast(1), links.size.coerceAtLeast(1)))
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: Int, singleLine: Boolean = true, error: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) }, singleLine = singleLine, minLines = if (singleLine) 1 else 2, isError = error,
    )
}
