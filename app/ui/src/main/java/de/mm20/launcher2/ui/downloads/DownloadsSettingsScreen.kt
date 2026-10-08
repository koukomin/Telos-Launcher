package de.mm20.launcher2.ui.downloads

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.downloads.AfterFinish
import de.mm20.launcher2.downloads.DownloadSettings
import de.mm20.launcher2.downloads.ProxyType
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import org.koin.compose.koinInject

@Composable
fun DownloadsSettingsScreen() {
    val context = LocalContext.current
    val store: DownloadSettings = koinInject()
    val s by store.values.collectAsState()
    var editUserAgent by remember { mutableStateOf(false) }
    var editProxy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            store.update { it.copy(defaultFolder = uri.toString()) }
        }
    }

    PreferenceScreen(title = stringResource(R.string.dl_settings_title)) {
        item {
            PreferenceCategory(title = stringResource(R.string.dl_settings_queue)) {
                SliderPreference(
                    title = stringResource(R.string.dl_max_parallel), value = s.maxParallel, min = 1, max = 10,
                    onValueChanged = { v -> store.update { it.copy(maxParallel = v) } },
                    label = { Text(it.toString()) },
                )
                SliderPreference(
                    title = stringResource(R.string.dl_default_connections), value = s.connections, min = 1, max = 16,
                    onValueChanged = { v -> store.update { it.copy(connections = v) } },
                    label = { Text(it.toString()) },
                )
                ListPreference(
                    title = stringResource(R.string.dl_global_limit),
                    items = listOf(0, 256, 512, 1024, 2048, 5120, 10240).map {
                        ListPreferenceItem(if (it == 0) stringResource(R.string.dl_limit_none) else if (it >= 1024) "${it / 1024} MB/s" else "$it KB/s", it)
                    },
                    value = s.speedLimitKBps,
                    summary = if (s.speedLimitKBps == 0) stringResource(R.string.dl_limit_none) else if (s.speedLimitKBps >= 1024) "${s.speedLimitKBps / 1024} MB/s" else "${s.speedLimitKBps} KB/s",
                    onValueChanged = { v -> store.update { it.copy(speedLimitKBps = v) } },
                )
                SliderPreference(
                    title = stringResource(R.string.dl_retries), value = s.maxRetries, min = 0, max = 20,
                    onValueChanged = { v -> store.update { it.copy(maxRetries = v) } },
                    label = { Text(it.toString()) },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.dl_settings_rules)) {
                SwitchPreference(
                    title = stringResource(R.string.dl_wifi_only), summary = stringResource(R.string.dl_wifi_only_summary),
                    value = s.wifiOnly, onValueChanged = { v -> store.update { it.copy(wifiOnly = v) } },
                )
                SwitchPreference(
                    title = stringResource(R.string.dl_low_battery), summary = stringResource(R.string.dl_low_battery_summary, s.lowBatteryPercent),
                    value = s.pauseOnLowBattery, onValueChanged = { v -> store.update { it.copy(pauseOnLowBattery = v) } },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.dl_settings_storage)) {
                Preference(
                    title = stringResource(R.string.dl_default_folder),
                    summary = s.defaultFolder.takeIf { it.isNotEmpty() }?.let { Uri.decode(it).substringAfterLast(':') } ?: stringResource(R.string.dl_folder_default),
                    onClick = { picker.launch(null) },
                )
                if (s.defaultFolder.isNotEmpty()) {
                    Preference(
                        title = stringResource(R.string.dl_default_folder_reset),
                        onClick = { store.update { it.copy(defaultFolder = "") } },
                    )
                }
                ListPreference(
                    title = stringResource(R.string.dl_after_finish),
                    items = listOf(
                        ListPreferenceItem(stringResource(R.string.dl_after_nothing), AfterFinish.Nothing),
                        ListPreferenceItem(stringResource(R.string.dl_after_open), AfterFinish.Open),
                        ListPreferenceItem(stringResource(R.string.dl_after_share), AfterFinish.Share),
                    ),
                    value = s.afterFinish,
                    onValueChanged = { v -> store.update { it.copy(afterFinish = v) } },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.dl_settings_network)) {
                Preference(
                    title = stringResource(R.string.dl_user_agent),
                    summary = s.userAgent.ifBlank { stringResource(R.string.dl_user_agent_default) },
                    onClick = { editUserAgent = true },
                )
                Preference(
                    title = stringResource(R.string.dl_proxy),
                    summary = when (s.proxyType) {
                        ProxyType.None -> stringResource(R.string.dl_proxy_none)
                        ProxyType.Http -> "HTTP ${s.proxyHost}:${s.proxyPort}"
                        ProxyType.Socks -> "SOCKS ${s.proxyHost}:${s.proxyPort}"
                    },
                    onClick = { editProxy = true },
                )
            }
        }
        torrentSettingsItems()
        item {
            PreferenceCategory(title = stringResource(R.string.dl_settings_notifications)) {
                SwitchPreference(
                    title = stringResource(R.string.dl_notifications),
                    value = s.notifications, onValueChanged = { v -> store.update { it.copy(notifications = v) } },
                )
            }
        }
    }

    if (editUserAgent) {
        var text by remember { mutableStateOf(s.userAgent) }
        AlertDialog(
            onDismissRequest = { editUserAgent = false },
            title = { Text(stringResource(R.string.dl_user_agent)) },
            text = {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.dl_user_agent_default)) })
            },
            confirmButton = { TextButton(onClick = { store.update { it.copy(userAgent = text.trim()) }; editUserAgent = false }) { Text(stringResource(R.string.dl_save)) } },
            dismissButton = { TextButton(onClick = { editUserAgent = false }) { Text(stringResource(R.string.dl_cancel)) } },
        )
    }
    if (editProxy) {
        var type by remember { mutableStateOf(s.proxyType) }
        var host by remember { mutableStateOf(s.proxyHost) }
        var port by remember { mutableStateOf(if (s.proxyPort > 0) s.proxyPort.toString() else "") }
        AlertDialog(
            onDismissRequest = { editProxy = false },
            title = { Text(stringResource(R.string.dl_proxy)) },
            text = {
                Column {
                    androidx.compose.foundation.layout.FlowRow {
                        for (t in ProxyType.entries) {
                            androidx.compose.material3.FilterChip(
                                selected = type == t, onClick = { type = t },
                                label = { Text(when (t) { ProxyType.None -> stringResource(R.string.dl_proxy_none); ProxyType.Http -> "HTTP"; ProxyType.Socks -> "SOCKS" }) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    if (type != ProxyType.None) {
                        OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.dl_proxy_host)) })
                        OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.dl_proxy_port)) })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.update { it.copy(proxyType = type, proxyHost = host.trim(), proxyPort = port.toIntOrNull() ?: 0) }
                    editProxy = false
                }) { Text(stringResource(R.string.dl_save)) }
            },
            dismissButton = { TextButton(onClick = { editProxy = false }) { Text(stringResource(R.string.dl_cancel)) } },
        )
    }
}
