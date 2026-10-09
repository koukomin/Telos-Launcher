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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.mm20.launcher2.downloads.DownloadManager
import de.mm20.launcher2.downloads.DownloadRequest
import de.mm20.launcher2.downloads.DownloadType
import de.mm20.launcher2.downloads.MediaData
import de.mm20.launcher2.downloads.SubtitleMode
import de.mm20.launcher2.downloads.logic.Formatting
import de.mm20.launcher2.downloads.logic.LinkParser
import de.mm20.launcher2.downloads.logic.MediaFormats
import de.mm20.launcher2.downloads.logic.MediaInfo
import de.mm20.launcher2.downloads.media.MediaRuntime
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Video and audio from sites (yt-dlp): paste a link, analyze it, choose quality and extras, download */
@Composable
internal fun AddMediaSheet(initialUrl: String, manager: DownloadManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val runtime: MediaRuntime = koinInject()
    val scope = rememberCoroutineScope()
    val settings = manager.settings.current

    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var info by remember { mutableStateOf<MediaInfo?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var heightCap by rememberSaveable { mutableStateOf(0) }
    var audioOnly by rememberSaveable { mutableStateOf(false) }
    var audioFormat by rememberSaveable { mutableStateOf("mp3") }
    var container by rememberSaveable { mutableStateOf("") }
    var subtitles by remember { mutableStateOf(SubtitleMode.Off) }
    var subLangs by rememberSaveable { mutableStateOf("en.*") }
    var embedThumb by rememberSaveable { mutableStateOf(false) }
    var embedMeta by rememberSaveable { mutableStateOf(false) }
    var sponsor by rememberSaveable { mutableStateOf(false) }
    var useCookies by remember { mutableStateOf(false) }
    var cookiesVersion by remember { mutableStateOf(0) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            folder = uri.toString()
        }
    }

    val link = remember(url) { LinkParser.extractHttp(url).firstOrNull() }
    val heights = info?.heights?.takeIf { it.isNotEmpty() } ?: MediaFormats.resolutionCaps

    fun analyze() {
        val l = link ?: return
        analyzing = true; error = null; info = null
        scope.launch {
            try {
                val r = runtime.analyze(l, useCookies && runtime.hasCookies, manager.settings.current.proxyUrl)
                info = r
                selected = r.entries.map { it.url }.toSet()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = context.getString(R.string.dl_m_analyze_failed, e.message.orEmpty())
            }
            analyzing = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.dl_m_title), style = MaterialTheme.typography.titleLarge)

            if (!runtime.isAvailable) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.dl_m_missing_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(stringResource(R.string.dl_m_missing_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it; info = null; error = null },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1, maxLines = 3,
                label = { Text(stringResource(R.string.dl_m_url_hint)) },
                isError = url.isNotBlank() && link == null,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { analyze() }, enabled = link != null && !analyzing && runtime.isAvailable) {
                    Text(stringResource(R.string.dl_m_analyze))
                }
                if (analyzing) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.dl_m_analyzing), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            info?.let { i ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (i.thumbnail.isNotBlank()) {
                        AsyncImage(
                            model = i.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 120.dp, height = 68.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(i.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(
                            i.uploader.takeIf { it.isNotBlank() },
                            i.durationSec.takeIf { it > 0 }?.let { Formatting.duration(it) },
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!i.isPlaylist) {
                            val est = MediaFormats.estimateBytes(i, MediaData(heightCap = heightCap, audioOnly = audioOnly))
                            if (est > 0) Text("~" + Formatting.size(est), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (i.isPlaylist) {
                    Text(stringResource(R.string.dl_m_playlist, i.entries.size), style = MaterialTheme.typography.labelLarge)
                    Row {
                        TextButton(onClick = { selected = i.entries.map { it.url }.toSet() }) { Text(stringResource(R.string.dl_m_select_all)) }
                        TextButton(onClick = { selected = emptySet() }) { Text(stringResource(R.string.dl_m_select_none)) }
                    }
                    Column {
                        for (e in i.entries.take(300)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = e.url in selected, onCheckedChange = { selected = if (it) selected + e.url else selected - e.url })
                                Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                if (e.durationSec > 0) Text(Formatting.duration(e.durationSec), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            Text(stringResource(R.string.dl_m_quality), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !audioOnly && heightCap == 0, onClick = { audioOnly = false; heightCap = 0 }, label = { Text(stringResource(R.string.dl_m_quality_best)) })
                for (h in heights) {
                    FilterChip(selected = !audioOnly && heightCap == h, onClick = { audioOnly = false; heightCap = h }, label = { Text(stringResource(R.string.dl_m_quality_p, h)) })
                }
                FilterChip(selected = audioOnly, onClick = { audioOnly = true }, label = { Text(stringResource(R.string.dl_m_audio_only)) })
            }
            if (audioOnly) {
                Text(stringResource(R.string.dl_m_audio_format), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (f in MediaFormats.audioFormats) FilterChip(selected = audioFormat == f, onClick = { audioFormat = f }, label = { Text(f) })
                }
            } else {
                Text(stringResource(R.string.dl_m_container), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in MediaFormats.containers) {
                        FilterChip(selected = container == c, onClick = { container = c }, label = { Text(if (c.isEmpty()) stringResource(R.string.dl_m_container_auto) else c) })
                    }
                }
                Text(stringResource(R.string.dl_m_subtitles), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = subtitles == SubtitleMode.Off, onClick = { subtitles = SubtitleMode.Off }, label = { Text(stringResource(R.string.dl_m_sub_off)) })
                    FilterChip(selected = subtitles == SubtitleMode.Files, onClick = { subtitles = SubtitleMode.Files }, label = { Text(stringResource(R.string.dl_m_sub_files)) })
                    FilterChip(selected = subtitles == SubtitleMode.Embed, onClick = { subtitles = SubtitleMode.Embed }, label = { Text(stringResource(R.string.dl_m_sub_embed)) })
                }
                if (subtitles != SubtitleMode.Off) {
                    OutlinedTextField(value = subLangs, onValueChange = { subLangs = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.dl_m_sub_langs)) })
                }
            }
            SwitchRow(embedThumb, { embedThumb = it }, R.string.dl_m_embed_thumb)
            SwitchRow(embedMeta, { embedMeta = it }, R.string.dl_m_embed_meta)
            SwitchRow(sponsor, { sponsor = it }, R.string.dl_m_sponsorblock)

            // sites that need a login
            val hasCookies = remember(cookiesVersion) { runtime.hasCookies }
            if (hasCookies) SwitchRow(useCookies, { useCookies = it }, R.string.dl_m_use_cookies)
            MediaCookieControls(runtime, link, onChanged = { cookiesVersion++; if (runtime.hasCookies) useCookies = true })

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

            Text(stringResource(R.string.dl_m_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val playlist = info?.takeIf { it.isPlaylist }
            val count = if (playlist != null) selected.size else if (link != null) 1 else 0
            Button(
                onClick = {
                    val base = MediaData(
                        heightCap = if (audioOnly) 0 else heightCap, audioOnly = audioOnly, audioFormat = audioFormat, container = if (audioOnly) "" else container,
                        subtitles = if (audioOnly) SubtitleMode.Off else subtitles, subLangs = subLangs, embedThumbnail = embedThumb, embedMetadata = embedMeta,
                        sponsorBlock = sponsor, useCookies = useCookies && runtime.hasCookies,
                    )
                    val common = DownloadRequest(url = "", type = DownloadType.Media, treeUri = folder)
                    if (playlist != null) {
                        manager.addAll(playlist.entries.filter { it.url in selected }.map { e ->
                            common.copy(url = e.url, name = e.title, media = base.copy(title = e.title, thumbnail = e.thumbnail, durationSec = e.durationSec, uploader = playlist.uploader))
                        })
                    } else {
                        val i = info
                        manager.add(
                            common.copy(
                                url = link!!, name = i?.title.orEmpty(),
                                media = base.copy(
                                    title = i?.title.orEmpty(), thumbnail = i?.thumbnail.orEmpty(), durationSec = i?.durationSec ?: 0, uploader = i?.uploader.orEmpty(),
                                    expectedBytes = i?.let { MediaFormats.estimateBytes(it, base) } ?: 0,
                                ),
                            )
                        )
                    }
                    onDismiss()
                },
                enabled = count > 0 && runtime.isAvailable,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (count > 1) stringResource(R.string.dl_m_add_n, count) else stringResource(R.string.dl_m_add_one))
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SwitchRow(checked: Boolean, onChange: (Boolean) -> Unit, label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(label), modifier = Modifier.weight(1f))
    }
}

/**
 * Import a cookies.txt (exported from a browser) for sites that need a login, take over the cookies the Telos
 * browser (the web view of the web apps) holds for [pageUrl], or delete them again.
 */
@Composable
internal fun MediaCookieControls(runtime: MediaRuntime, pageUrl: String?, onChanged: () -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().take(4 * 1024 * 1024).toByteArray().decodeToString() }
            }.getOrNull().orEmpty()
            val n = runtime.importCookies(text)
            Toast.makeText(context, if (n > 0) context.getString(R.string.dl_m_cookies_imported, n) else context.getString(R.string.dl_m_cookies_invalid), Toast.LENGTH_SHORT).show()
            onChanged()
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picker.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) }) { Text(stringResource(R.string.dl_m_cookies_import)) }
        if (pageUrl != null) {
            OutlinedButton(onClick = {
                val header = runCatching { android.webkit.CookieManager.getInstance().getCookie(pageUrl) }.getOrNull().orEmpty()
                val n = if (header.isBlank()) 0 else runtime.addCookieHeader(pageUrl, header)
                Toast.makeText(
                    context,
                    if (n > 0) context.getString(R.string.dl_m_cookies_browser_done, n) else context.getString(R.string.dl_m_cookies_browser_none),
                    Toast.LENGTH_LONG,
                ).show()
                onChanged()
            }) { Text(stringResource(R.string.dl_m_cookies_browser)) }
        }
        if (runtime.hasCookies) OutlinedButton(onClick = { runtime.clearCookies(); onChanged() }) { Text(stringResource(R.string.dl_m_cookies_clear)) }
    }
}
