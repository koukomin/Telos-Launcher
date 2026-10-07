package de.mm20.launcher2.ui.files.remote

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The sign-in fields of the cloud storages: the ID of the app the user registered, and the sign-in itself. */
@Composable
internal fun CloudFields(c: RemoteConnection, onChange: (RemoteConnection) -> Unit, onStatus: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val provider = remember(c.type) { OAuthProviders.of(c.type) }

    Text(provider.consoleHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = c.clientId, onValueChange = { onChange(c.copy(clientId = it.trim())) }, singleLine = true,
        label = { Text(if (c.type == RemoteType.Dropbox) "App key" else "Client ID") }, modifier = Modifier.fillMaxWidth(),
    )
    if (provider.needsSecret) {
        OutlinedTextField(
            value = c.clientSecret, onValueChange = { onChange(c.copy(clientSecret = it.trim())) }, singleLine = true,
            label = { Text("Client secret") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (c.refreshToken.isNotEmpty()) "Signed in" else "Not signed in", Modifier.weight(1f))
        if (c.refreshToken.isNotEmpty()) TextButton(onClick = { onChange(c.copy(refreshToken = "")) }) { Text("Sign out") }
        Button(
            enabled = c.clientId.isNotBlank() && (!provider.needsSecret || c.clientSecret.isNotBlank()),
            onClick = {
                val verifier = Pkce.verifier()
                val state = Pkce.state()
                onStatus("Waiting for you to sign in in the browser…")
                scope.launch {
                    // the small server has to listen before the browser is opened
                    val code = async(Dispatchers.IO) { runCatching { OAuth.waitForCode(state) } }
                    delay(400)
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OAuth.authorizeUrl(c, verifier, state))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { onStatus("Could not open the browser"); return@launch }
                    code.await().mapCatching { withContext(Dispatchers.IO) { OAuth.exchange(c, it, verifier) } }
                        .onSuccess { onChange(c.copy(refreshToken = it)); onStatus("Connected: signed in. Save the connection.") }
                        .onFailure { onStatus("Sign-in failed: ${it.message}") }
                }
            },
        ) { Text(if (c.refreshToken.isNotEmpty()) "Sign in again" else "Sign in") }
    }
}
