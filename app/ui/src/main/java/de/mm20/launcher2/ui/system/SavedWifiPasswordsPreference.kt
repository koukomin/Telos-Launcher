package de.mm20.launcher2.ui.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.mm20.launcher2.comms.AuthManager
import de.mm20.launcher2.comms.graphics.HwuiRenderer
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.comms.ContactQrImage
import de.mm20.launcher2.ui.component.preferences.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun SavedWifiPasswordsPreference() {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<WifiEntry>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedQrPayload by remember { mutableStateOf<String?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            enabled = runCatching { HwuiRenderer.privilegedAvailable() }.getOrDefault(false)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        entries = null
    }

    Preference(
        title = stringResource(R.string.telos_system_saved_wifi),
        summary = stringResource(R.string.telos_system_wifi_need_root),
        enabled = enabled && !loading,
        onClick = {
            scope.launch {
                if (loading) return@launch
                loading = true
                try {
                    val authenticated = if (activity != null) {
                        runCatching {
                            AuthManager().authenticateNative(activity, context.getString(R.string.telos_system_unlock_wifi))
                        }.getOrDefault(true)
                    } else true

                    if (authenticated) {
                        entries = withContext(Dispatchers.IO) {
                            runCatching { readWifiStore() }.getOrDefault(emptyList())
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading Wi-Fi passwords", Toast.LENGTH_SHORT).show()
                } finally {
                    loading = false
                }
            }
        },
    )

    // Main Saved Wi-Fi Networks Dialog
    entries?.let { networks ->
        val filteredNetworks = remember(networks, query) {
            networks.filter {
                query.isBlank() || it.ssid.contains(query.trim(), ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { entries = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.telos_system_saved_wifi))
                    Text(
                        text = "Total: ${networks.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.hc_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Export / Backup Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                exportWifiBackup(context, networks)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Backup (JSON)")
                        }
                    }

                    if (filteredNetworks.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.telos_system_wifi_need_root))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(filteredNetworks, key = { index, item -> "${item.ssid}_$index" }) { _, entry ->
                                WifiNetworkCard(
                                    entry = entry,
                                    onShowQr = {
                                        val psk = entry.psk.orEmpty()
                                        selectedQrPayload = "WIFI:T:WPA;S:${entry.ssid};P:$psk;;"
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { entries = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    // QR Code Dialog
    selectedQrPayload?.let { payload ->
        AlertDialog(
            onDismissRequest = { selectedQrPayload = null },
            title = { Text("Wi-Fi QR Code") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ContactQrImage(payload, Modifier.size(220.dp).padding(12.dp))
                    Text(
                        text = "Scan to connect automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedQrPayload = null }) {
                    Text(stringResource(R.string.hc_close))
                }
            }
        )
    }
}

@Composable
private fun WifiNetworkCard(
    entry: WifiEntry,
    onShowQr: () -> Unit
) {
    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.ssid,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (passwordVisible) entry.psk ?: "Open Network (No Password)" else "••••••••",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry.psk.isNullOrBlank()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        painter = painterResource(if (passwordVisible) R.drawable.visibility_24px else R.drawable.visibility_off_24px),
                        contentDescription = "Toggle Password"
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Copy
                TextButton(onClick = {
                    val psk = entry.psk.orEmpty()
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Wi-Fi Password", psk))
                    Toast.makeText(context, "Password copied!", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy")
                }

                // Share
                TextButton(onClick = {
                    val psk = entry.psk.orEmpty()
                    val text = "Wi-Fi Network: ${entry.ssid}\nPassword: $psk"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Wi-Fi"))
                }) {
                    Text("Share")
                }

                // QR
                TextButton(onClick = onShowQr) {
                    Text("QR Code")
                }
            }
        }
    }
}

private fun exportWifiBackup(context: Context, networks: List<WifiEntry>) {
    runCatching {
        val array = JSONArray()
        networks.forEach {
            array.put(JSONObject().apply {
                put("ssid", it.ssid)
                put("psk", it.psk ?: "")
            })
        }
        val text = array.toString(2)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, "telos_wifi_backup.json")
        }
        context.startActivity(Intent.createChooser(intent, "Export Wi-Fi Backup"))
    }.onFailure {
        Toast.makeText(context, "Failed to export Wi-Fi backup", Toast.LENGTH_SHORT).show()
    }
}
