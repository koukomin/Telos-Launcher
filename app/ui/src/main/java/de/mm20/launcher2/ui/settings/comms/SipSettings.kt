package de.mm20.launcher2.ui.settings.comms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.comms.remote.SecretBox
import de.mm20.launcher2.comms.sip.SipEngine
import de.mm20.launcher2.comms.sip.SipRegistration
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import org.koin.compose.koinInject

@Composable
fun SipSettings() {
    val settings: CommsSettings = koinInject()
    val snap by settings.snapshot.collectAsStateWithLifecycle(null)
    val registration by SipEngine.registration.collectAsStateWithLifecycle()
    val error by SipEngine.lastError.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    val current = snap ?: return
    val context = androidx.compose.ui.platform.LocalContext.current

    // SIP calls need the microphone, and incoming calls the notification permission (Android 13+)
    val permissions = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[android.Manifest.permission.RECORD_AUDIO] != false) settings.setSipEnabled(true)
        else android.widget.Toast.makeText(context, "SIP calls need the microphone permission", android.widget.Toast.LENGTH_LONG).show()
    }
    fun enable() {
        val needed = buildList {
            add(android.Manifest.permission.RECORD_AUDIO)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
        }.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) settings.setSipEnabled(true) else permissions.launch(needed.toTypedArray())
    }

    val status = when {
        !SipEngine.available -> "SIP is not available in this build or on this device (needs Android 9 or newer)"
        !current.sipEnabled -> "Switched off"
        registration == SipRegistration.Registered -> "Registered, ready for calls"
        registration == SipRegistration.Registering -> "Connecting…"
        registration == SipRegistration.Failed -> "Registration failed" + if (error.isNotBlank()) ": $error" else ""
        else -> "Starting…"
    }

    PreferenceCategory(title = "SIP / VoIP account") {
        SwitchPreference(
            title = "Use SIP account",
            summary = "Keeps the account registered in the background (shows a small notification) so calls can be received. $status",
            value = current.sipEnabled,
            enabled = SipEngine.available && current.sipUser.isNotBlank(),
            onValueChanged = { if (it) enable() else settings.setSipEnabled(false) },
        )
        Preference(
            title = "Account",
            summary = if (current.sipUser.isBlank()) "Not configured"
            else "${current.sipUser} @ ${current.sipDomain}",
            onClick = { showDialog = true },
        )
        ListPreference(
            title = "Outgoing calls",
            items = listOf(
                "Never, receive calls only" to "off",
                "Offer a SIP button when calling" to "choose",
                "Use SIP by default" to "default",
            ),
            value = current.sipOutgoing,
            onValueChanged = { settings.setSipOutgoing(it) },
        )
    }

    if (showDialog) {
        var user by remember { mutableStateOf(current.sipUser) }
        var domain by remember { mutableStateOf(current.sipDomain) }
        var name by remember { mutableStateOf(current.sipDisplayName) }
        var pass by remember { mutableStateOf(SecretBox.decrypt(current.sipPasswordEnc)) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("SIP account") },
            text = {
                Column {
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = { Text("Server (FRITZ!Box: fritz.box)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("User name (FRITZ!Box: the IP phone user)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = domain.isNotBlank() && domain.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == ':' || it == '_' }, onClick = {
                    settings.setSipAccount(user, domain, name, SecretBox.encrypt(pass))
                    showDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
        )
    }
}
