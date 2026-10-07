package de.mm20.launcher2.ui.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.recording.CallAudioRecorder
import de.mm20.launcher2.comms.recording.RecordingCrypto
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.comms.recording.RecordingQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

@Serializable
data object VoiceRecorderRoute : NavKey

@Serializable
data object VoiceSettingsRoute : NavKey

/** One row of the list: a recording of the voice recorder, or a call recording of Telos Phone */
private data class RecordingItem(
    val file: File,
    val title: String,
    val modified: Long,
    val durationMs: Long,
    val isCall: Boolean,
    val mime: String,
    val voice: VoiceRecording? = null,
)

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Plays one recording at a time, through the loudspeaker or the earpiece */
private class VoicePlayer {
    var current by mutableStateOf<File?>(null)
    var playing by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    private var player: MediaPlayer? = null

    fun play(file: File, speaker: Boolean) {
        stop()
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(if (speaker) AudioAttributes.CONTENT_TYPE_MUSIC else AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(if (speaker) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build()
            )
            p.setDataSource(file.absolutePath)
            p.prepare()
            p.setOnCompletionListener {
                playing = false
                positionMs = 0
                runCatching { it.seekTo(0) }
            }
            p.start()
            player = p
            current = file
            durationMs = p.duration.toLong()
            positionMs = 0
            playing = true
        } catch (e: Exception) {
            stop()
        }
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            playing = false
        } else {
            p.start()
            playing = true
        }
    }

    fun seekTo(ms: Long) {
        player?.seekTo(ms.toInt())
        positionMs = ms
    }

    fun refresh() {
        player?.let { positionMs = it.currentPosition.toLong() }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        current = null
        playing = false
        positionMs = 0
    }
}

/**
 * Telos Voice Recorder. The layout follows the Sound Recorder of OxygenOS (big title, search,
 * menu, filter chips, the list, a red record button) and the look follows the Telos theme.
 */
@Composable
fun VoiceRecorderScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val settings = remember { VoiceRecorderSettings(context) }
    val recorder by VoiceRecorderEngine.state.collectAsState()
    val player = remember { VoicePlayer() }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    var tab by rememberSaveable { mutableStateOf(0) } // 0 all, 1 call recordings
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(settings.speaker) }
    var reload by remember { mutableStateOf(0) }
    var renaming by remember { mutableStateOf<RecordingItem?>(null) }
    var deleting by remember { mutableStateOf<RecordingItem?>(null) }

    val items by produceItems(context, reload, recorder.status)
    val shown = remember(items, tab, query) {
        items.filter { (tab == 0 || it.isCall) && (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)) }
    }

    LaunchedEffect(player.playing) {
        while (player.playing) {
            player.refresh()
            delay(200)
        }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            player.stop()
            VoiceRecorderService.startRecording(context)
        } else {
            Toast.makeText(context, R.string.voice_permission_needed, Toast.LENGTH_LONG).show()
        }
    }
    fun startRecording() {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            player.stop()
            VoiceRecorderService.startRecording(context)
        } else {
            permissions.launch(needed.toTypedArray())
        }
    }
    LaunchedEffect(recorder.error) {
        if (recorder.error) {
            Toast.makeText(context, R.string.voice_start_failed, Toast.LENGTH_LONG).show()
            VoiceRecorderEngine.clearError()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding()) {
        if (recorder.status != VoiceStatus.Idle) {
            RecordingPage(
                state = recorder,
                onPauseResume = {
                    if (recorder.status == VoiceStatus.Paused) VoiceRecorderEngine.resume() else VoiceRecorderEngine.pause()
                },
                onStop = {
                    VoiceRecorderService.stopRecording(context)
                    reload++
                },
                onDiscard = {
                    VoiceRecorderEngine.cancel()
                    reload++
                },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (searching) {
                        Row(
                            Modifier.weight(1f).padding(start = 8.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) Text(stringResource(R.string.voice_search), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                BasicTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        IconButton(onClick = { searching = false; query = "" }) {
                            Icon(painterResource(R.drawable.close_24px), contentDescription = stringResource(R.string.close))
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { searching = true }) {
                            Icon(painterResource(R.drawable.search_24px), contentDescription = stringResource(R.string.voice_search))
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(painterResource(R.drawable.more_vert_24px), contentDescription = stringResource(R.string.voice_more))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (speaker) R.string.voice_receiver_mode else R.string.voice_speaker_mode)) },
                                    onClick = {
                                        menu = false
                                        speaker = !speaker
                                        settings.speaker = speaker
                                        player.stop()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.voice_settings)) },
                                    onClick = { menu = false; backStack.add(VoiceSettingsRoute) },
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.voice_title),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text(stringResource(R.string.voice_all_recordings)) })
                    FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text(stringResource(R.string.voice_call_recordings)) })
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (shown.isEmpty()) {
                        EmptyState()
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(shown, key = { it.file.absolutePath }) { item ->
                                RecordingRow(
                                    item = item,
                                    selected = player.current?.name == item.file.name,
                                    player = player,
                                    onClick = {
                                        if (player.current?.name == item.file.name) {
                                            player.toggle()
                                        } else {
                                            val readable = if (item.isCall) RecordingCrypto.readableCopy(context, item.file) else item.file
                                            if (readable != null) player.play(readable, speaker)
                                        }
                                    },
                                    onRename = { renaming = item },
                                    onShare = { shareRecording(context, item) },
                                    onDelete = { deleting = item },
                                )
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    RecordButton(onClick = ::startRecording)
                }
            }
        }
    }

    renaming?.let { item ->
        var name by remember(item) { mutableStateOf(item.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.voice_rename)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val voice = item.voice
                    if (voice != null && VoiceRecorderEngine.rename(voice, name) != null) {
                        player.stop()
                        reload++
                    } else {
                        Toast.makeText(context, R.string.voice_rename_failed, Toast.LENGTH_SHORT).show()
                    }
                    renaming = null
                }) { Text(stringResource(R.string.voice_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.voice_cancel)) } },
        )
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.voice_delete_title)) },
            text = { Text(item.title) },
            confirmButton = {
                TextButton(onClick = {
                    player.stop()
                    if (item.isCall) CallAudioRecorder.delete(item.file) else item.file.delete()
                    deleting = null
                    reload++
                }) { Text(stringResource(R.string.voice_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.voice_cancel)) } },
        )
    }
}

