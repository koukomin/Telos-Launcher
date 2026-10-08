package de.mm20.launcher2.ui.media.music

import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.scrobble.LastFm
import de.mm20.launcher2.comms.scrobble.ScrobbleConfig
import de.mm20.launcher2.comms.scrobble.Scrobblers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Switches for the scrobbling services of Telos Music: Last.fm, Libre.fm and ListenBrainz. */
@Composable
internal fun ScrobbleDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(Scrobblers.load(context)) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    var lfKey by remember { mutableStateOf(config.lastfmKey) }
    var lfSecret by remember { mutableStateOf(config.lastfmSecret) }
    var lfUser by remember { mutableStateOf(config.lastfmUser) }
    var lfPass by remember { mutableStateOf("") }
    var libUser by remember { mutableStateOf(config.librefmUser) }
    var libPass by remember { mutableStateOf("") }
    var lbToken by remember { mutableStateOf(config.listenbrainzToken) }
    var lbServer by remember { mutableStateOf(config.listenbrainzServer) }

    fun update(change: (ScrobbleConfig) -> ScrobbleConfig) {
        config = change(config)
        Scrobblers.save(context, config)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hc_scrobbling)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.hc_reports_what_you_listen_to_once_half_of),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Section("Last.fm", config.lastfmEnabled, config.lastfmSession.isNotBlank()) { on ->
                    update { it.copy(lastfmEnabled = on) }
                }
                Text(
                    stringResource(R.string.hc_create_a_free_api_account_at_last_fm_api),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(lfKey, { lfKey = it }, label = { Text(stringResource(R.string.hc_api_key)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(lfSecret, { lfSecret = it }, label = { Text(stringResource(R.string.hc_shared_secret)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(lfUser, { lfUser = it }, label = { Text(stringResource(R.string.hc_user_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    lfPass, { lfPass = it }, label = { Text(stringResource(R.string.hc_password_not_stored)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                TextButton(enabled = !busy && lfKey.isNotBlank() && lfSecret.isNotBlank() && lfUser.isNotBlank() && lfPass.isNotBlank(), onClick = {
                    busy = true
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { LastFm.login(lfKey.trim(), lfSecret.trim(), lfUser.trim(), lfPass) } }
                            .onSuccess { session ->
                                update { it.copy(lastfmKey = lfKey, lastfmSecret = lfSecret, lastfmUser = lfUser, lastfmSession = session, lastfmEnabled = true) }
                                lfPass = ""
                                message = "Last.fm connected"
                            }
                            .onFailure { message = "Last.fm: " + (it.message ?: "login failed") }
                        busy = false
                    }
                }) { Text(stringResource(R.string.hc_connect_last_fm)) }

                Section("Libre.fm", config.librefmEnabled, config.librefmPasswordHash.isNotBlank()) { on ->
                    update { it.copy(librefmEnabled = on) }
                }
                OutlinedTextField(libUser, { libUser = it }, label = { Text(stringResource(R.string.hc_user_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    libPass, { libPass = it }, label = { Text(stringResource(R.string.hc_password_only_its_hash_is_stored)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                TextButton(enabled = !busy && libUser.isNotBlank() && libPass.isNotBlank(), onClick = {
                    busy = true
                    scope.launch {
                        // check the login before saving it
                        val hash = Scrobblers.md5Public(libPass)
                        runCatching { withContext(Dispatchers.IO) { de.mm20.launcher2.comms.scrobble.LibreFm(libUser.trim(), hash).login() } }
                            .onSuccess {
                                update { it.copy(librefmUser = libUser, librefmPasswordHash = hash, librefmEnabled = true) }
                                libPass = ""
                                message = "Libre.fm connected"
                            }
                            .onFailure { message = "Libre.fm: " + (it.message ?: "login failed") }
                        busy = false
                    }
                }) { Text(stringResource(R.string.hc_connect_libre_fm)) }

                Section("ListenBrainz", config.listenbrainzEnabled, config.listenbrainzToken.isNotBlank()) { on ->
                    update { it.copy(listenbrainzEnabled = on) }
                }
                Text(
                    stringResource(R.string.hc_copy_your_user_token_from_listenbrainz_o),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(lbToken, { lbToken = it }, label = { Text(stringResource(R.string.hc_user_token)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(lbServer, { lbServer = it }, label = { Text(stringResource(R.string.hc_server)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(enabled = lbToken.isNotBlank(), onClick = {
                    update { it.copy(listenbrainzToken = lbToken, listenbrainzServer = lbServer, listenbrainzEnabled = true) }
                    message = "ListenBrainz saved"
                }) { Text(stringResource(R.string.hc_save_listenbrainz)) }

                if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.hc_done)) } },
    )
}

@Composable
private fun Section(title: String, on: Boolean, ready: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                if (ready) "Connected" else "Not connected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = onChange, enabled = ready)
    }
}
