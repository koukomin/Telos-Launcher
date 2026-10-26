package de.mm20.launcher2.ui.comms

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.draggable
import android.Manifest
import android.widget.Toast
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CallScreen(onFinished: () -> Unit,
    onTerminationBounds: (androidx.compose.ui.geometry.Rect?) -> Unit = {},
    stateSource: kotlinx.coroutines.flow.StateFlow<de.mm20.launcher2.comms.telephony.InCallUiState> = TelosCallSession.ui,
    onHangup: () -> Unit = { TelosCallSession.hangup() },
) {
    val state by stateSource.collectAsStateWithLifecycle()
    DisposableEffect(state.incoming) { onDispose { onTerminationBounds(null) } }
    val recording by CallAudioRecorder.isRecording.collectAsStateWithLifecycle()
    val commsSettings: CommsSettings = koinInject()
    val autoRecord by commsSettings.autoRecordCalls.collectAsStateWithLifecycle(false)
    val autoRecordMode by commsSettings.autoRecordMode.collectAsStateWithLifecycle("ALL_EXCEPT_EXCEPTIONS")
    val prohibitedNumbers by commsSettings.recordProhibitedNumbers.collectAsStateWithLifecycle(emptySet())
    val mandatoryNumbers by commsSettings.recordMandatoryNumbers.collectAsStateWithLifecycle(emptySet())

    fun shouldRecordCall(number: String): Boolean {
        val clean = number.filter { it.isDigit() || it == '+' }
        if (clean.isNotEmpty() && clean in prohibitedNumbers) return false
        if (clean.isNotEmpty() && clean in mandatoryNumbers) return true
        return when (autoRecordMode) {
            "ALL_EXCEPT_EXCEPTIONS" -> autoRecord
            "EXCEPTIONS_ONLY" -> false
            else -> false
        }
    }
    val qualityKey by commsSettings.recordingQuality.collectAsStateWithLifecycle("BALANCED")
    val rejectSms by commsSettings.rejectSmsTemplate.collectAsStateWithLifecycle("")
    val notesEnabled by commsSettings.inCallNotes.collectAsStateWithLifecycle(true)
    val notesMap by commsSettings.callerNotes.collectAsStateWithLifecycle(emptyMap())
    val context = LocalContext.current
    val recordScope = rememberCoroutineScope()
    var showKeypad by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.active, autoRecord, qualityKey) {
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (state.active && shouldRecordCall(state.number) && hasMic && !CallAudioRecorder.isRecording.value) {
            RecordingCoordinator.start(context, state.number, RecordingQuality.fromKey(qualityKey))
        }
    }
    LaunchedEffect(state.hasCall) {
        if (!state.hasCall) {
            RecordingCoordinator.stop()
            onFinished()
        }
    }
    LaunchedEffect(state.connectedAtEpochMs, state.active) {
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

    val fullScreenPhoto by commsSettings.fullScreenCallerPhoto.collectAsStateWithLifecycle(false)
    val answerStyle by commsSettings.answerStyle.collectAsStateWithLifecycle("buttons")
    val hiddenMap by commsSettings.hiddenNumbers.collectAsStateWithLifecycle(emptyMap())
    val maskHidden by commsSettings.maskHiddenIncoming.collectAsStateWithLifecycle(true)
    val hiddenIncoming = state.incoming && maskHidden &&
        de.mm20.launcher2.comms.privacy.HiddenContacts.matches(state.number, hiddenMap)
    val privateLabel = stringResource(R.string.au_phoneb_private_caller)
    val unknownLabel = stringResource(R.string.widget_name_unknown)
    val displayName = if (hiddenIncoming) privateLabel
    else if (state.callerNameSuggested && state.name != null) stringResource(R.string.caller_suggested_name, state.name!!)
    else state.name ?: state.number.ifEmpty { unknownLabel }
    val status = when {
        state.incoming -> stringResource(R.string.comms_incoming_call)
        state.connecting -> stringResource(R.string.comms_calling)
        state.onHold -> stringResource(R.string.comms_on_hold)
        state.active -> formatElapsed(elapsed)
        else -> ""
    }

    val photo = state.photoUri.takeUnless { hiddenIncoming }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (!photo.isNullOrBlank()) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                modifier = if (state.incoming && fullScreenPhoto) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxSize().blur(32.dp).alpha(0.35f)
                },
                contentScale = ContentScale.Crop,
            )
        }
        if (state.incoming) {
            IncomingCallLayout(
                displayName = displayName,
                number = if (!hiddenIncoming && !state.name.isNullOrBlank() && state.number.isNotBlank()) state.number else null,
                status = status,
                photoUri = photo.takeIf { fullScreenPhoto },
                likelySpam = state.likelySpam && !hiddenIncoming,
                rejectSms = rejectSms,
                answerStyle = answerStyle,
                onTerminationBounds = onTerminationBounds,
            )
        } else {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth > maxHeight || maxHeight < 620.dp * LocalDensity.current.fontScale
        val avatarSize = minOf(maxWidth * 0.62f, maxHeight * 0.32f, 240.dp).coerceAtLeast(96.dp)
        Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(if (compact) 12.dp else 24.dp)
            ,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(if (compact) 8.dp else 48.dp))
            CommsAvatar(name = displayName, photoUri = state.photoUri, size = avatarSize)
            Spacer(Modifier.height(if (compact) 8.dp else 20.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
            fontWeight = FontWeight.SemiBold,
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

        Spacer(Modifier.height(12.dp))

        if (showKeypad && !state.incoming) {
            InCallKeypad(onDigit = { TelosCallSession.playDtmf(it) })
            // the controls are hidden while the keypad is open: this is the way back to them
            TextButton(onClick = { showKeypad = false }) { Text(stringResource(R.string.hc_close)) }
            Spacer(Modifier.height(8.dp))
        } else if (!state.incoming) {
            val muteLabel = stringResource(R.string.comms_mute)
            val keypadLabel = stringResource(R.string.comms_keypad)
            val speakerLabel = stringResource(R.string.comms_speaker)
            val bluetoothLabel = stringResource(R.string.au_phoneb_bluetooth)
            val holdLabel = stringResource(R.string.comms_hold)
            val recordLabel = stringResource(
                if (recording) R.string.au_phoneb_recording_short else R.string.au_phoneb_record
            )
            val addLabel = stringResource(R.string.comms_add_call)
            val mergeLabel = stringResource(R.string.comms_merge)
            val swapLabel = stringResource(R.string.comms_swap)
            val noteLabel = stringResource(R.string.hc_note)
            val micNeeded = stringResource(R.string.au_phoneb_mic_needed)
            val recordFailed = stringResource(R.string.au_phoneb_record_failed)
            val controls = buildList {
                add(
                    CallControlSpec(
                        icon = if (state.muted) R.drawable.rd_ic_microphone_off_vector else R.drawable.mic_24px,
                        label = muteLabel,
                        selected = state.muted,
                        onClick = { TelosCallSession.toggleMute() },
                    )
                )
                add(
                    CallControlSpec(
                        icon = R.drawable.dialpad_24px,
                        label = keypadLabel,
                        selected = showKeypad,
                        onClick = { showKeypad = !showKeypad },
                    )
                )
                add(
                    CallControlSpec(
                        icon = if (state.speaker) R.drawable.volume_up_24px else R.drawable.volume_off_24px,
                        label = speakerLabel,
                        selected = state.speaker,
                        onClick = { TelosCallSession.toggleSpeaker() },
                    )
                )
                add(
                    CallControlSpec(
                        icon = R.drawable.rd_ic_bluetooth_audio_vector,
                        label = bluetoothLabel,
                        selected = state.bluetooth,
                        onClick = { TelosCallSession.cycleAudioRoute() },
                    )
                )
                add(
                    CallControlSpec(
                        icon = R.drawable.rd_ic_pause_vector,
                        label = holdLabel,
                        selected = state.onHold,
                        onClick = { TelosCallSession.toggleHold() },
                    )
                )
                add(
                    CallControlSpec(
                        icon = if (recording) R.drawable.mic_24px else R.drawable.mic_off_24px,
                        label = recordLabel,
                        selected = recording,
                        onClick = {
                            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (!hasMic) {
                                Toast.makeText(context, micNeeded, Toast.LENGTH_SHORT).show()
                            } else {
                                recordScope.launch {
                                    if (recording) RecordingCoordinator.stop()
                                    else if (!RecordingCoordinator.start(
                                            context,
                                            state.number,
                                            RecordingQuality.fromKey(qualityKey),
                                        )
                                    ) {
                                        Toast.makeText(context, recordFailed, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                    )
                )
                add(
                    CallControlSpec(
                        icon = R.drawable.rd_ic_add_call_vector,
                        label = addLabel,
                        selected = false,
                        onClick = { TelosCallSession.addCall(context) },
                    )
                )
                add(
                    CallControlSpec(
                        icon = R.drawable.rd_ic_call_merge_vector,
                        label = mergeLabel,
                        selected = state.canMerge,
                        onClick = { TelosCallSession.merge() },
                    )
                )
                add(
                    CallControlSpec(
                        icon = R.drawable.rd_ic_call_swap_vector,
                        label = swapLabel,
                        selected = state.canSwap,
                        onClick = { TelosCallSession.swap() },
                    )
                )
                if (notesEnabled) {
                    add(
                        CallControlSpec(
                            icon = R.drawable.rd_ic_note,
                            label = noteLabel,
                            selected = showNotes,
                            onClick = { showNotes = true },
                        )
                    )
                }
            }
            // four per row: six 56 dp buttons in one row do not fit on a 360 dp wide screen
            controls.chunked(4).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    rowItems.forEach { c ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            CallControl(c.icon, c.label, c.selected, c.onClick)
                        }
                    }
                    repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
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

        } // scroll content; disconnect remains visible
            FloatingActionButton(
                onClick = onHangup,
                containerColor = RdRedCall,
                contentColor = Color.White,
                modifier = Modifier
                    .size(76.dp)
                    .onGloballyPositioned { onTerminationBounds(it.boundsInWindow()) }
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
}

@Composable
private fun IncomingCallLayout(
    displayName: String,
    number: String?,
    status: String,
    photoUri: String?,
    likelySpam: Boolean,
    rejectSms: String,
    answerStyle: String,
    onTerminationBounds: (androidx.compose.ui.geometry.Rect?) -> Unit,
) {
    val context = LocalContext.current
    val overlay = Color.Black.copy(alpha = 0.45f)
    var sheetExtraPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val peekPx = with(density) { 56.dp.toPx() }
    val extraMaxPx = with(density) { 184.dp.toPx() }
    var remindOpen by remember { mutableStateOf(false) }
    val onWhite = Color.White
    val onWhiteMuted = Color.White.copy(alpha = 0.78f)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.35f to Color.Transparent,
                        0.65f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f),
                    )
                ),
        )
        if (photoUri.isNullOrBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CommsAvatar(name = displayName, photoUri = null, size = 160.dp)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = onWhite,
            )
            if (number != null) {
                Spacer(Modifier.height(6.dp))
                Text(text = number, style = MaterialTheme.typography.bodyLarge, color = onWhiteMuted)
            }
            Spacer(Modifier.height(8.dp))
            if (likelySpam) Text(stringResource(R.string.caller_likely_spam), color = MaterialTheme.colorScheme.errorContainer)
            Text(text = status, style = MaterialTheme.typography.titleMedium, color = onWhiteMuted)
            Spacer(Modifier.height(12.dp))

            val sheetHeight = with(density) { (peekPx + sheetExtraPx).toDp() }
            val expanded = sheetExtraPx > extraMaxPx * 0.4f
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .draggable(
                        orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                        state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                            sheetExtraPx = (sheetExtraPx - delta).coerceIn(0f, extraMaxPx)
                        },
                        onDragStopped = {
                            sheetExtraPx = if (sheetExtraPx > extraMaxPx * 0.4f) extraMaxPx else 0f
                        },
                    )
                    .clickable {
                        sheetExtraPx = if (sheetExtraPx > extraMaxPx * 0.4f) 0f else extraMaxPx
                    },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = overlay,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.45f)),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.keyboard_arrow_up_24px),
                            contentDescription = null,
                            tint = onWhiteMuted,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            stringResource(R.string.comms_swipe_up_actions),
                            style = MaterialTheme.typography.labelLarge,
                            color = onWhiteMuted,
                        )
                    }
                    if (expanded) {
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { remindOpen = true }) {
                            Text(stringResource(R.string.hc_remind_me), color = onWhite)
                        }
                        if (rejectSms.isNotBlank()) {
                            TextButton(onClick = {
                                val n = TelosCallSession.ui.value.number
                                TelosCallSession.reject()
                                de.mm20.launcher2.comms.sms.QuickSms.send(context, n, rejectSms)
                            }) {
                                Text(stringResource(R.string.hc_reject_sms), color = onWhite)
                            }
                        }
                    }
                }
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
                                        context,
                                        TelosCallSession.ui.value.number,
                                        TelosCallSession.ui.value.name,
                                        minutes,
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

            }
            Spacer(Modifier.height(8.dp))
            if (answerStyle == "swipe") {
                SwipeAnswer(
                    onAnswer = { TelosCallSession.answer() },
                    onReject = { TelosCallSession.reject() },
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FloatingActionButton(
                            onClick = { TelosCallSession.reject() },
                            containerColor = RdRedCall,
                            contentColor = Color.White,
                            modifier = Modifier.size(72.dp).onGloballyPositioned { onTerminationBounds(it.boundsInWindow()) },
                            shape = CircleShape,
                        ) {
                            Icon(
                                painterResource(R.drawable.rd_ic_call_end),
                                contentDescription = stringResource(R.string.comms_reject),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.comms_reject),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onWhite,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FloatingActionButton(
                            onClick = { TelosCallSession.answer() },
                            containerColor = RdCallGreen,
                            contentColor = Color.White,
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                        ) {
                            Icon(
                                painterResource(R.drawable.rd_ic_call_accept),
                                contentDescription = stringResource(R.string.comms_answer),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.comms_answer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onWhite,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private class CallControlSpec(
    val icon: Int,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

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
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .clickable { onDigit(key.first()) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = key, style = MaterialTheme.typography.headlineSmall)
                    }
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
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
                .background(MaterialTheme.colorScheme.onSurface)
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
