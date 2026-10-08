package de.mm20.launcher2.ui.comms

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.draggable
import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.mm20.launcher2.comms.recording.CallAudioRecorder
import de.mm20.launcher2.comms.recording.RecordingCoordinator
import de.mm20.launcher2.comms.recording.RecordingQuality
import de.mm20.launcher2.comms.telephony.TelosCallSession
import de.mm20.launcher2.preferences.comms.CommsSettings
import org.koin.compose.koinInject
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CallScreen(onFinished: () -> Unit) {
    val state by TelosCallSession.ui.collectAsStateWithLifecycle()
    val recording by CallAudioRecorder.isRecording.collectAsStateWithLifecycle()
    val commsSettings: CommsSettings = koinInject()
    val autoRecord by commsSettings.autoRecordCalls.collectAsStateWithLifecycle(false)
    val qualityKey by commsSettings.recordingQuality.collectAsStateWithLifecycle("BALANCED")
    val rejectSms by commsSettings.rejectSmsTemplate.collectAsStateWithLifecycle("I'll call you back")
    val notesEnabled by commsSettings.inCallNotes.collectAsStateWithLifecycle(true)
    val notesMap by commsSettings.callerNotes.collectAsStateWithLifecycle(emptyMap())
    val context = LocalContext.current
    val recordScope = rememberCoroutineScope()
    var showKeypad by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.number) {
        if (state.number.isEmpty()) return@LaunchedEffect
        val photo = withContext(Dispatchers.IO) {
            try {
                val uri = android.net.Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    android.net.Uri.encode(state.number),
                )
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.PHOTO_URI),
                    null,
                    null,
                    null,
                )?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (_: Exception) {
                null
            }
        }
        TelosCallSession.setPhoto(photo)
    }

    LaunchedEffect(state.active, autoRecord, qualityKey) {
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (state.active && autoRecord && hasMic && !CallAudioRecorder.isRecording.value) {
            RecordingCoordinator.start(context, state.number, RecordingQuality.fromKey(qualityKey))
        }
    }
    LaunchedEffect(state.hasCall) {
        if (!state.hasCall) {
            RecordingCoordinator.stop()
            onFinished()
        }
    }
    LaunchedEffect(state.connectedAtEpochMs) {
        while (true) {
            val current = TelosCallSession.ui.value
            if (!current.active || current.connectedAtEpochMs == null) {
                elapsed = 0L
                break
            }
            elapsed = (System.currentTimeMillis() - current.connectedAtEpochMs!!) / 1000
            delay(500)
        }
    }

    val answerStyle by commsSettings.answerStyle.collectAsStateWithLifecycle("buttons")
    val hiddenMap by commsSettings.hiddenNumbers.collectAsStateWithLifecycle(emptyMap())
    val maskHidden by commsSettings.maskHiddenIncoming.collectAsStateWithLifecycle(true)
    val hiddenIncoming = state.incoming && maskHidden &&
        de.mm20.launcher2.comms.privacy.HiddenContacts.matches(state.number, hiddenMap)
    val displayName = if (hiddenIncoming) "Private"
    else state.name ?: state.number.ifEmpty { "Unknown" }
    val status = when {
        state.incoming -> stringResource(R.string.comms_incoming_call)
        state.connecting -> stringResource(R.string.comms_calling)
        state.onHold -> stringResource(R.string.comms_on_hold)
        state.active -> formatElapsed(elapsed)
        else -> ""
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (!state.photoUri.isNullOrBlank()) {
            AsyncImage(
                model = state.photoUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(32.dp).alpha(0.35f),
                contentScale = ContentScale.Crop,
            )
        }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        CommsAvatar(name = displayName, photoUri = state.photoUri, size = 100.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!hiddenIncoming && !state.name.isNullOrBlank() && state.number.isNotBlank()) {
            Text(
                text = state.number,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )

        Spacer(Modifier.weight(1f))

        if (showKeypad && !state.incoming) {
            InCallKeypad(onDigit = { TelosCallSession.playDtmf(it) })
            Spacer(Modifier.height(16.dp))
        } else if (!state.incoming) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CallControl(
                    icon = if (state.muted) R.drawable.rd_ic_microphone_off_vector else R.drawable.mic_24px,
                    label = stringResource(R.string.comms_mute),
                    selected = state.muted,
                    onClick = { TelosCallSession.toggleMute() },
                )
                CallControl(
                    icon = R.drawable.dialpad_24px,
                    label = stringResource(R.string.comms_keypad),
                    selected = showKeypad,
                    onClick = { showKeypad = !showKeypad },
                )
                CallControl(
                    icon = if (state.speaker) R.drawable.volume_up_24px else R.drawable.volume_off_24px,
                    label = stringResource(R.string.comms_speaker),
                    selected = state.speaker,
                    onClick = { TelosCallSession.toggleSpeaker() },
                )
                CallControl(
                    icon = R.drawable.rd_ic_bluetooth_audio_vector,
                    label = "BT",
                    selected = state.bluetooth,
                    onClick = { TelosCallSession.cycleAudioRoute() },
                )
                CallControl(
                    icon = R.drawable.rd_ic_pause_vector,
                    label = stringResource(R.string.comms_hold),
                    selected = state.onHold,
                    onClick = { TelosCallSession.toggleHold() },
                )
                CallControl(
                    icon = if (recording) R.drawable.mic_24px else R.drawable.mic_off_24px,
                    label = if (recording) "REC" else "Record",
                    selected = recording,
                    onClick = {
                        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (!hasMic) return@CallControl
                        recordScope.launch {
                            if (recording) RecordingCoordinator.stop()
                            else RecordingCoordinator.start(
                                context,
                                state.number,
                                RecordingQuality.fromKey(qualityKey),
                            )
                        }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CallControl(
                    icon = R.drawable.rd_ic_add_call_vector,
                    label = stringResource(R.string.comms_add_call),
                    selected = false,
                    onClick = { TelosCallSession.addCall(context) },
                )
                CallControl(
                    icon = R.drawable.rd_ic_call_merge_vector,
                    label = stringResource(R.string.comms_merge),
                    selected = state.canMerge,
                    onClick = { TelosCallSession.merge() },
                )
                CallControl(
                    icon = R.drawable.rd_ic_call_swap_vector,
                    label = stringResource(R.string.comms_swap),
                    selected = state.canSwap,
                    onClick = { TelosCallSession.swap() },
                )
                if (notesEnabled) {
                    CallControl(
                        icon = R.drawable.rd_ic_note,
                        label = stringResource(R.string.hc_note),
                        selected = showNotes,
                        onClick = { showNotes = true },
                    )
                }
            }
            if (state.canSwap && state.secondNumber.isNotBlank()) {
                Text(
                    text = (state.secondName ?: state.secondNumber) +
                        if (state.conferenceCount > 1) " · ${state.conferenceCount}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        if (showNotes) {
            var draft by remember(state.number) {
                mutableStateOf(notesMap[state.number].orEmpty())
            }
            AlertDialog(
                onDismissRequest = { showNotes = false },
                title = { Text(stringResource(R.string.hc_call_note)) },
                text = {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        commsSettings.setCallerNote(state.number, draft)
                        showNotes = false
                    }) { Text(stringResource(R.string.hc_save)) }
                },
                dismissButton = {
                    TextButton(onClick = { showNotes = false }) { Text(stringResource(R.string.hc_close)) }
                },
            )
        }

        if (state.incoming) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            var remindOpen by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(
                    text = stringResource(R.string.hc_remind_me),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { remindOpen = true }.padding(8.dp),
                )
            }
            if (remindOpen) {
                AlertDialog(
                    onDismissRequest = { remindOpen = false },
                    title = { Text(stringResource(R.string.hc_remind_me_to_call_back)) },
                    text = {
                        Column {
                            listOf(5, 15, 30, 60).forEach { minutes ->
                                TextButton(onClick = {
                                    de.mm20.launcher2.comms.reminder.CallbackReminder.schedule(
                                        context, state.number, state.name, minutes,
                                    )
                                    remindOpen = false
                                    TelosCallSession.reject()
                                }) { Text(stringResource(R.string.hc_in_minutes, minutes)) }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { remindOpen = false }) { Text(stringResource(R.string.hc_cancel)) }
                    },
                )
            }
            if (rejectSms.isNotBlank()) {
                Text(
                    text = stringResource(R.string.hc_reject_sms),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            val number = state.number
                            TelosCallSession.reject()
                            de.mm20.launcher2.comms.sms.QuickSms.send(context, number, rejectSms)
                        }
                        .padding(8.dp),
                )
            }
            if (answerStyle == "swipe") {
                SwipeAnswer(
                    onAnswer = { TelosCallSession.answer() },
                    onReject = { TelosCallSession.reject() },
                )
            } else Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = { TelosCallSession.reject() },
                        containerColor = RdRedCall,
                        contentColor = Color.White,
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                    ) {
                        Icon(painterResource(R.drawable.rd_ic_call_end), contentDescription = stringResource(R.string.comms_reject))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.comms_reject), style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = { TelosCallSession.answer() },
                        containerColor = RdCallGreen,
                        contentColor = Color.White,
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                    ) {
                        Icon(painterResource(R.drawable.rd_ic_call_accept), contentDescription = stringResource(R.string.comms_answer))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.comms_answer), style = MaterialTheme.typography.bodyMedium)
                }
            }
            }
        } else {
            FloatingActionButton(
                onClick = { TelosCallSession.hangup() },
                containerColor = RdRedCall,
                contentColor = Color.White,
                modifier = Modifier
                    .size(76.dp)
                    .padding(bottom = 8.dp),
                shape = CircleShape,
            ) {
                Icon(painterResource(R.drawable.rd_ic_call_end), contentDescription = stringResource(R.string.comms_hangup))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    }
}

