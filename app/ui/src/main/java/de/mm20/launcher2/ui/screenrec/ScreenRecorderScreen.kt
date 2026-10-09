package de.mm20.launcher2.ui.screenrec

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.NavKey
import androidx.compose.runtime.saveable.rememberSaveable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.SearchEmptyState
import de.mm20.launcher2.ui.component.TelosSearchBar
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.text.DateFormat
import java.util.Date

@Serializable
data object ScreenRecorderRoute : NavKey

@Serializable
data object ScreenRecorderSettingsRoute : NavKey

private data class ScreenRecording(val uri: Uri, val name: String, val modified: Long, val durationMs: Long, val size: Long)

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun queryRecordings(context: Context): List<ScreenRecording> {
    val result = mutableListOf<ScreenRecording>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE,
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection,
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?",
            arrayOf(Environment.DIRECTORY_MOVIES + "/Telos%", "Screen_%"),
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                result += ScreenRecording(
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(0)),
                    c.getString(1) ?: continue, c.getLong(2) * 1000, c.getLong(3), c.getLong(4),
                )
            }
        }
    } else {
        File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Telos").listFiles()
            ?.filter { it.extension == "mp4" && it.length() > 0 }
            ?.sortedByDescending { it.lastModified() }
            ?.forEach { result += ScreenRecording(Uri.fromFile(it), it.name, it.lastModified(), 0L, it.length()) }
    }
    return result
}

/**
 * Telos Screen Recorder. The screen follows the Telos theme: a big red button to start, the state
 * of a running recording, and the list of your recordings.
 */
@Composable
fun ScreenRecorderScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val state by ScreenRecorderState.state.collectAsState()
    var reload by remember { mutableStateOf(0) }
    var deleting by remember { mutableStateOf<ScreenRecording?>(null) }
    val recordings by produceState(emptyList<ScreenRecording>(), reload, state.status) {
        value = withContext(Dispatchers.IO) { runCatching { queryRecordings(context) }.getOrDefault(emptyList()) }
    }
    val settings = remember { ScreenRecorderSettings(context) }
    var recQuery by rememberSaveable { mutableStateOf("") }
    val shownRecordings = remember(recordings, recQuery) {
        val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        de.mm20.launcher2.comms.search.TelosSearch.filter(recordings, recQuery) { listOf(it.name, df.format(java.util.Date(it.modified))) }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        ScreenRecorderRequestActivity.launch(context)
    }
    fun start() {
        val needsMic = settings.audio == ScreenAudio.Microphone &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        if (needsMic) micPermission.launch(Manifest.permission.RECORD_AUDIO) else ScreenRecorderRequestActivity.launch(context)
    }
    LaunchedEffect(state.error) {
        if (state.error) ScreenRecorderState.set(ScreenRecState())
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { backStack.add(ScreenRecorderSettingsRoute) }) {
                    Icon(painterResource(R.drawable.settings_24px), contentDescription = stringResource(R.string.voice_settings))
                }
            }
            Text(
                stringResource(R.string.screenrec_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (state.status) {
                    ScreenRecStatus.Idle -> {
                        Box(
                            Modifier
                                .size(88.dp)
                                .shadow(20.dp, CircleShape, ambientColor = Color(0xFFE53935), spotColor = Color(0xFFE53935))
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(Color(0xFFFF7A6E), Color(0xFFE53935))))
                                .clickable(onClickLabel = stringResource(R.string.screenrec_start), onClick = ::start),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_glyph_screen_recorder), contentDescription = stringResource(R.string.screenrec_start), tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                        Text(stringResource(R.string.screenrec_start), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    }
                    ScreenRecStatus.Countdown -> {
                        Text(state.countdown.toString(), fontSize = 72.sp, fontWeight = FontWeight.Light)
                        TextButton(onClick = { ScreenRecorderService.stop(context) }) { Text(stringResource(R.string.voice_cancel)) }
                    }
                    ScreenRecStatus.Recording, ScreenRecStatus.Paused -> {
                        Text(
                            stringResource(if (state.status == ScreenRecStatus.Paused) R.string.screenrec_paused else R.string.screenrec_recording),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(formatDuration(state.elapsedMs), fontSize = 56.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(vertical = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { ScreenRecorderService.togglePause(context) },
                                modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            ) {
                                Icon(
                                    painterResource(if (state.status == ScreenRecStatus.Paused) R.drawable.ic_voice_play else R.drawable.ic_voice_pause),
                                    contentDescription = stringResource(if (state.status == ScreenRecStatus.Paused) R.string.voice_resume else R.string.voice_pause),
                                )
                            }
                            Box(
                                Modifier.size(72.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFFFF7A6E), Color(0xFFE53935))))
                                    .clickable(onClickLabel = stringResource(R.string.voice_stop)) { ScreenRecorderService.stop(context) },
                                contentAlignment = Alignment.Center,
                            ) { Box(Modifier.size(24.dp).clip(RoundedCornerShape(5.dp)).background(Color.White)) }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.screenrec_recordings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (recordings.isEmpty()) {
                Text(stringResource(R.string.screenrec_no_recordings), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            } else {
                TelosSearchBar(recQuery, { recQuery = it }, stringResource(R.string.tsp_search_recordings))
                if (shownRecordings.isEmpty() && recQuery.isNotBlank()) SearchEmptyState(recQuery)
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(shownRecordings, key = { it.uri.toString() }) { rec ->
                        RecordingRow(rec, onDelete = { deleting = rec })
                    }
                }
            }
        }
    }

    deleting?.let { rec ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.voice_delete_title)) },
            text = { Text(rec.name) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        if (rec.uri.scheme == "file") File(rec.uri.path!!).delete() else context.contentResolver.delete(rec.uri, null, null)
                    }
                    deleting = null
                    reload++
                }) { Text(stringResource(R.string.voice_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.voice_cancel)) } },
        )
    }
}

