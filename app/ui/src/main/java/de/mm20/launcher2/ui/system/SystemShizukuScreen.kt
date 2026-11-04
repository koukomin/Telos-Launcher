package de.mm20.launcher2.ui.system
import de.mm20.launcher2.i18n.R as I18nR

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.appmanagement.shizuku.TelosShizukuHost
import de.mm20.launcher2.appmanagement.shizuku.TelosShizukuWatchdog
import de.mm20.launcher2.ktx.ShizukuProcesses
import de.mm20.launcher2.ktx.TelosShellEngine
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.settings.security.TelosSecurityHubBody
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SystemShizukuTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui: UiSettings = koinInject()
    val host by TelosShizukuHost.status.collectAsStateWithLifecycle()
    val auto by ui.shizukuAutoStart.collectAsStateWithLifecycle(false)
    var log by remember { mutableStateOf("") }
    var shellCmd by remember { mutableStateOf("id") }
    var shellOut by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { TelosShizukuHost.refresh(context) }
    DisposableEffect(Unit) { onDispose { TelosShizukuHost.stopPairingWait() } }
    var autoPaired by remember { mutableStateOf("") }
    LaunchedEffect(host.pairing.code, host.pairing.phase, host.pairing.host, host.pairing.port) {
        if (host.pairing.phase == TelosShizukuHost.PairingPhase.WaitingForCode &&
            host.pairing.code.length == 6
        ) {
            val sig = "${host.pairing.code}:${host.pairing.host}:${host.pairing.port}"
            if (sig == autoPaired) return@LaunchedEffect
            autoPaired = sig
            val r = TelosShizukuHost.submitPairingCode(context, host.pairing.code)
            log = r.getOrElse { it.message.orEmpty() }
        }
    }

    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(I18nR.string.telos_shizuku_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        I18nR.string.telos_shizuku_status,
                        if (host.running) stringResource(I18nR.string.security_hub_active) else stringResource(I18nR.string.security_hub_inactive),
                        host.uid,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(I18nR.string.telos_shizuku_starter, host.starterPath.ifBlank { "—" }), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(I18nR.string.telos_shizuku_howto_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(I18nR.string.telos_shizuku_howto_body), style = MaterialTheme.typography.bodySmall)
                Text(host.pcAdbCommand.ifBlank { TelosShizukuHost.pcAdbCommand(context) }, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    val cmd = host.pcAdbCommand.ifBlank { TelosShizukuHost.pcAdbCommand(context) }
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(ClipData.newPlainText("adb", cmd))
                    Toast.makeText(context, context.getString(I18nR.string.telos_shizuku_copied), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(I18nR.string.telos_shizuku_copy_adb)) }
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text(stringResource(I18nR.string.telos_shizuku_enable_a11y)) }
                Text(stringResource(I18nR.string.telos_shizuku_pair_title), style = MaterialTheme.typography.titleSmall)
                val phase = host.pairing.phase
                val pairStatus = when (phase) {
                    TelosShizukuHost.PairingPhase.Searching ->
                        stringResource(I18nR.string.telos_shizuku_pair_searching)
                    TelosShizukuHost.PairingPhase.WaitingForCode ->
                        if (host.pairing.host != null && host.pairing.port != null) {
                            stringResource(
                                I18nR.string.telos_shizuku_pair_found,
                                host.pairing.host ?: "",
                                host.pairing.port ?: 0,
                            )
                        } else stringResource(I18nR.string.telos_shizuku_pair_waiting)
                    TelosShizukuHost.PairingPhase.Working ->
                        stringResource(I18nR.string.telos_shizuku_pair_working)
                    TelosShizukuHost.PairingPhase.Done ->
                        stringResource(I18nR.string.telos_shizuku_pair_ok)
                    TelosShizukuHost.PairingPhase.Failed ->
                        stringResource(I18nR.string.telos_shizuku_pair_fail)
                    TelosShizukuHost.PairingPhase.Idle -> null
                }
                if (pairStatus != null) {
                    Text(pairStatus, style = MaterialTheme.typography.bodySmall)
                }
                if (phase == TelosShizukuHost.PairingPhase.Searching ||
                    phase == TelosShizukuHost.PairingPhase.WaitingForCode
                ) {
                    OutlinedTextField(
                        value = host.pairing.code,
                        onValueChange = { TelosShizukuHost.setPairingCode(it) },
                        label = { Text(stringResource(I18nR.string.telos_shizuku_pair_code)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val r = TelosShizukuHost.submitPairingCode(context, host.pairing.code)
                                log = r.getOrElse { it.message.orEmpty() }
                            }
                        },
                        enabled = host.pairing.code.length == 6,
                    ) { Text(stringResource(I18nR.string.telos_shizuku_pair_submit)) }
                    OutlinedButton(onClick = { TelosShizukuHost.stopPairingWait() }) {
                        Text(stringResource(I18nR.string.telos_shizuku_pair_stop))
                    }
                } else {
                    FilledTonalButton(onClick = { TelosShizukuHost.startPairingWait(context) }) {
                        Text(stringResource(I18nR.string.telos_shizuku_pair_start))
                    }
                }
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent("com.android.settings.WIFI_DEBUGGING_SETTINGS")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.onFailure {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }) { Text(stringResource(I18nR.string.telos_shizuku_pair_open)) }
                if (host.pairing.code.isNotBlank() && phase == TelosShizukuHost.PairingPhase.Done) {
                    Text(
                        stringResource(
                            I18nR.string.telos_shizuku_code_seen,
                            host.pairing.code,
                            host.pairing.port?.toString() ?: "—",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FilledTonalButton(onClick = {
                    scope.launch {
                        val r = TelosShizukuHost.start(context, TelosShizukuHost.StartMethod.Auto)
                        log = r.getOrElse { it.message.orEmpty() }
                        TelosShizukuWatchdog.start(context)
                    }
                }) { Text(stringResource(I18nR.string.telos_shizuku_start)) }
                OutlinedButton(onClick = {
                    scope.launch {
                        TelosShizukuHost.start(context, TelosShizukuHost.StartMethod.Root)
                    }
                }) { Text(stringResource(I18nR.string.telos_shizuku_start_root)) }
                OutlinedButton(onClick = {
                    scope.launch {
                        TelosShizukuHost.start(context, TelosShizukuHost.StartMethod.Wireless)
                    }
                }) { Text(stringResource(I18nR.string.telos_shizuku_start_wireless)) }
                OutlinedButton(onClick = {
                    scope.launch { TelosShizukuHost.stop(); TelosShizukuHost.refresh(context) }
                }) { Text(stringResource(I18nR.string.telos_shizuku_stop)) }
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text(stringResource(I18nR.string.telos_shizuku_open_devopts)) }
                if (log.isNotBlank()) Text(log, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.telos_shizuku_settings)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.settings_shizuku_autostart),
                    summary = stringResource(I18nR.string.telos_shizuku_autostart_summary),
                    value = auto,
                    onValueChanged = {
                        ui.setShizukuAutoStart(it)
                        if (it) TelosShizukuWatchdog.start(context) else TelosShizukuWatchdog.stop(context)
                    },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.telos_shizuku_labs)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch { TelosShellEngine.execResult("reboot", timeoutMs = 8_000L) }
                    }) { Text(stringResource(I18nR.string.telos_shizuku_reboot)) }
                    OutlinedButton(onClick = {
                        scope.launch { TelosShellEngine.execResult("reboot recovery", timeoutMs = 8_000L) }
                    }) { Text(stringResource(I18nR.string.telos_shizuku_recovery)) }
                    OutlinedButton(onClick = {
                        scope.launch { TelosShellEngine.execResult("input keyevent 26", timeoutMs = 5_000L) }
                    }) { Text(stringResource(I18nR.string.telos_shizuku_screen_off)) }
                    OutlinedButton(onClick = {
                        scope.launch { TelosShellEngine.execResult("wm density reset", timeoutMs = 8_000L) }
                    }) { Text(stringResource(I18nR.string.telos_shizuku_density_reset)) }
                    androidx.compose.material3.OutlinedTextField(
                        value = shellCmd,
                        onValueChange = { if (it.length < 512) shellCmd = it },
                        label = { Text(stringResource(I18nR.string.telos_shizuku_shell)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FilledTonalButton(onClick = {
                        scope.launch {
                            ShizukuProcesses.init(context)
                            val r = TelosShellEngine.execResult(shellCmd, timeoutMs = 15_000L)
                            shellOut = (r.stdout + r.stderr).take(4000)
                        }
                    }) { Text(stringResource(I18nR.string.telos_shizuku_run)) }
                    if (shellOut.isNotBlank()) Text(shellOut, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { TelosSecurityHubBody() }
    }
}
