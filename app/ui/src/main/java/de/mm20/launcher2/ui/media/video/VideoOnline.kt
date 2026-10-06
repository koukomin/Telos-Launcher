package de.mm20.launcher2.ui.media.video

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.media.video.VideoServices
import kotlinx.coroutines.launch
import de.mm20.launcher2.comms.media.video.VideoServicesConfig

/** Opens the player with a web address, a magnet link or a .torrent file. */
internal fun openSource(context: Context, source: String, torrentFile: Boolean = false) {
    context.startActivity(
        Intent(context, VideoPlayerActivity::class.java)
            .putExtra(VideoPlayerActivity.EXTRA_SOURCE, source.trim())
            .putExtra(VideoPlayerActivity.EXTRA_IS_TORRENT, torrentFile)
    )
}

@Composable
internal fun OpenSourceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // a magnet link or address on the clipboard is filled in right away
    var text by remember {
        mutableStateOf(
            clipboard.getText()?.text?.trim()?.takeIf {
                it.startsWith("magnet:", true) || it.startsWith("http://", true) || it.startsWith("https://", true)
            }.orEmpty()
        )
    }
    val torrentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            openSource(context, uri.toString(), torrentFile = true)
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play from the web") },
        text = {
            Column {
                Text(
                    "Web address of a video or stream (HLS, DASH, MP4…), a magnet link, or the address of a .torrent file.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Address or magnet link") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { clipboard.getText()?.text?.let { text = it } }) { Text("Paste") }
                    TextButton(onClick = { torrentPicker.launch(arrayOf("*/*")) }) { Text("Choose .torrent file") }
                }
                Text(
                    "Torrents are downloaded while they play and deleted when the player is closed. Only play content you are allowed to watch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    openSource(context, text)
                    onDismiss()
                },
            ) { Text("Play") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun VideoServicesDialog(onDismiss: () -> Unit) {
    var config by remember { mutableStateOf<VideoServicesConfig?>(null) }
    LaunchedEffect(Unit) { config = VideoServices.config() }
    val c = config ?: return

    var tmdb by remember { mutableStateOf(c.tmdbKey) }
    var subKey by remember { mutableStateOf(c.subtitleKey) }
    var subUser by remember { mutableStateOf(c.subtitleUser) }
    var subPass by remember { mutableStateOf(c.subtitlePassword) }
    var languages by remember { mutableStateOf(c.languages) }
    var auto by remember { mutableStateOf(c.autoDownload) }
    var wifiOnly by remember { mutableStateOf(c.torrentWifiOnly) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video services") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Posters and descriptions", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Posters work without a key (from Wikipedia). A free TMDB API key (themoviedb.org, Settings, API) finds more and adds ratings. This product uses the TMDB API but is not endorsed or certified by TMDB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(tmdb, { tmdb = it }, label = { Text("TMDB API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Text("Subtitles", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Text(
                    "OpenSubtitles API key (opensubtitles.com, Consumers) and your account, which they require for downloads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(subKey, { subKey = it }, label = { Text("OpenSubtitles API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(subUser, { subUser = it }, label = { Text("User name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    subPass, { subPass = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    languages, { languages = it }, label = { Text("Languages (e.g. el,en)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Download subtitles automatically", modifier = Modifier.weight(1f))
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }

                TraktSection()

                Text("Torrents", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Only on Wi-Fi", modifier = Modifier.weight(1f))
                    Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                VideoServices.save(VideoServicesConfig(tmdb, subKey, subUser, subPass, languages, auto, wifiOnly))
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

/** Trakt.tv sign in with the device code: create an app at trakt.tv/oauth/applications first. */
@Composable
private fun TraktSection() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var login by remember { mutableStateOf(de.mm20.launcher2.comms.media.video.trakt.Trakt.login(context)) }
    var clientId by remember { mutableStateOf(login.clientId) }
    var secret by remember { mutableStateOf(login.clientSecret) }
    var code by remember { mutableStateOf<de.mm20.launcher2.comms.media.video.trakt.TraktDeviceCode?>(null) }
    var message by remember { mutableStateOf("") }

    Text("Trakt.tv", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
    Text(
        "Scrobbles what you watch, marks watched videos and adds titles to your watchlist. Create an application at trakt.tv/oauth/applications (redirect address urn:ietf:wg:oauth:2.0:oob) and enter its client id and secret.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (login.connected) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("Connected, scrobbling", modifier = Modifier.weight(1f))
            Switch(checked = login.enabled, onCheckedChange = {
                de.mm20.launcher2.comms.media.video.trakt.Trakt.setEnabled(context, it)
                login = de.mm20.launcher2.comms.media.video.trakt.Trakt.login(context)
            })
        }
        TextButton(onClick = {
            de.mm20.launcher2.comms.media.video.trakt.Trakt.signOut(context)
            login = de.mm20.launcher2.comms.media.video.trakt.Trakt.login(context)
        }) { Text("Sign out") }
    } else {
        OutlinedTextField(clientId, { clientId = it }, label = { Text("Client id") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            secret, { secret = it }, label = { Text("Client secret") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        val pending = code
        if (pending == null) {
            TextButton(enabled = clientId.isNotBlank() && secret.isNotBlank(), onClick = {
                de.mm20.launcher2.comms.media.video.trakt.Trakt.saveApp(context, clientId, secret)
                message = "Contacting Trakt…"
                scope.launch {
                    runCatching { de.mm20.launcher2.comms.media.video.trakt.Trakt.startDeviceLogin(clientId.trim()) }
                        .onSuccess { c ->
                            code = c
                            message = ""
                            runCatching {
                                de.mm20.launcher2.comms.media.video.trakt.Trakt.finishDeviceLogin(context, clientId.trim(), secret.trim(), c)
                            }.onSuccess {
                                login = de.mm20.launcher2.comms.media.video.trakt.Trakt.login(context)
                                message = "Connected"
                            }.onFailure { message = it.message ?: "Sign in failed" }
                            code = null
                        }
                        .onFailure { message = it.message ?: "Could not reach Trakt" }
                }
            }) { Text("Connect Trakt") }
        } else {
            Text(
                "Open ${pending.verificationUrl} and enter the code",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(pending.userCode, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(pending.verificationUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Text("Open trakt.tv/activate") }
        }
    }
    if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall)
}
