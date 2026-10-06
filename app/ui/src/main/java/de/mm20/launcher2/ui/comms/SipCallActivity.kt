package de.mm20.launcher2.ui.comms

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.comms.sip.SipAudio
import de.mm20.launcher2.comms.sip.SipCallState
import de.mm20.launcher2.comms.sip.SipEngine
import de.mm20.launcher2.comms.sip.SipUri
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.theme.LauncherTheme
import kotlinx.coroutines.delay

/** Call screen for SIP calls: answer or decline, mute, speaker, keypad and hang up. */
class SipCallActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    val call by SipEngine.call.collectAsStateWithLifecycle()
                    // the call is over: leave the screen
                    LaunchedEffect(call.state) {
                        if (call.state == SipCallState.None) {
                            delay(600)
                            if (SipEngine.call.value.state == SipCallState.None) finish()
                        }
                    }
                    SipCallScreen(call)
                }
            }
        }
    }
}

@Composable
private fun SipCallScreen(call: de.mm20.launcher2.comms.sip.SipCall) {
    val context = LocalContext.current
    val audio = remember { SipAudio(context) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(false) }
    var keypad by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }

    val number = SipUri.user(call.peer)
    val name = remember(number) { SipUri.displayName(context, number) }

    LaunchedEffect(call.state) {
        seconds = 0
        while (call.state == SipCallState.Established) {
            delay(1000)
            seconds++
        }
    }

    val status = when (call.state) {
        SipCallState.Incoming -> "Incoming SIP call"
        SipCallState.Outgoing -> "Calling…"
        SipCallState.Ringing -> "Ringing…"
        SipCallState.Established -> "%d:%02d".format(seconds / 60, seconds % 60)
        SipCallState.None -> "Call ended"
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Text("SIP", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                name,
                fontSize = 32.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (name != number) {
                Text(number, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(status, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))

            Spacer(Modifier.weight(1f))

            if (call.state == SipCallState.Established && keypad) {
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
                for (row in keys.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        for (key in row) {
                            FilledTonalButton(
                                onClick = { SipEngine.sendDigit(key[0]) },
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                            ) { Text(key, fontSize = 22.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (call.state == SipCallState.Established) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ToggleButton(R.drawable.mic_off_24px, "Mute", muted) {
                        muted = !muted
                        SipEngine.setMuted(muted)
                    }
                    ToggleButton(R.drawable.volume_up_24px, "Speaker", speaker) {
                        speaker = !speaker
                        audio.setSpeaker(speaker)
                    }
                    ToggleButton(R.drawable.dialpad_24px, "Keypad", keypad) { keypad = !keypad }
                }
                Spacer(Modifier.height(32.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(64.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundCallButton(Color(0xFFD32F2F), R.drawable.rd_ic_phone_down_red_vector, "Hang up") {
                    SipEngine.hangUp()
                }
                if (call.state == SipCallState.Incoming) {
                    RoundCallButton(RdCallGreen, R.drawable.rd_ic_phone_green_vector, "Answer") {
                        SipEngine.answer()
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleButton(icon: Int, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconToggleButton(
            checked = active,
            onCheckedChange = { onClick() },
            modifier = Modifier.size(60.dp),
        ) {
            Icon(painterResource(icon), contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun RoundCallButton(color: Color, icon: Int, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(painterResource(icon), contentDescription = label, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}
