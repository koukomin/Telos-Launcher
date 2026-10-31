package de.mm20.launcher2.ui.settings.advanced
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.freeze.ShizukuSystemStatus
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.applock.AppLockSettingsRoute
import de.mm20.launcher2.ui.settings.art.ArtOptimizerRoute
import de.mm20.launcher2.ui.settings.backup.BackupSettingsRoute
import de.mm20.launcher2.ui.settings.contextprofiles.ContextProfilesSettingsRoute
import de.mm20.launcher2.ui.settings.debug.DebugSettingsRoute
import de.mm20.launcher2.ui.settings.performance.PerformanceSettingsRoute
import de.mm20.launcher2.ui.settings.plugins.PluginsSettingsRoute
import de.mm20.launcher2.ui.settings.workprofile.WorkProfileSettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object AdvancedSettingsRoute : NavKey

@Composable
fun AdvancedSettingsScreen() {
    val backStack = LocalBackStack.current
    val vm: AdvancedSettingsScreenVM = viewModel()
    val context = LocalContext.current

    val shizukuAutoStart by vm.shizukuAutoStart.collectAsStateWithLifecycle(false)
    val systemStatusState by vm.systemStatus.collectAsStateWithLifecycle()
    val status = systemStatusState

    var showDiagnosticDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshStatus(context)
    }

    PreferenceScreen(
        title = { Text(stringResource(I18nR.string.preference_screen_advanced)) },
    ) {
        item {
            PreferenceCategory(title = stringResource(I18nR.string.settings_system_automation)) {
                val currentStatus = status
                val isInstalled = currentStatus?.isShizukuInstalled == true
                val summaryText = if (isInstalled) {
                    val statusText = when {
                        currentStatus.hasWriteSecureSettings -> "Status: Ready (Permission Granted)"
                        currentStatus.isShizukuRunning -> "Status: Shizuku Active (Action Required: Grant Permission)"
                        else -> "Status: Action Required (Missing WRITE_SECURE_SETTINGS)"
                    }
                    "Automatically enables Android Wireless Debugging once Wi-Fi connects on boot and wakes the Shizuku daemon in the background without needing a PC.\n$statusText"
                } else {
                    "Requires Shizuku app to be installed. Tap here to find it in Telos Store."
                }

                if (isInstalled) {
                    SwitchPreference(
                        title = stringResource(I18nR.string.settings_shizuku_autostart),
                        summary = summaryText,
                        value = shizukuAutoStart,
                        onValueChanged = { enabled ->
                            vm.setShizukuAutoStart(
                                context = context,
                                enabled = enabled,
                                onVerified = {
                                    Toast.makeText(context, "Επιβεβαιώθηκε! Το Shizuku Auto-Start λειτουργεί κανονικά και ενεργοποιήθηκε.", Toast.LENGTH_LONG).show()
                                },
                                onFailed = {
                                    showDiagnosticDialog = true
                                }
                            )
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().clickable {
                            backStack.add(de.mm20.launcher2.ui.store.StoreDashboardRoute(initialQuery = "Shizuku"))
                        }
                    ) {
                        Preference(
                            title = stringResource(I18nR.string.settings_shizuku_autostart),
                            summary = summaryText,
                            enabled = false,
                        )
                    }
                }

                Preference(
                    icon = BaseR.drawable.settings_24px,
                    title = stringResource(I18nR.string.settings_shizuku_diagnostics),
                    summary = stringResource(I18nR.string.settings_shizuku_diagnostics_summary),
                    onClick = { showDiagnosticDialog = true }
                )
            }
        }

        item {
            GraphicsApiPreferenceCategory()
            de.mm20.launcher2.ui.system.SavedWifiPasswordsPreference()
        }

        item {
            PreferenceCategory {
                Preference(
                    icon = BaseR.drawable.speed_24px,
                    title = stringResource(id = I18nR.string.preference_screen_performance),
                    summary = stringResource(id = I18nR.string.preference_screen_performance_summary),
                    onClick = {
                        backStack.add(PerformanceSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.bolt_24px,
                    title = stringResource(id = I18nR.string.art_optimizer_title),
                    summary = stringResource(id = I18nR.string.art_optimizer_summary),
                    onClick = {
                        backStack.add(ArtOptimizerRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.dashboard_2_24px,
                    title = stringResource(id = I18nR.string.preference_screen_context_profiles),
                    summary = stringResource(id = I18nR.string.preference_screen_context_profiles_summary),
                    onClick = {
                        backStack.add(ContextProfilesSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.extension_24px,
                    title = stringResource(id = I18nR.string.preference_screen_plugins),
                    summary = stringResource(id = I18nR.string.preference_screen_plugins_summary),
                    onClick = {
                        backStack.add(PluginsSettingsRoute)
                    }
                )

                Preference(
                    icon = BaseR.drawable.lock_24px,
                    title = stringResource(id = I18nR.string.preference_screen_app_lock),
                    summary = stringResource(id = I18nR.string.preference_screen_app_lock_summary),
                    onClick = {
                        backStack.add(AppLockSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.enterprise_24px,
                    title = stringResource(id = I18nR.string.preference_screen_work_profile),
                    summary = stringResource(id = I18nR.string.preference_screen_work_profile_summary),
                    onClick = {
                        backStack.add(WorkProfileSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.settings_backup_restore_24px,
                    title = stringResource(id = I18nR.string.preference_screen_backup),
                    summary = stringResource(id = I18nR.string.preference_screen_backup_summary),
                    onClick = {
                        backStack.add(BackupSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.bug_report_24px,
                    title = stringResource(id = I18nR.string.preference_screen_debug),
                    summary = stringResource(id = I18nR.string.preference_screen_debug_summary),
                    onClick = {
                        backStack.add(DebugSettingsRoute)
                    }
                )
            }
        }
    }

    if (showDiagnosticDialog) {
        ShizukuDiagnosticDialog(
            status = status,
            vm = vm,
            onDismiss = {
                vm.clearError()
                showDiagnosticDialog = false
            }
        )
    }
}

@Composable
private fun ShizukuDiagnosticDialog(
    status: ShizukuSystemStatus?,
    vm: AdvancedSettingsScreenVM,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val adbCmd = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shizuku Auto-Start Diagnostics & Setup") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Live OS Compatibility Checklist:", fontWeight = FontWeight.Bold)

                ChecklistItem("Android 11+ (Wireless Debugging Support)", status?.isAndroid11OrHigher == true)
                ChecklistItem("Shizuku Installed (moe.shizuku.privileged.api)", status?.isShizukuInstalled == true)
                ChecklistItem("Developer Options Enabled", status?.isDeveloperOptionsEnabled == true)
                ChecklistItem("Wi-Fi Connected (Active Network)", status?.isWifiConnected == true)
                ChecklistItem("WRITE_SECURE_SETTINGS Granted", status?.hasWriteSecureSettings == true)
                ChecklistItem("Shizuku Daemon Running & Authorized", status?.isShizukuRunning == true && status.isShizukuAuthorized)

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                Text("Step 1 (Automatic without PC):", fontWeight = FontWeight.Bold)
                Text("Open the Shizuku app, start it once via Wireless Debugging, and tap 'Grant via Shizuku' below so the Launcher can configure itself automatically.", style = MaterialTheme.typography.bodySmall)

                Button(
                    onClick = {
                        vm.trySelfGrant(context) { ok ->
                            if (ok) {
                                Toast.makeText(context, "Permission WRITE_SECURE_SETTINGS granted successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Could not self-grant via Shizuku. Start Shizuku first.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant via Shizuku & Retry")
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                Text("Step 2 (Manual via ADB / Termux):", fontWeight = FontWeight.Bold)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(adbCmd, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ADB Command", adbCmd))
                                Toast.makeText(context, "ADB command copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Copy ADB Command")
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                Text("Step 3 (Preventing OS Background Killing):", fontWeight = FontWeight.Bold)
                Text("• Enable 'Start on boot (Wireless debugging)' inside Shizuku settings.", style = MaterialTheme.typography.bodySmall)
                Text("• Enable 'Disable adb authorization timeout' in Android Developer Options.", style = MaterialTheme.typography.bodySmall)
                Text("• Set Battery Optimization for Shizuku and Telos to 'Unrestricted'.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(I18nR.string.hc_close))
            }
        }
    )
}

@Composable
private fun ChecklistItem(label: String, passed: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(if (passed) "✅" else "❌")
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
