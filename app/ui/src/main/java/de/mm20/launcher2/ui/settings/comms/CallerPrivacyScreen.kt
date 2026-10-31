package de.mm20.launcher2.ui.settings.comms
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data object CallerPrivacyRoute : NavKey

@Composable
fun CallerPrivacyScreen() {
    val settings: CommsSettings = koinInject()
    val personal by remember(settings) { settings.enableSpamBlocking }.collectAsStateWithLifecycle(true)
    val system by remember(settings) { settings.systemSpamBlocking }.collectAsStateWithLifecycle(true)
    val online by remember(settings) { settings.onlineCallerLookup }.collectAsStateWithLifecycle(false)
    val context = LocalContext.current
    var number by rememberSaveable { mutableStateOf("") }
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure {
            android.widget.Toast.makeText(context, I18nR.string.hc_failed_try_again, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    PreferenceScreen(title = stringResource(I18nR.string.caller_privacy_title)) {
        item { Column {
        SwitchPreference(title = stringResource(I18nR.string.hc_offline_spam_list),
            summary = stringResource(I18nR.string.hc_block_numbers_you_have_added_to_the_telo),
            value = personal, onValueChanged = settings::setEnableSpamBlocking)
        SwitchPreference(title = stringResource(I18nR.string.caller_system_list),
            value = system, onValueChanged = settings::setSystemSpamBlocking)
        SwitchPreference(title = stringResource(I18nR.string.caller_online_lookup),
            summary = stringResource(I18nR.string.caller_online_disclosure),
            value = online, onValueChanged = settings::setOnlineCallerLookup)
        } }
        item {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(number, { number = it }, label = { Text(stringResource(I18nR.string.hc_phone_number)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                // User-initiated links: no request is made merely by displaying or typing the number.
                TextButton(enabled = number.any(Char::isDigit), onClick = {
                    open("https://www.google.com/search?q=" + Uri.encode("\"${number.filter { it.isDigit() || it == '+' }}\""))
                }) { Text("Google · " + stringResource(I18nR.string.ts_search)) }
                TextButton(enabled = de.mm20.launcher2.comms.callerid.CallerLookupParser.greekNumber(number) != null,
                    onClick = { open("https://www.white-pages.gr/arithmos/" + de.mm20.launcher2.comms.callerid.CallerLookupParser.greekNumber(number)) }) {
                    Text("white-pages.gr · " + stringResource(I18nR.string.ts_search))
                }
            }
        }
    }
}
