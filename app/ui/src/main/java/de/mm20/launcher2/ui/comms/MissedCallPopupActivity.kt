package de.mm20.launcher2.ui.comms

import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.intent.MessengerIntentUtils
import de.mm20.launcher2.comms.overlay.CallOverlayIntents
import de.mm20.launcher2.comms.sms.QuickSms
import de.mm20.launcher2.comms.telephony.SimRouter
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.theme.LauncherTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class MissedCallPopupActivity : BaseActivity() {
    private val commsSettings: CommsSettings by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra(CallOverlayIntents.EXTRA_NAME)
        val number = intent.getStringExtra(CallOverlayIntents.EXTRA_NUMBER).orEmpty()
        val ringMs = intent.getLongExtra(CallOverlayIntents.EXTRA_RING_MS, 0L)
        val template = runBlocking { commsSettings.rejectSmsTemplate.first() }
        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    Surface(tonalElevation = 6.dp) {
                        Column(Modifier.padding(20.dp).fillMaxWidth()) {
                            Text(
                                text = name?.ifBlank { null } ?: number.ifBlank { "Unknown" },
                                style = MaterialTheme.typography.titleLarge,
                            )
                            if (ringMs > 0) {
                                Text(
                                    text = stringResource(R.string.hc_rang_for_seconds, (ringMs / 1000).toInt()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!name.isNullOrBlank() && number.isNotBlank()) {
                                Text(number, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TextButton(onClick = { finish() }) { Text(stringResource(R.string.hc_dismiss)) }
                                if (template.isNotBlank() && number.isNotBlank()) {
                                    TextButton(onClick = {
                                        QuickSms.send(this@MissedCallPopupActivity, number, template)
                                        finish()
                                    }) { Text(stringResource(R.string.hc_message)) }
                                }
                                FilledTonalButton(onClick = {
                                    if (number.isNotBlank()) SimRouter.place(this@MissedCallPopupActivity, number)
                                    finish()
                                }) { Text(stringResource(R.string.hc_call)) }
                            }
                            if (number.isNotBlank()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        tryStartActivity(MessengerIntentUtils.whatsApp(number))
                                    }) { Text("WhatsApp") }
                                    TextButton(onClick = {
                                        tryStartActivity(MessengerIntentUtils.telegram(number))
                                    }) { Text("Telegram") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