@Composable
private fun CallControl(icon: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .size(56.dp)
                .clickable(onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(icon),
                    contentDescription = label,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InCallKeypad(onDigit: (Char) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
    Column {
        keys.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { key ->
                    Text(
                        text = key,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .padding(12.dp)
                            .clickable { onDigit(key.first()) },
                    )
                }
            }
        }
    }
}

private fun formatElapsed(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

@Composable
private fun SwipeAnswer(onAnswer: () -> Unit, onReject: () -> Unit) {
    val thumb = 64.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    var offsetX by remember { mutableStateOf(0f) }
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .height(72.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        val maxPx = with(density) { ((maxWidth - thumb) / 2).toPx() }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("<", color = RdRedCall, style = MaterialTheme.typography.headlineSmall)
            Text(">", color = RdCallGreen, style = MaterialTheme.typography.headlineSmall)
        }
        Box(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(Color.White)
                .draggable(
                    orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                    state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                        offsetX = (offsetX + delta).coerceIn(-maxPx, maxPx)
                    },
                    onDragStopped = {
                        when {
                            offsetX > maxPx * 0.7f -> onAnswer()
                            offsetX < -maxPx * 0.7f -> onReject()
                        }
                        offsetX = 0f
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.rd_ic_call_accept),
                contentDescription = null,
                tint = RdCallGreen,
            )
        }
    }
}
