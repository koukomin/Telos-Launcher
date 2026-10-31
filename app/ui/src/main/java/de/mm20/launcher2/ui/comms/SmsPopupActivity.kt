package de.mm20.launcher2.ui.comms
import de.mm20.launcher2.i18n.R as I18nR

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.sms.SmsThreads
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.theme.LauncherTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsPopupActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        val address = intent.getStringExtra("address").orEmpty()
        val title = intent.getStringExtra("title").orEmpty()
        val text = intent.getStringExtra("text").orEmpty()
        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    var reply by remember { mutableStateOf("") }
                    Surface(tonalElevation = 6.dp) {
                        Column(Modifier.padding(20.dp).fillMaxWidth()) {
                            Text(title, style = MaterialTheme.typography.titleLarge)
                            if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                            OutlinedTextField(reply, { reply = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                            TextButton(
                                enabled = reply.isNotBlank(),
                                onClick = {
                                    val body = reply
                                    CoroutineScope(Dispatchers.IO).launch { SmsThreads.send(this@SmsPopupActivity, address, body) }
                                    finish()
                                },
                            ) { Text(androidx.compose.ui.res.stringResource(I18nR.string.hc_send)) }
                        }
                    }
                }
            }
        }
    }
}