@Composable
private fun produceItems(context: Context, reload: Int, status: VoiceStatus) =
    androidx.compose.runtime.produceState(emptyList<RecordingItem>(), reload, status) {
        value = withContext(Dispatchers.IO) {
            val own = VoiceRecorderEngine.list(context).map {
                RecordingItem(it.file, it.name, it.modified, it.durationMs, false, it.format.mime, it)
            }
            val calls = runCatching { CallAudioRecorder.list(context) }.getOrDefault(emptyList()).map {
                RecordingItem(it.file, it.number, it.file.lastModified(), 0L, true, "audio/mp4")
            }
            (own + calls).sortedByDescending { it.modified }
        }
    }

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        // a microphone with a waveform, drawn here so that no picture is needed
        Box(Modifier.size(width = 180.dp, height = 120.dp), contentAlignment = Alignment.Center) {
            val bars = listOf(0.25f, 0.5f, 0.35f, 0.7f, 1f, 0.7f, 0.45f, 0.3f, 0.2f)
            val color = MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
            Canvas(Modifier.fillMaxSize()) {
                val barWidth = 6.dp.toPx()
                val gap = 4.dp.toPx()
                val total = bars.size * barWidth + (bars.size - 1) * gap
                var x = (size.width - total) / 2
                for (b in bars) {
                    val h = size.height * b
                    drawRoundRect(color, Offset(x, (size.height - h) / 2), Size(barWidth, h), CornerRadius(barWidth / 2))
                    x += barWidth + gap
                }
            }
            Icon(
                painterResource(R.drawable.ic_glyph_voice_recorder),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.size(56.dp),
            )
        }
        Text(stringResource(R.string.voice_no_recordings), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun RecordButton(onClick: () -> Unit) {
    val red = Color(0xFFE53935)
    Box(
        Modifier
            .size(72.dp)
            .shadow(20.dp, CircleShape, ambientColor = red, spotColor = red)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFFFF7A6E), red)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun RecordingRow(
    item: RecordingItem,
    selected: Boolean,
    player: VoicePlayer,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val duration = if (item.durationMs > 0) formatDuration(item.durationMs) + " · " else ""
                Text(
                    duration + format.format(Date(item.modified)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                IconButton(onClick = onClick) {
                    Icon(
                        painterResource(if (player.playing) R.drawable.ic_voice_pause else R.drawable.ic_voice_play),
                        contentDescription = stringResource(if (player.playing) R.string.voice_pause else R.string.voice_play),
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(R.drawable.more_vert_24px), contentDescription = stringResource(R.string.voice_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!item.isCall) DropdownMenuItem(text = { Text(stringResource(R.string.voice_rename)) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.voice_share)) }, onClick = { menu = false; onShare() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.voice_delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
        if (selected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(player.positionMs), style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = player.positionMs.toFloat(),
                    onValueChange = { player.seekTo(it.toLong()) },
                    valueRange = 0f..max(1f, player.durationMs.toFloat()),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(formatDuration(player.durationMs), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun shareRecording(context: Context, item: RecordingItem) {
    runCatching {
        val readable = if (item.isCall) RecordingCrypto.readableCopy(context, item.file)!! else item.file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", readable)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = item.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, R.string.voice_share_failed, Toast.LENGTH_SHORT).show() }
}

@Composable
private fun RecordingPage(state: VoiceRecorderState, onPauseResume: () -> Unit, onStop: () -> Unit, onDiscard: () -> Unit) {
    val paused = state.status == VoiceStatus.Paused
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(if (paused) R.string.voice_notification_paused else R.string.voice_notification_recording),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(formatDuration(state.elapsedMs), fontSize = 64.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(vertical = 16.dp))
        Waveform(state.amplitudes, Modifier.fillMaxWidth().height(120.dp))
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDiscard, modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Icon(painterResource(R.drawable.delete_24px), contentDescription = stringResource(R.string.voice_discard))
            }
            Box(
                Modifier.size(80.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFFFF7A6E), Color(0xFFE53935)))).clickable(onClick = onStop),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color.White))
            }
            IconButton(onClick = onPauseResume, modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Icon(
                    painterResource(if (paused) R.drawable.ic_voice_play else R.drawable.ic_voice_pause),
                    contentDescription = stringResource(if (paused) R.string.voice_resume else R.string.voice_pause),
                )
            }
        }
    }
}

@Composable
private fun Waveform(amplitudes: List<Int>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val barWidth = 5.dp.toPx()
        val gap = 3.dp.toPx()
        val count = (size.width / (barWidth + gap)).toInt().coerceAtLeast(1)
        val visible = amplitudes.takeLast(count)
        var x = size.width - visible.size * (barWidth + gap)
        for (amp in visible) {
            val h = (amp / 32768f).coerceIn(0.03f, 1f) * size.height
            drawRoundRect(color, Offset(x, (size.height - h) / 2), Size(barWidth, h), CornerRadius(barWidth / 2))
            x += barWidth + gap
        }
    }
}

