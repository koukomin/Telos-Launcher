package de.mm20.launcher2.ui.system

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.mm20.launcher2.comms.AuthManager
import de.mm20.launcher2.comms.graphics.HwuiRenderer
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SavedWifiPasswordsPreference() {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<WifiEntry>?>(null) }
    var loading by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { enabled = HwuiRenderer.privilegedAvailable() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { entries = null }
    Preference(
        title = stringResource(R.string.telos_system_saved_wifi),
        summary = stringResource(R.string.telos_system_wifi_need_root),
        enabled = enabled && !loading,
        onClick = { scope.launch {
            if (activity == null || loading) return@launch
            loading = true
            try {
                if (AuthManager().authenticateNative(activity, context.getString(R.string.telos_system_unlock_wifi))) {
                    entries = withContext(Dispatchers.IO) { readWifiStore() }
                }
            } finally { loading = false }
        } },
    )
    entries?.let { networks ->
        AlertDialog(onDismissRequest = { entries = null },
            title = { Text(stringResource(R.string.telos_system_saved_wifi)) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (networks.isEmpty()) item { Text(stringResource(R.string.telos_system_wifi_need_root)) }
                    items(networks, key = { it.ssid }) { entry ->
                        Column { Text(entry.ssid, style = MaterialTheme.typography.titleMedium); Text(entry.psk ?: "—") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { entries = null }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}
