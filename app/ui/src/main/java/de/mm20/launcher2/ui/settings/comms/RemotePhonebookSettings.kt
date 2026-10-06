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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.comms.remote.RemotePhonebook
import de.mm20.launcher2.comms.remote.SecretBox
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date

@Composable
fun RemotePhonebookSettings() {
    val settings: CommsSettings = koinInject()
    val scope = rememberCoroutineScope()
    val enabled by settings.remotePhonebookEnabled.collectAsStateWithLifecycle(false)
    val host by settings.remotePhonebookHost.collectAsStateWithLifecycle("fritz.box")
    val user by settings.remotePhonebookUser.collectAsStateWithLifecycle("")
    val passwordEnc by settings.remotePhonebookPasswordEnc.collectAsStateWithLifecycle("")
    var showDialog by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    PreferenceCategory(title = "Remote phonebook (FRITZ!Box)") {
        SwitchPreference(
            title = "Identify callers from FRITZ!Box",
            summary = "Show names from the FRITZ!Box telephone book for numbers that are not in your contacts",
            value = enabled,
            onValueChanged = {
                settings.setRemotePhonebook(it, host, user, passwordEnc)
                if (!it) RemotePhonebook.clear()
            },
        )
        Preference(
            title = "Connection",
            summary = if (user.isBlank()) "Not configured" else "$user @ $host",
            onClick = { showDialog = true },
        )
        Preference(
            title = "Sync now",
            summary = status ?: RemotePhonebook.lastSyncMillis.takeIf { it > 0 }?.let {
                "Last sync: " + DateFormat.getDateTimeInstance().format(Date(it))
            } ?: "Never synced",
            onClick = {
                if (busy || !enabled || user.isBlank()) return@Preference
                busy = true
                status = "Syncing…"
                scope.launch {
                    val result = RemotePhonebook.sync(host, user, SecretBox.decrypt(passwordEnc))
                    status = result.fold(
                        onSuccess = { "$it contacts synced" },
                        onFailure = { "Failed: ${it.message}" },
                    )
                    busy = false
                }
            },
        )
    }

    if (showDialog) {
        var hostInput by remember { mutableStateOf(host) }
        var userInput by remember { mutableStateOf(user) }
        var passInput by remember { mutableStateOf(SecretBox.decrypt(passwordEnc)) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("FRITZ!Box connection") },
            text = {
                Column {
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text("Address (e.g. fritz.box or 192.168.178.1)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        label = { Text("User") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = passInput,
                        onValueChange = { passInput = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    settings.setRemotePhonebook(
                        enabled = enabled,
                        host = hostInput,
                        user = userInput,
                        passwordEnc = SecretBox.encrypt(passInput),
                    )
                    showDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        )
    }
}