/** The settings of Telos Voice Recorder, in the layout of the settings of the OxygenOS Sound Recorder */
@Composable
fun VoiceSettingsScreen() {
    val context = LocalContext.current
    val settings = remember { VoiceRecorderSettings(context) }
    var format by remember { mutableStateOf(settings.format) }
    var mode by remember { mutableStateOf(settings.mode) }
    var quality by remember { mutableStateOf(settings.quality) }
    var speaker by remember { mutableStateOf(settings.speaker) }

    PreferenceScreen(title = stringResource(R.string.voice_settings)) {
        item {
            PreferenceCategory(title = stringResource(R.string.voice_settings_sound)) {
                ListPreference(
                    title = stringResource(R.string.voice_format),
                    items = VoiceFormat.entries.filter { it.available }.map {
                        ListPreferenceItem(if (it == VoiceFormat.Aac) "AAC (.m4a)" else "Opus (.ogg)", it)
                    },
                    value = format,
                    onValueChanged = { format = it; settings.format = it },
                )
                ListPreference(
                    title = stringResource(R.string.voice_mode),
                    items = listOf(
                        ListPreferenceItem(stringResource(R.string.voice_mode_standard), VoiceMode.Standard),
                        ListPreferenceItem(stringResource(R.string.voice_mode_voice), VoiceMode.Voice),
                    ),
                    value = mode,
                    onValueChanged = { mode = it; settings.mode = it },
                )
                ListPreference(
                    title = stringResource(R.string.voice_quality),
                    items = listOf(
                        ListPreferenceItem(stringResource(R.string.voice_quality_compact), RecordingQuality.COMPACT),
                        ListPreferenceItem(stringResource(R.string.voice_quality_balanced), RecordingQuality.BALANCED),
                        ListPreferenceItem(stringResource(R.string.voice_quality_high), RecordingQuality.HIGH),
                    ),
                    value = quality,
                    onValueChanged = { quality = it; settings.quality = it },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.voice_settings_playback)) {
                SwitchPreference(
                    title = stringResource(R.string.voice_speaker),
                    summary = stringResource(R.string.voice_speaker_summary),
                    value = speaker,
                    onValueChanged = { speaker = it; settings.speaker = it },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.voice_settings_privacy)) {
                Text(
                    stringResource(R.string.voice_privacy_text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
