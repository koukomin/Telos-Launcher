package de.mm20.launcher2.ui.system
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.appmanagement.shizuku.TelosAdbPairingService
import de.mm20.launcher2.appmanagement.shizuku.TelosShizukuHost
import de.mm20.launcher2.appmanagement.shizuku.TelosShizukuWatchdog
import de.mm20.launcher2.ktx.ShizukuProcesses
import de.mm20.launcher2.ktx.TelosShellEngine
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.settings.security.TelosShizukuAppsPanel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SystemShizukuTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui: UiSettings = koinInject()
    val host by TelosShizukuHost.status.collectAsStateWithLifecycle()
    val auto by ui.shizukuAutoStart.collectAsStateWithLifecycle(false)
    val disableWireless by ui.shizukuDisableWirelessAfterStart.collectAsStateWithLifecycle(false)
    var log by remember { mutableStateOf("") }
    var shellCmd by remember { mutableStateOf("id") }
    var shellOut by remember { mutableStateOf("") }
    var automaticPairing by rememberSaveable { mutableStateOf(true) }

    val pairingPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.isNotEmpty() && results.values.all { it }) TelosAdbPairingService.start(context, automaticPairing)
        else Toast.makeText(context, context.getString(I18nR.string.preference_weather_alerts_test_failed), Toast.LENGTH_LONG).show()
    }

    fun beginPairing(automatic: Boolean) {
        automaticPairing = automatic
        val missing = buildList {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) add("android.permission.ACCESS_LOCAL_NETWORK")
        }
        if (missing.isNotEmpty()) pairingPermissions.launch(missing.toTypedArray())
        else if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        } else TelosAdbPairingService.start(context, automatic)
    }

    LaunchedEffect(Unit) {
        TelosShizukuHost.refresh(context)
    }

    val running = host.running

    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Hero Status Card with Single Primary Start/Stop Control
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (running) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (running) stringResource(I18nR.string.telos_shizuku_running)
                                else stringResource(I18nR.string.telos_shizuku_stopped),
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (running) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(
                                    I18nR.string.telos_shizuku_status,
                                    if (running) stringResource(I18nR.string.security_hub_active)
                                    else stringResource(I18nR.string.security_hub_inactive),
                                    host.uid,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (running) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            painter = painterResource(
                                if (running) BaseR.drawable.task_alt_24px else BaseR.drawable.warning_24px
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                    }

                    // Unified Start/Stop Primary Control Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (running) {
                            // Primary Stop button
                            Button(
                                onClick = {
                                    scope.launch {
                                        TelosShizukuWatchdog.stop(context)
                                        TelosShizukuHost.stop()
                                        TelosShizukuHost.refresh(context)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(I18nR.string.telos_shizuku_card_stop))
                            }
                            // Secondary Restart button
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        TelosShizukuWatchdog.stop(context)
                                        TelosShizukuHost.stop()
                                        val r = TelosShizukuHost.start(context, TelosShizukuHost.StartMethod.Auto)
                                        log = r.getOrElse { it.message.orEmpty() }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(I18nR.string.telos_shizuku_card_restart))
                            }
                        } else {
                            // Primary Start button
                            Button(
                                onClick = {
                                    scope.launch {
                                        val r = TelosShizukuHost.start(context, TelosShizukuHost.StartMethod.Auto)
                                        log = r.getOrElse { it.message.orEmpty() }
                                        if (auto) TelosShizukuWatchdog.start(context)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(I18nR.string.telos_shizuku_card_start))
                            }
                            // Secondary Root button
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val result = TelosShizukuHost.start(context, TelosShizukuHost.StartMethod.Root)
                                        log = result.getOrElse { it.message.orEmpty() }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(I18nR.string.telos_shizuku_start_root))
                            }
                        }
                    }
                }
            }
        }

        // 2. Info Banner Explaining Shizuku Capabilities
        item {
            Banner(
                text = stringResource(I18nR.string.telos_shizuku_autostart_summary),
                icon = BaseR.drawable.info_24px,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        // 3. User-Friendly Step-by-Step Activation Guide
        item {
            PreferenceCategory(title = stringResource(I18nR.string.telos_shizuku_howto_title)) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(I18nR.string.telos_shizuku_howto_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Method 1: Wireless ADB Action Buttons
                    Text(
                        text = stringResource(I18nR.string.telos_shizuku_wireless),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = { beginPairing(true) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(I18nR.string.telos_shizuku_pair_start))
                        }
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
                        ) {
                            Text(stringResource(I18nR.string.telos_shizuku_enable_a11y))
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedButton(
                            onClick = { TelosAdbPairingService.openWirelessDebugging(context) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(I18nR.string.telos_shizuku_wireless))
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(I18nR.string.telos_shizuku_open_devopts))
                        }
                    }

                    OutlinedButton(
                        onClick = { beginPairing(false) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(I18nR.string.telos_shizuku_pair_notif_enter))
                    }

                    // Pairing Phase Feedback
                    val phase = host.pairing.phase
                    if (phase == TelosShizukuHost.PairingPhase.Searching ||
                        phase == TelosShizukuHost.PairingPhase.WaitingForCode ||
                        phase == TelosShizukuHost.PairingPhase.Working
                    ) {
                        Text(
                            text = when (phase) {
                                TelosShizukuHost.PairingPhase.Searching -> stringResource(I18nR.string.telos_shizuku_pair_searching)
                                TelosShizukuHost.PairingPhase.Working -> stringResource(I18nR.string.telos_shizuku_pair_working)
                                else -> stringResource(I18nR.string.telos_shizuku_pair_waiting)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        OutlinedButton(
                            onClick = { context.startService(TelosAdbPairingService.stopIntent(context)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(I18nR.string.telos_shizuku_pair_stop))
                        }
                    }

                    if (host.pairing.phase == TelosShizukuHost.PairingPhase.Done) {
                        Text(
                            text = stringResource(I18nR.string.telos_shizuku_pair_ok),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    if (host.pairing.phase == TelosShizukuHost.PairingPhase.Failed) {
                        Text(
                            text = stringResource(I18nR.string.telos_shizuku_pair_fail),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    // Computer ADB Action
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(I18nR.string.telos_shizuku_usb),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    OutlinedButton(
                        onClick = {
                            val cmd = host.pcAdbCommand.ifBlank { TelosShizukuHost.pcAdbCommand(context) }
                            context.getSystemService(ClipboardManager::class.java)
                                ?.setPrimaryClip(ClipData.newPlainText("adb", cmd))
                            Toast.makeText(context, context.getString(I18nR.string.telos_shizuku_copied), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(I18nR.string.telos_shizuku_copy_adb))
                    }

                    if (log.isNotBlank()) {
                        Text(log, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // 4. Server Preferences & Watchdog Configuration
        item {
            PreferenceCategory(title = stringResource(I18nR.string.telos_shizuku_settings)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.settings_shizuku_autostart),
                    summary = stringResource(I18nR.string.startup_shizuku_help),
                    value = auto,
                    onValueChanged = { enabled ->
                        scope.launch {
                            ui.setShizukuAutoStartAndWait(enabled)
                            if (enabled) TelosShizukuWatchdog.start(context) else TelosShizukuWatchdog.stop(context)
                        }
                    },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.startup_wireless_off),
                    value = disableWireless,
                    onValueChanged = ui::setShizukuDisableWirelessAfterStart,
                )
            }
        }

        // 5. Startup Scripts
        item { StartupScriptsPreference() }

        // 6. Authorized Applications Panel
        item {
            PreferenceCategory(title = stringResource(I18nR.string.security_hub_tab_shizuku)) {
                TelosShizukuAppsPanel()
            }
        }

        // 7. Developer Shell & Diagnostics (Labs)
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
                    }) {
                        Text(stringResource(I18nR.string.telos_shizuku_run))
                    }
                    if (shellOut.isNotBlank()) {
                        Text(
                            text = shellOut,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
