package de.mm20.launcher2.ui.media.video

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.media.video.trakt.Trakt
import de.mm20.launcher2.comms.media.video.trakt.TraktDeviceCode
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Trakt settings of Telos Video: the user's own Trakt application (client id and secret), device code
 * sign in, the scrobbling switch and the queue of scrobbles that could not be sent.
 */
@Composable
internal fun TraktDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var login by remember { mutableStateOf<Trakt.Login?>(null) }
    var queue by remember { mutableIntStateOf(0) }
    var clientId by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var code by remember { mutableStateOf<TraktDeviceCode?>(null) }
    var message by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var waitingBrowser by remember { mutableStateOf(false) }
    var useCode by remember { mutableStateOf(false) }

    // the stored login is decrypted with the keystore: not on the main thread
    suspend fun reload() {
        val l = withContext(Dispatchers.IO) { Trakt.login(context) to Trakt.queueSize(context) }
        login = l.first
        queue = l.second
    }
    LaunchedEffect(Unit) {
        reload()
        login?.let { clientId = it.clientId; secret = it.clientSecret }
    }
    // coming back from the browser: the redirect may have stored the login meanwhile
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    reload()
                    if (login?.connected == true && waitingBrowser) { waitingBrowser = false; message = context.getString(R.string.au_video_trakt_connected) }
                }
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val l = login ?: return

    AlertDialog(
        onDismissRequest = { job?.cancel(); onDismiss() },
        title = { Text(stringResource(R.string.au7_trakt_title)) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.au8_traktweb_howto), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.au7_trakt_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (l.connected) {
                    Text(stringResource(R.string.au7_trakt_signed_in), style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.au7_trakt_switch), modifier = Modifier.weight(1f))
                        Switch(checked = l.enabled, onCheckedChange = { on ->
                            scope.launch {
                                withContext(Dispatchers.IO) { Trakt.setEnabled(context, on) }
                                reload()
                            }
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.au7_trakt_queue, queue), modifier = Modifier.weight(1f))
                        TextButton(enabled = queue > 0, onClick = {
                            scope.launch { withContext(Dispatchers.IO) { Trakt.clearQueue(context) }; reload() }
                        }) { Text(stringResource(R.string.au7_trakt_clear_queue)) }
                    }
                    TextButton(onClick = {
                        scope.launch { withContext(Dispatchers.IO) { Trakt.signOut(context) }; reload() }
                    }) { Text(stringResource(R.string.au7_trakt_sign_out)) }
                } else {
                    OutlinedTextField(clientId, { clientId = it }, label = { Text(stringResource(R.string.hc_client_id)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        secret, { secret = it }, label = { Text(stringResource(R.string.hc_client_secret)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.au8_traktweb_redirect_label), style = MaterialTheme.typography.labelMedium)
                    Text(Trakt.REDIRECT_URI, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { copyCode(context, Trakt.REDIRECT_URI) }) { Text(stringResource(R.string.au8_traktweb_copy_redirect)) }
                    androidx.compose.material3.Button(
                        enabled = clientId.isNotBlank() && secret.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                val url = withContext(Dispatchers.IO) {
                                    Trakt.saveApp(context, clientId, secret)
                                    Trakt.beginWebLogin(context, clientId)
                                }
                                val ok = runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.isSuccess
                                waitingBrowser = ok
                                message = context.getString(if (ok) R.string.au8_traktweb_waiting_browser else R.string.au8_traktweb_no_browser)
                            }
                        },
                    ) { Text(stringResource(R.string.au8_traktweb_sign_in_browser)) }
                    if (!useCode && code == null) {
                        TextButton(onClick = { useCode = true }) { Text(stringResource(R.string.au8_traktweb_use_code)) }
                    }
                    val pending = code
                    if (!useCode && pending == null) {
                        // device code sign in is hidden until asked for
                    } else if (pending == null) {
                        TextButton(enabled = clientId.isNotBlank() && secret.isNotBlank(), onClick = {
                            message = context.getString(R.string.au_video_trakt_contacting)
                            job = scope.launch {
                                withContext(Dispatchers.IO) { Trakt.saveApp(context, clientId, secret) }
                                runCatching { Trakt.startDeviceLogin(clientId.trim()) }
                                    .onSuccess { c ->
                                        code = c
                                        message = context.getString(R.string.au7_trakt_waiting, c.verificationUrl)
                                        try {
                                            Trakt.finishDeviceLogin(context, clientId.trim(), secret.trim(), c)
                                            message = context.getString(R.string.au_video_trakt_connected)
                                            reload()
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            message = context.getString(R.string.au_video_trakt_signin_failed)
                                        } finally {
                                            code = null
                                        }
                                    }
                                    .onFailure { if (it is CancellationException) throw it; message = context.getString(R.string.au_video_trakt_unreachable) }
                            }
                        }) { Text(stringResource(R.string.au7_trakt_sign_in)) }
                    } else {
                        Text(stringResource(R.string.au_video_trakt_open_and_enter, pending.verificationUrl), style = MaterialTheme.typography.bodyMedium)
                        Text(pending.userCode, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Row {
                            TextButton(onClick = { copyCode(context, pending.userCode) }) { Text(stringResource(R.string.au7_trakt_copy)) }
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pending.verificationUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }) { Text(stringResource(R.string.hc_open_trakt_tv_activate)) }
                        }
                    }
                }
                if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { job?.cancel(); onDismiss() }) { Text(stringResource(R.string.close)) } },
    )
}

private fun copyCode(context: Context, text: String) {
    runCatching {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Trakt", text))
    }
}
