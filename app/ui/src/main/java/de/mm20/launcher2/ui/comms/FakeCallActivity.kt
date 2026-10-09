package de.mm20.launcher2.ui.comms

import android.app.NotificationManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.fakecall.FakeCallScheduler
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.theme.LauncherTheme
import kotlinx.coroutines.delay

class FakeCallActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        val name = intent.getStringExtra(FakeCallScheduler.EXTRA_NAME) ?: getString(R.string.comms_incoming_call)
        val number = intent.getStringExtra(FakeCallScheduler.EXTRA_NUMBER).orEmpty()
        enableEdgeToEdge()
        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    FakeCallScreen(
                        name = name,
                        number = number,
                        onFinished = {
                            getSystemService(NotificationManager::class.java)?.cancel(9999)
                            finish()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FakeCallScreen(name: String, number: String, onFinished: () -> Unit) {
    var ringing by remember { mutableStateOf(true) }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(ringing) {
        if (!ringing) {
            while (true) {
                delay(1000)
                elapsed++
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        CommsAvatar(name = name, photoUri = null, size = 120.dp)
        Spacer(Modifier.height(20.dp))
        Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (number.isNotBlank()) {
            Text(number, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = if (ringing) stringResource(R.string.comms_incoming_call) else "%d:%02d".format(elapsed / 60, elapsed % 60),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        if (ringing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FloatingActionButton(
                    onClick = onFinished,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                ) {
                    Icon(painterResource(R.drawable.rd_ic_call_end), contentDescription = stringResource(R.string.comms_reject))
                }
                FloatingActionButton(
                    onClick = { ringing = false },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                ) {
                    Icon(painterResource(R.drawable.rd_ic_call_accept), contentDescription = stringResource(R.string.comms_answer))
                }
            }
        } else {
            FloatingActionButton(
                onClick = onFinished,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(76.dp),
                shape = CircleShape,
            ) {
                Icon(painterResource(R.drawable.rd_ic_call_end), contentDescription = stringResource(R.string.comms_hangup))
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
