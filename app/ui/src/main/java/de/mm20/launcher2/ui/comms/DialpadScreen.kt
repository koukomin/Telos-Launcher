package de.mm20.launcher2.ui.comms
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.comms.intent.MessengerIntentUtils
import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.comms.CommsSettingsRoute

private data class DialpadKeySpec(val digit: String, val latin: String, val greek: String, val cyrillic: String)

private val DIALPAD_KEYS = listOf(
    DialpadKeySpec("1", "", "", ""),
    DialpadKeySpec("2", "ABC", "ΑΒΓ", "АБВГ"),
    DialpadKeySpec("3", "DEF", "ΔΕΖ", "ДЕЖЗ"),
    DialpadKeySpec("4", "GHI", "ΗΘΙ", "ИЙКЛ"),
    DialpadKeySpec("5", "JKL", "ΚΛΜ", "МНОП"),
    DialpadKeySpec("6", "MNO", "ΝΞΟ", "РСТУ"),
    DialpadKeySpec("7", "PQRS", "ΠΡΣ", "ФХЦЧ"),
    DialpadKeySpec("8", "TUV", "ΤΥΦ", "ШЩЪЫ"),
    DialpadKeySpec("9", "WXYZ", "ΧΨΩ", "ЬЭЮЯ"),
    DialpadKeySpec("*", "", "", ""),
    DialpadKeySpec("0", "+", "+", "+"),
    DialpadKeySpec("#", "", "", ""),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialpadScreen(initialNumber: String = "") {
    val viewModel: DialpadViewModel = viewModel()
    val context = LocalContext.current
    LaunchedEffect(initialNumber) { viewModel.seedInput(initialNumber) }
    val haptic = LocalHapticFeedback.current
    val backStack = LocalBackStack.current

    val input by viewModel.input.collectAsStateWithLifecycle()
    val t9Results by viewModel.t9Results.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val speedDials by viewModel.speedDials.collectAsStateWithLifecycle()
    val alphabet by viewModel.t9Alphabet.collectAsStateWithLifecycle()
    val sounds by viewModel.dialpadSounds.collectAsStateWithLifecycle()
    val vibrate by viewModel.dialpadVibration.collectAsStateWithLifecycle()
    val hideLetters by viewModel.hideDialpadLetters.collectAsStateWithLifecycle()
    val vaultUnlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()
    val vaultAuthRequested by viewModel.vaultAuthRequested.collectAsStateWithLifecycle()
    val sims = remember { de.mm20.launcher2.comms.telephony.TelosDialer.callCapableSims(context) }
    val sipSettings: de.mm20.launcher2.preferences.comms.CommsSettings = org.koin.compose.koinInject()
    val sipEnabled by sipSettings.sipEnabled.collectAsStateWithLifecycle(false)
    val sipMode by sipSettings.sipOutgoing.collectAsStateWithLifecycle("choose")
    val sipRegistration by de.mm20.launcher2.comms.sip.SipEngine.registration.collectAsStateWithLifecycle()
    val sipOffered = sipEnabled && sipMode != "off" &&
        sipRegistration == de.mm20.launcher2.comms.sip.SipRegistration.Registered

    val vaultTitle = stringResource(I18nR.string.hc_hidden_contacts)
    LaunchedEffect(vaultUnlocked) {
        if (vaultUnlocked) {
            // consume the event: coming back to this screen must not open the hidden contacts again
            viewModel.onVaultNavigated()
            backStack.add(HiddenContactsRoute)
        }
    }
    LaunchedEffect(vaultAuthRequested) {
        if (!vaultAuthRequested) return@LaunchedEffect
        val activity = context as? androidx.fragment.app.FragmentActivity
        if (activity == null) {
            viewModel.onVaultAuthHandled()
            return@LaunchedEffect
        }
        val ok = de.mm20.launcher2.comms.AuthManager().authenticateNative(activity, vaultTitle)
        if (ok) viewModel.onVaultAuthSuccess() else viewModel.onVaultAuthHandled()
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val fontScale = LocalDensity.current.fontScale
        val isLandscape = maxWidth > maxHeight
        val availableHeight = if (isLandscape) (maxHeight - 64.dp) else (maxHeight * 0.55f)
        val calculatedSize = (availableHeight / 5.2f).coerceIn(36.dp, 72.dp)
        val keySize = if (isLandscape) minOf(calculatedSize, 52.dp) else minOf(78.dp, (maxWidth - 24.dp) / 3).coerceAtLeast(42.dp)
        
        val toneGenerator = remember {
            runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 80) }.getOrNull()
        }
        DisposableEffect(toneGenerator) {
            onDispose { toneGenerator?.release() }
        }

        fun playTone(digit: Char) {
            if (!sounds) return
            val tone = when (digit) {
                '0' -> ToneGenerator.TONE_DTMF_0
                '1' -> ToneGenerator.TONE_DTMF_1
                '2' -> ToneGenerator.TONE_DTMF_2
                '3' -> ToneGenerator.TONE_DTMF_3
                '4' -> ToneGenerator.TONE_DTMF_4
                '5' -> ToneGenerator.TONE_DTMF_5
                '6' -> ToneGenerator.TONE_DTMF_6
                '7' -> ToneGenerator.TONE_DTMF_7
                '8' -> ToneGenerator.TONE_DTMF_8
                '9' -> ToneGenerator.TONE_DTMF_9
                '*' -> ToneGenerator.TONE_DTMF_S
                '#' -> ToneGenerator.TONE_DTMF_P
                else -> return
            }
            toneGenerator?.startTone(tone, 150)
        }

        fun onDigit(digit: String, long: Boolean) {
            if (vibrate) haptic.performHapticFeedback(
                if (long) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
            )
            if (long) {
                if (digit == "0") {
                    playTone('0')
                    viewModel.onKeyPressed('+')
                } else if (digit == "1" && speedDials[1].isNullOrEmpty()) {
                    viewModel.dialVoicemail(context)
                } else if (digit.first().isDigit()) {
                    val number = speedDials[digit.toInt()]
                    if (number != null) viewModel.dial(context, number)
                    else Toast.makeText(context, context.getString(I18nR.string.hc_speed_dial_not_assigned), Toast.LENGTH_SHORT).show()
                }
            } else {
                playTone(digit.first())
                viewModel.onKeyPressed(digit.first())
            }
        }

        val resultsPanel: @Composable (Modifier) -> Unit = { modifier ->
            Box(modifier) {
                if (input.isBlank()) {
                    if (recents.isEmpty()) {
                        EmptyCommsTab(
                            title = stringResource(I18nR.string.recents_empty_title),
                            message = stringResource(I18nR.string.recents_empty_msg),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(recents, key = { it.id }) { call ->
                                RecentCallRow(
                                    call = call,
                                    onCall = { viewModel.dial(context, call.phoneNumber) },
                                    onSms = { context.tryStartActivity(MessengerIntentUtils.sms(call.phoneNumber)) },
                                    onDelete = { viewModel.deleteRecent(call) },
                                    onDetails = {
                                        backStack.add(ContactDetailsRoute(phoneNumber = call.phoneNumber))
                                    },
                                )
                            }
                        }
                    }
                } else {
                    if (t9Results.isEmpty()) {
                        Text(
                            text = stringResource(I18nR.string.hc_no_contacts_found),
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        )
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(t9Results, key = { it.id }) { contact ->
                            T9ContactRow(
                                contact = contact,
                                onCall = {
                                    contact.phoneNumbers.firstOrNull()?.let { viewModel.dial(context, it) }
                                },
                                onDetails = {
                                    viewModel.onClear()
                                    backStack.add(ContactDetailsRoute(contactId = contact.id))
                                },
                            )
                        }
                    }
                }
            }
        }

        val dialpadPanel: @Composable (Modifier) -> Unit = { modifier ->
            Column(
                modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                for (row in DIALPAD_KEYS.chunked(3)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        for (key in row) {
                            val letters = when (alphabet) {
                                "greek" -> key.greek
                                "cyrillic" -> key.cyrillic
                                else -> key.latin
                            }.ifEmpty { if (key.digit == "0") "+" else "" }
                            DialpadKey(
                                size = keySize,
                                digit = key.digit,
                                sublabel = if (hideLetters) "" else letters,
                                onClick = { onDigit(key.digit, long = false) },
                                onLongClick = { onDigit(key.digit, long = true) },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f))
                    if (sims.size >= 2) {
                        sims.take(2).forEach { sim ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                FloatingActionButton(
                                    onClick = {
                                        viewModel.dialSim(
                                            context,
                                            input.filter { it.isDigit() || it == '+' || it == '*' || it == '#' },
                                            sim.handle,
                                        )
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(56.dp),
                                    shape = CircleShape,
                                ) {
                                    Text(sim.label.take(4), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            FloatingActionButton(
                                onClick = { viewModel.dial(context) },
                                containerColor = RdCallGreen,
                                contentColor = Color.White,
                                modifier = Modifier.size(68.dp),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    painterResource(BaseR.drawable.rd_ic_phone_green_vector),
                                    contentDescription = stringResource(I18nR.string.search_action_call),
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                    if (sipOffered) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            FloatingActionButton(
                                onClick = {
                                    viewModel.dialSip(
                                        context,
                                        input.filter { it.isDigit() || it == '+' || it == '*' || it == '#' },
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                            ) {
                                Text("SIP", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .alpha(if (input.isNotEmpty()) 1f else 0f)
                                .combinedClickable(
                                    enabled = input.isNotEmpty(),
                                    role = androidx.compose.ui.semantics.Role.Button,
                                    onClick = {
                                        if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.onBackspace()
                                    },
                                    onLongClick = {
                                        if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.onClear()
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(BaseR.drawable.rd_ic_backspace),
                                contentDescription = stringResource(I18nR.string.au_phonea_backspace),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        val headerPanel: @Composable () -> Unit = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = input.ifEmpty { " " },
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = if (isLandscape) 28.sp else 36.sp),
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .horizontalScroll(rememberScrollState(), reverseScrolling = true)
                        .heightIn(min = 56.dp)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = {
                                val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                                    ?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                clip.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                                    .forEach { viewModel.onKeyPressed(it) }
                            },
                        ),
                )
                if (input.isNotBlank()) {
                    Text(
                        text = stringResource(I18nR.string.search_action_contact),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val intent = Intent(Intent.ACTION_INSERT).apply {
                                    type = ContactsContract.RawContacts.CONTENT_TYPE
                                    putExtra(ContactsContract.Intents.Insert.PHONE, input)
                                }
                                context.tryStartActivity(intent)
                            }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        if (isLandscape) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    headerPanel()
                    resultsPanel(Modifier.weight(1f).fillMaxWidth())
                }
                dialpadPanel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                headerPanel()
                resultsPanel(Modifier.weight(1f).fillMaxWidth())
                dialpadPanel(Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadKey(
    size: androidx.compose.ui.unit.Dp,
    digit: String,
    sublabel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val digitSp = (26f / fontScale.coerceAtLeast(1f)).coerceAtLeast(18f).sp
    val sublabelSp = (11f / fontScale.coerceAtLeast(1f)).coerceAtLeast(8f).sp

    Box(
        modifier = Modifier
            .width(size)
            .heightIn(min = size)
            .clip(CircleShape)
            .combinedClickable(
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                fontSize = digitSp,
                fontWeight = FontWeight.Normal,
                lineHeight = digitSp * 1.1f,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = sublabel.ifEmpty { " " },
                fontSize = sublabelSp,
                lineHeight = sublabelSp * 1.15f,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = sublabel.ifEmpty { " " },
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun T9ContactRow(
    contact: DialerContact,
    onCall: () -> Unit,
    onDetails: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetails)
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommsAvatar(
            name = contact.displayName,
            photoUri = contact.photoUri,
            size = 48.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = contact.phoneNumbers.firstOrNull().orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCall) {
            Icon(
                painterResource(BaseR.drawable.rd_ic_phone_green_vector),
                contentDescription = stringResource(I18nR.string.search_action_call),
                tint = RdGreenCall,
            )
        }
    }
}
