package de.mm20.launcher2.ui.settings.comms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.scrobble.LastFm
import de.mm20.launcher2.comms.scrobble.LibreFm
import de.mm20.launcher2.comms.scrobble.ListenBrainz
import de.mm20.launcher2.comms.scrobble.ScrobbleConfig
import de.mm20.launcher2.comms.scrobble.Scrobblers
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data object ScrobbleSettingsRoute : NavKey

/**
 * Scrobbling of Telos Music and Telos Radio: which sources are sent, and the sign in for
 * Last.fm, Libre.fm and ListenBrainz. Secrets are stored encrypted (see [Scrobblers]).
 */
@Composable
fun ScrobbleSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(Scrobblers.load(context)) }
    var queueCount by remember { mutableIntStateOf(Scrobblers.queue(context).length()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val lastFmConnectedMsg = stringResource(R.string.au_music_lastfm_connected)
    val libreFmConnectedMsg = stringResource(R.string.au_music_librefm_connected)
    val loginFailedMsg = stringResource(R.string.au_music_login_failed_generic)
    val loginFailedFormat = stringResource(R.string.au_music_login_failed)
    val lbOkFormat = stringResource(R.string.au7_scrobble_lb_ok)
    val httpsMsg = stringResource(R.string.au7_scrobble_https)
    val clearedMsg = stringResource(R.string.au7_scrobble_queue_cleared)

    var lfKey by rememberSaveable { mutableStateOf(config.lastfmKey) }
    var lfSecret by remember { mutableStateOf(config.lastfmSecret) }
    var lfUser by rememberSaveable { mutableStateOf(config.lastfmUser) }
    var lfPass by remember { mutableStateOf("") }
    var libUser by rememberSaveable { mutableStateOf(config.librefmUser) }
    var libPass by remember { mutableStateOf("") }
    var lbToken by remember { mutableStateOf(config.listenbrainzToken) }
    var lbServer by rememberSaveable { mutableStateOf(config.listenbrainzServer) }

    fun update(change: (ScrobbleConfig) -> ScrobbleConfig) {
        config = change(config)
        Scrobblers.save(context, config)
    }

    val lastFmReady = config.lastfmSession.isNotBlank()
    val libreFmReady = config.librefmPasswordHash.isNotBlank()
    val listenBrainzReady = config.listenbrainzToken.isNotBlank()
    val active = buildList {
        if (config.lastfmEnabled && lastFmReady) add("Last.fm")
        if (config.librefmEnabled && libreFmReady) add("Libre.fm")
        if (config.listenbrainzEnabled && listenBrainzReady) add("ListenBrainz")
    }
    val status = when {
        active.isEmpty() -> stringResource(R.string.au7_scrobble_status_no_service)
        !config.musicEnabled && !config.radioEnabled -> stringResource(R.string.au7_scrobble_status_no_source)
        else -> stringResource(R.string.au7_scrobble_status_active, active.joinToString(", "))
    }

    PreferenceScreen(title = stringResource(R.string.hc_scrobbling)) {
        item {
            PreferenceCategory {
                Text(
                    stringResource(R.string.au7_scrobble_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                Text(
                    status,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.au7_scrobble_what)) {
                SwitchPreference(
                    title = stringResource(R.string.au7_scrobble_music_title),
                    summary = stringResource(R.string.au7_scrobble_music_summary),
                    value = config.musicEnabled,
                    onValueChanged = { on -> update { it.copy(musicEnabled = on) } },
                )
                SwitchPreference(
                    title = stringResource(R.string.au7_scrobble_radio_title),
                    summary = stringResource(R.string.au7_scrobble_radio_summary),
                    value = config.radioEnabled,
                    onValueChanged = { on -> update { it.copy(radioEnabled = on) } },
                )
            }
        }
        item {
            PreferenceCategory(title = "Last.fm") {
                SwitchPreference(
                    title = stringResource(R.string.au7_scrobble_switch_on),
                    summary = stringResource(if (lastFmReady) R.string.au_music_connected else R.string.au_music_not_connected),
                    value = config.lastfmEnabled,
                    enabled = lastFmReady,
                    onValueChanged = { on -> update { it.copy(lastfmEnabled = on) } },
                )
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        stringResource(R.string.hc_create_a_free_api_account_at_last_fm_api),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Field(lfKey, { lfKey = it }, stringResource(R.string.hc_api_key))
                    Field(lfSecret, { lfSecret = it }, stringResource(R.string.hc_shared_secret), secret = true)
                    Field(lfUser, { lfUser = it }, stringResource(R.string.hc_user_name))
                    Field(lfPass, { lfPass = it }, stringResource(R.string.hc_password_not_stored), secret = true)
                    TextButton(
                        enabled = !busy && lfKey.isNotBlank() && lfSecret.isNotBlank() && lfUser.isNotBlank() && lfPass.isNotBlank(),
                        onClick = {
                            busy = true
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { LastFm.login(lfKey.trim(), lfSecret.trim(), lfUser.trim(), lfPass) } }
                                    .onSuccess { session ->
                                        update { it.copy(lastfmKey = lfKey.trim(), lastfmSecret = lfSecret.trim(), lastfmUser = lfUser.trim(), lastfmSession = session, lastfmEnabled = true) }
                                        lfPass = ""
                                        message = lastFmConnectedMsg
                                    }
                                    .onFailure { message = loginFailedFormat.format("Last.fm", it.message ?: loginFailedMsg) }
                                busy = false
                            }
                        },
                    ) { Text(stringResource(R.string.hc_connect_last_fm)) }
                }
            }
        }
        item {
            PreferenceCategory(title = "ListenBrainz") {
                SwitchPreference(
                    title = stringResource(R.string.au7_scrobble_switch_on),
                    summary = stringResource(if (listenBrainzReady) R.string.au_music_connected else R.string.au_music_not_connected),
                    value = config.listenbrainzEnabled,
                    enabled = listenBrainzReady,
                    onValueChanged = { on -> update { it.copy(listenbrainzEnabled = on) } },
                )
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        stringResource(R.string.hc_copy_your_user_token_from_listenbrainz_o),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Field(lbToken, { lbToken = it }, stringResource(R.string.hc_user_token), secret = true)
                    Field(lbServer, { lbServer = it }, stringResource(R.string.hc_server))
                    TextButton(
                        enabled = !busy && lbToken.isNotBlank(),
                        onClick = {
                            val server = lbServer.trim().trimEnd('/')
                            if (!Scrobblers.isAllowedServer(server)) {
                                message = httpsMsg
                            } else {
                                busy = true
                                scope.launch {
                                runCatching { withContext(Dispatchers.IO) { ListenBrainz(lbToken.trim(), server).validate() } }
                                    .onSuccess { user ->
                                        update { it.copy(listenbrainzToken = lbToken.trim(), listenbrainzServer = server, listenbrainzEnabled = true) }
                                        message = lbOkFormat.format(user)
                                    }
                                    .onFailure { message = loginFailedFormat.format("ListenBrainz", it.message ?: loginFailedMsg) }
                                busy = false
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.au7_scrobble_sign_in)) }
                }
            }
        }
        item {
            PreferenceCategory(title = "Libre.fm") {
                SwitchPreference(
                    title = stringResource(R.string.au7_scrobble_switch_on),
                    summary = stringResource(if (libreFmReady) R.string.au_music_connected else R.string.au_music_not_connected),
                    value = config.librefmEnabled,
                    enabled = libreFmReady,
                    onValueChanged = { on -> update { it.copy(librefmEnabled = on) } },
                )
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Field(libUser, { libUser = it }, stringResource(R.string.hc_user_name))
                    Field(libPass, { libPass = it }, stringResource(R.string.hc_password_only_its_hash_is_stored), secret = true)
                    TextButton(
                        enabled = !busy && libUser.isNotBlank() && libPass.isNotBlank(),
                        onClick = {
                            busy = true
                            scope.launch {
                                // check the login before saving it
                                val hash = Scrobblers.md5Public(libPass)
                                runCatching { withContext(Dispatchers.IO) { LibreFm(libUser.trim(), hash).login() } }
                                    .onSuccess {
                                        update { it.copy(librefmUser = libUser.trim(), librefmPasswordHash = hash, librefmEnabled = true) }
                                        libPass = ""
                                        message = libreFmConnectedMsg
                                    }
                                    .onFailure { message = loginFailedFormat.format("Libre.fm", it.message ?: loginFailedMsg) }
                                busy = false
                            }
                        },
                    ) { Text(stringResource(R.string.hc_connect_libre_fm)) }
                }
            }
        }
        if (message.isNotEmpty()) {
            item {
                Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 28.dp))
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.au7_scrobble_queue)) {
                Preference(
                    title = stringResource(R.string.au7_scrobble_queue_clear),
                    summary = if (queueCount > 0) stringResource(R.string.au7_scrobble_queue_count, queueCount)
                    else stringResource(R.string.au7_scrobble_queue_empty),
                    enabled = queueCount > 0,
                    onClick = {
                        Scrobblers.clearQueue(context)
                        queueCount = 0
                        message = clearedMsg
                    },
                )
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, secret: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}