@Composable
private fun RecordingRow(rec: ScreenRecording, onDelete: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).setDataAndType(rec.uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                }.onFailure { Toast.makeText(context, R.string.screenrec_cannot_open, Toast.LENGTH_SHORT).show() }
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(rec.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val duration = if (rec.durationMs > 0) formatDuration(rec.durationMs) + " · " else ""
            Text(
                duration + format.format(Date(rec.modified)) + " · " + android.text.format.Formatter.formatShortFileSize(context, rec.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(R.drawable.more_vert_24px), contentDescription = stringResource(R.string.voice_more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.voice_share)) }, onClick = {
                    menu = false
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, de.mm20.launcher2.ui.screenshot.ScreenshotController.shareableUri(context, rec.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                null,
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.voice_delete)) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** The settings of Telos Screen Recorder */
@Composable
fun ScreenRecorderSettingsScreen() {
    val context = LocalContext.current
    val settings = remember { ScreenRecorderSettings(context) }
    var resolution by remember { mutableStateOf(settings.resolution) }
    var fps by remember { mutableStateOf(settings.fps) }
    var quality by remember { mutableStateOf(settings.quality) }
    var audio by remember { mutableStateOf(settings.audio) }
    var touches by remember { mutableStateOf(settings.showTouches) }
    var countdown by remember { mutableStateOf(settings.countdown) }
    var screenOff by remember { mutableStateOf(settings.stopOnScreenOff) }

    PreferenceScreen(title = stringResource(R.string.voice_settings)) {
        item {
            PreferenceCategory(title = stringResource(R.string.screenrec_settings_video)) {
                ListPreference(
                    title = stringResource(R.string.screenrec_resolution),
                    items = listOf(
                        ListPreferenceItem(stringResource(R.string.screenrec_resolution_native), ScreenResolution.Native),
                        ListPreferenceItem("1080p", ScreenResolution.P1080),
                        ListPreferenceItem("720p", ScreenResolution.P720),
                        ListPreferenceItem("480p", ScreenResolution.P480),
                    ),
                    value = resolution,
                    onValueChanged = { resolution = it; settings.resolution = it },
                )
                ListPreference(
                    title = stringResource(R.string.screenrec_fps),
                    items = listOf(ListPreferenceItem("30 fps", 30), ListPreferenceItem("60 fps", 60)),
                    value = fps,
                    onValueChanged = { fps = it; settings.fps = it },
                )
                ListPreference(
                    title = stringResource(R.string.voice_quality),
                    items = listOf(
                        ListPreferenceItem(stringResource(R.string.voice_quality_compact), ScreenQuality.Low),
                        ListPreferenceItem(stringResource(R.string.voice_quality_balanced), ScreenQuality.Medium),
                        ListPreferenceItem(stringResource(R.string.voice_quality_high), ScreenQuality.High),
                    ),
                    value = quality,
                    onValueChanged = { quality = it; settings.quality = it },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.screenrec_settings_audio)) {
                SwitchPreference(
                    title = stringResource(R.string.screenrec_microphone),
                    summary = stringResource(R.string.screenrec_microphone_summary),
                    value = audio == ScreenAudio.Microphone,
                    onValueChanged = {
                        audio = if (it) ScreenAudio.Microphone else ScreenAudio.None
                        settings.audio = audio
                    },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.screenrec_settings_recording)) {
                SwitchPreference(
                    title = stringResource(R.string.screenrec_show_touches),
                    summary = stringResource(R.string.screenrec_show_touches_summary),
                    value = touches,
                    onValueChanged = {
                        touches = it
                        settings.showTouches = it
                        // changing the system setting needs its own permission
                        if (it && !Settings.System.canWrite(context)) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    },
                )
                ListPreference(
                    title = stringResource(R.string.screenrec_countdown_setting),
                    items = listOf(
                        ListPreferenceItem(stringResource(R.string.screenrec_countdown_none), 0),
                        ListPreferenceItem(stringResource(R.string.au_capture_seconds, 3), 3),
                        ListPreferenceItem(stringResource(R.string.au_capture_seconds, 5), 5),
                    ),
                    value = countdown,
                    onValueChanged = { countdown = it; settings.countdown = it },
                )
                SwitchPreference(
                    title = stringResource(R.string.screenrec_stop_screen_off),
                    summary = stringResource(R.string.screenrec_stop_screen_off_summary),
                    value = screenOff,
                    onValueChanged = { screenOff = it; settings.stopOnScreenOff = it },
                )
            }
        }
    }
}
