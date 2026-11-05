package de.mm20.launcher2.ui.system
import de.mm20.launcher2.i18n.R as I18nR

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.appmanagement.shizuku.TelosAdbPairingService
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
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val ui: UiSettings = koinInject()
    val host by TelosShizukuHost.status.collectAsStateWithLifecycle()
    val auto by ui.shizukuAutoStart.collectAsStateWithLifecycle(false)
    var log by remember { mutableStateOf("") }
    var shellCmd by remember { mutableStateOf("id") }
    var shellOut by remember { mutableStateOf("") }
    var automaticPairing by rememberSaveable { mutableStateOf(true) }
    val pairingPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) TelosAdbPairingService.start(context, automaticPairing)
    }
    fun beginPairing(automatic: Boolean) {
        automaticPairing = automatic
        if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) {
            pairingPermission.launch("android.permission.ACCESS_LOCAL_NETWORK")
        } else TelosAdbPairingService.start(context, automatic)
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        TelosShizukuHost.refresh(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }


    val running = host.running
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (running) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (running) stringResource(I18nR.string.telos_shizuku_running)
                        else stringResource(I18nR.string.telos_shizuku_stopped),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(
                            I18nR.string.telos_shizuku_status,
                            if (running) stringResource(I18nR.string.security_hub_active)
                            else stringResource(I18nR.string.security_hub_inactive),
                            host.uid,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                val r = TelosShizukuHost.start(context, TelosShizukuHost.StartMethod.Auto)
                                log = r.getOrElse { it.message.orEmpty() }
                                if (auto) TelosShizukuWatchdog.start(context)
                            }
                        }) { Text(stringResource(I18nR.string.telos_shizuku_card_start)) }
                        OutlinedButton(onClick = {
                            scope.launch { TelosShizukuWatchdog.stop(context); TelosShizukuHost.stop(); TelosShizukuHost.refresh(context) }
                        }) { Text(stringResource(I18nR.string.telos_shizuku_card_stop)) }
                        OutlinedButton(onClick = {
                            scope.launch {
                                TelosShizukuWatchdog.stop(context)
                                TelosShizukuHost.stop()
                                val r = TelosShizukuHost.start(context, TelosShizukuHost.StartMethod.Auto)
                                log = r.getOrElse { it.message.orEmpty() }
                            }
                        }) { Text(stringResource(I18nR.string.telos_shizuku_card_restart)) }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(I18nR.string.telos_shizuku_howto_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(I18nR.string.telos_shizuku_howto_body), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            beginPairing(true)
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(I18nR.string.telos_shizuku_pair_start)) }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(I18nR.string.telos_shizuku_enable_a11y)) }
                }
                OutlinedButton(onClick = { beginPairing(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(I18nR.string.telos_shizuku_pair_notif_enter))
                }
                OutlinedButton(onClick = {
                    TelosAdbPairingService.openWirelessDebugging(context)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(I18nR.string.telos_shizuku_wireless))
                }
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(I18nR.string.telos_shizuku_open_devopts))
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        val result = TelosShizukuHost.start(context, TelosShizukuHost.StartMethod.Root)
                        log = result.getOrElse { it.message.orEmpty() }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(I18nR.string.telos_shizuku_start_root))
                }
                val phase = host.pairing.phase
                if (phase == TelosShizukuHost.PairingPhase.Searching ||
                    phase == TelosShizukuHost.PairingPhase.WaitingForCode ||
                    phase == TelosShizukuHost.PairingPhase.Working
                ) {
                    Text(
                        when (phase) {
                            TelosShizukuHost.PairingPhase.Searching ->
                                stringResource(I18nR.string.telos_shizuku_pair_searching)
                            TelosShizukuHost.PairingPhase.Working ->
                                stringResource(I18nR.string.telos_shizuku_pair_working)
                            else -> stringResource(I18nR.string.telos_shizuku_pair_waiting)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = host.pairing.code,
                        onValueChange = { TelosShizukuHost.setPairingCode(it) },
                        label = { Text(stringResource(I18nR.string.telos_shizuku_pair_code)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide()
                            focus.clearFocus()
                            scope.launch {
                                val r = TelosShizukuHost.submitPairingCode(context, host.pairing.code)
                                log = r.getOrElse { it.message.orEmpty() }
                            }
                        }),
                    )
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val r = TelosShizukuHost.submitPairingCode(context, host.pairing.code)
                                log = r.getOrElse { it.message.orEmpty() }
                            }
                        },
                        enabled = host.pairing.code.length == 6,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(I18nR.string.telos_shizuku_pair_submit)) }
                }
                if (host.pairing.phase == TelosShizukuHost.PairingPhase.Done) {
                    Text(stringResource(I18nR.string.telos_shizuku_pair_ok), color = MaterialTheme.colorScheme.primary)
                }
                if (host.pairing.phase == TelosShizukuHost.PairingPhase.Failed) {
                    Text(stringResource(I18nR.string.telos_shizuku_pair_fail), color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = {
                    val cmd = host.pcAdbCommand.ifBlank { TelosShizukuHost.pcAdbCommand(context) }
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("adb", cmd))
                    Toast.makeText(context, context.getString(I18nR.string.telos_shizuku_copied), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(I18nR.string.telos_shizuku_copy_adb)) }
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
                    OutlinedTextField(
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
        item { Spacer(Modifier.height(24.dp)) }
    }
}
