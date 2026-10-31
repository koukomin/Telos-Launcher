package de.mm20.launcher2.ui.comms
import de.mm20.launcher2.i18n.R as I18nR

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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MissedCallPopupActivity : BaseActivity() {
    private val commsSettings: CommsSettings by inject()

    private var callIntent by androidx.compose.runtime.mutableStateOf<android.content.Intent?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        callIntent = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callIntent = intent
        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    val source = callIntent ?: intent
                    val name = source.getStringExtra(CallOverlayIntents.EXTRA_NAME)
                    val number = source.getStringExtra(CallOverlayIntents.EXTRA_NUMBER).orEmpty()
                    val ringMs = source.getLongExtra(CallOverlayIntents.EXTRA_RING_MS, 0L)
                    val identity by androidx.compose.runtime.produceState(number to name, number, name) {
                        val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            de.mm20.launcher2.comms.remote.DeviceContactsResolver.lookup(applicationContext, number).first
                        }?.takeIf { it.isNotBlank() } ?: name
                        value = number to found
                    }
                    val resolvedName = if (identity.first == number) identity.second else name
                    val template by commsSettings.rejectSmsTemplate.collectAsStateWithLifecycle("")
                    Surface(tonalElevation = 6.dp) {
                        Column(Modifier.padding(20.dp).fillMaxWidth()) {
                            Text(
                                text = resolvedName?.ifBlank { null } ?: number.ifBlank { stringResource(I18nR.string.widget_name_unknown) },
                                style = MaterialTheme.typography.titleLarge,
                            )
                            if (ringMs > 0) {
                                Text(
                                    text = stringResource(I18nR.string.hc_rang_for_seconds, (ringMs / 1000).toInt()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!resolvedName.isNullOrBlank() && number.isNotBlank()) {
                                Text(number, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TextButton(onClick = { finish() }) { Text(stringResource(I18nR.string.hc_dismiss)) }
                                if (template.isNotBlank() && number.isNotBlank()) {
                                    TextButton(onClick = {
                                        QuickSms.send(this@MissedCallPopupActivity, number, template)
                                        finish()
                                    }) { Text(stringResource(I18nR.string.hc_message)) }
                                }
                                FilledTonalButton(onClick = {
                                    // the popup stays open until the call is placed, so the dial screen may still start
                                    lifecycleScope.launch {
                                        if (number.isNotBlank()) SimRouter.placeAwait(this@MissedCallPopupActivity, number)
                                        finish()
                                    }
                                }) { Text(stringResource(I18nR.string.hc_call)) }
                            }
                            if (number.isNotBlank()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        tryStartActivity(MessengerIntentUtils.whatsApp(number))
                                    }) { Text(stringResource(I18nR.string.action_whatsapp)) }
                                    TextButton(onClick = {
                                        tryStartActivity(MessengerIntentUtils.telegram(number))
                                    }) { Text(stringResource(I18nR.string.action_telegram)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
