package de.mm20.launcher2.ui.settings.security

import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.appmanagement.clipboard.ClipboardScrubMode
import de.mm20.launcher2.appmanagement.clipboard.TelosClipboardShield
import de.mm20.launcher2.appmanagement.shizuku.TelosShizukuManager
import de.mm20.launcher2.appmanagement.stealth.TelosDevOptsCloak
import de.mm20.launcher2.appmanagement.stealth.TelosStealthBlacklist
import de.mm20.launcher2.packages.TelosIconCache
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object TelosSecurityHubRoute : NavKey

@Composable
fun TelosSecurityHubScreen() {
    PreferenceScreen(title = stringResource(I18nR.string.security_hub_title)) {
        TelosSecurityHubItems()
    }
}

fun androidx.compose.foundation.lazy.LazyListScope.TelosSecurityHubItems() {
    item(key = "security-hub") {
        TelosSecurityHubBody()
    }
}

@Composable
fun TelosSecurityHubBody() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine by TelosShizukuManager.status.collectAsStateWithLifecycle()
    val cloak by TelosDevOptsCloak.status.collectAsStateWithLifecycle()
    val vault by TelosClipboardShield.vaultEntry.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        TelosShizukuManager.refreshStatus(context)
        TelosDevOptsCloak.refreshStatus(context)
        TelosStealthBlacklist.refresh(context)
        TelosShizukuManager.scanClientApps(context)
    }

    Column(Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(I18nR.string.security_hub_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        I18nR.string.security_hub_engine_status,
                        if (engine.binderAlive) stringResource(I18nR.string.security_hub_active) else stringResource(I18nR.string.security_hub_inactive),
                        engine.uid.takeIf { it >= 0 } ?: 0,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(I18nR.string.security_hub_cloak_status),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(I18nR.string.security_hub_selinux, engine.seLinuxContext.ifBlank { "—" }),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (engine.externalInstalled) {
                    SwitchPreference(
                        title = stringResource(I18nR.string.security_hub_freeze_shizuku),
                        summary = stringResource(I18nR.string.security_hub_freeze_shizuku_summary),
                        value = !engine.externalEnabled,
                        onValueChanged = { hide ->
                            scope.launch {
                                if (hide) TelosShizukuManager.freezeExternalShizuku(context)
                                else TelosShizukuManager.setExternalShizukuEnabled(context, true)
                            }
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            TelosShizukuManager.grantPrivilegedPerms(context, context.packageName, true)
                        }
                    }) {
                        Text(stringResource(I18nR.string.security_hub_grant_self))
                    }
                    if (vault != null) {
                        FilledTonalButton(onClick = { TelosClipboardShield.restoreVault(context) }) {
                            Text(stringResource(I18nR.string.security_hub_restore_clip))
                        }
                    }
                }
            }
        }

        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(I18nR.string.security_hub_tab_shizuku)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(I18nR.string.security_hub_tab_cloak)) })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(I18nR.string.security_hub_tab_clipboard)) })
        }

        when (tab) {
            0 -> ShizukuAppsTab()
            1 -> BankingCloakTab()
            else -> ClipboardShieldTab()
        }
    }
}

@Composable
private fun ShizukuAppsTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clients by TelosShizukuManager.clients.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(clients, query) {
        val t = query.trim()
        if (t.isEmpty()) clients
        else clients.filter { it.label.contains(t, true) || it.packageName.contains(t, true) }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
            label = { Text(stringResource(I18nR.string.security_hub_search_clients)) },
        )
        if (visible.isEmpty()) {
            Text(
                stringResource(I18nR.string.security_hub_no_clients),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        visible.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HubIcon(row.packageName)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(row.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(
                            I18nR.string.security_hub_client_meta,
                            row.packageName,
                            if (row.granted) stringResource(I18nR.string.security_hub_allow) else stringResource(I18nR.string.security_hub_deny),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = row.granted,
                    onCheckedChange = { on ->
                        scope.launch { TelosShizukuManager.setClientAccess(context, row.packageName, on) }
                    },
                )
            }
            Row(Modifier.padding(start = 68.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val launch = context.packageManager.getLaunchIntentForPackage(row.packageName)
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(launch) }
                    }
                }) { Text(stringResource(I18nR.string.menu_launch)) }
                TextButton(onClick = {
                    scope.launch { TelosShizukuManager.forceStop(row.packageName) }
                }) { Text(stringResource(I18nR.string.security_hub_force_stop)) }
            }
        }
    }
}

@Composable
private fun BankingCloakTab() {
    val context = LocalContext.current
    val rows by TelosStealthBlacklist.rows.collectAsStateWithLifecycle()
    val newly by TelosStealthBlacklist.newlyDetected.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(rows, query) {
        val t = query.trim()
        if (t.isEmpty()) rows
        else rows.filter { it.label.contains(t, true) || it.packageName.contains(t, true) }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        SwitchPreference(
            title = stringResource(I18nR.string.stealth_cloak_permanent),
            summary = stringResource(I18nR.string.stealth_cloak_permanent_summary),
            icon = BaseR.drawable.lock_24px,
            value = TelosDevOptsCloak.status.value.permanent,
            onValueChanged = { TelosDevOptsCloak.setPermanent(context, it) },
        )
        SwitchPreference(
            title = stringResource(I18nR.string.stealth_cloak_hide_shizuku_auto),
            summary = stringResource(I18nR.string.stealth_cloak_hide_shizuku_auto_summary),
            value = TelosDevOptsCloak.status.value.hideShizuku,
            onValueChanged = { TelosDevOptsCloak.setHideShizukuOnCloak(context, it) },
        )
        if (newly.isNotEmpty()) {
            Text(
                stringResource(I18nR.string.stealth_cloak_new_detected, newly.size),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
            label = { Text(stringResource(I18nR.string.stealth_cloak_search)) },
        )
        visible.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HubIcon(row.packageName)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(row.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(row.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = row.protected,
                    onCheckedChange = { TelosStealthBlacklist.setProtected(context, row.packageName, it) },
                )
            }
        }
    }
}

@Composable
private fun ClipboardShieldTab() {
    val context = LocalContext.current
    val mode by TelosClipboardShield.mode.collectAsStateWithLifecycle()
    val autoClear by TelosClipboardShield.autoClear.collectAsStateWithLifecycle()
    val autoRestore by TelosClipboardShield.autoRestore.collectAsStateWithLifecycle()
    val appops by TelosClipboardShield.appOpsLockdown.collectAsStateWithLifecycle()
    val blocked by TelosClipboardShield.blockedPackages.collectAsStateWithLifecycle()
    val rows by TelosStealthBlacklist.rows.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(rows, query) {
        val t = query.trim()
        if (t.isEmpty()) rows
        else rows.filter { it.label.contains(t, true) || it.packageName.contains(t, true) }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        ListPreference(
            title = stringResource(I18nR.string.security_hub_scrub_mode),
            items = listOf(
                stringResource(I18nR.string.security_hub_scrub_off) to ClipboardScrubMode.Off,
                stringResource(I18nR.string.security_hub_scrub_smart) to ClipboardScrubMode.Smart,
                stringResource(I18nR.string.security_hub_scrub_strict) to ClipboardScrubMode.Strict,
            ),
            value = mode,
            onValueChanged = { TelosClipboardShield.setMode(context, it) },
        )
        SwitchPreference(
            title = stringResource(I18nR.string.security_hub_auto_clear),
            summary = stringResource(I18nR.string.security_hub_auto_clear_summary),
            value = autoClear,
            onValueChanged = { TelosClipboardShield.setAutoClear(context, it) },
        )
        SwitchPreference(
            title = stringResource(I18nR.string.security_hub_auto_restore),
            summary = stringResource(I18nR.string.security_hub_auto_restore_summary),
            value = autoRestore,
            onValueChanged = { TelosClipboardShield.setAutoRestore(context, it) },
        )
        SwitchPreference(
            title = stringResource(I18nR.string.security_hub_appops),
            summary = stringResource(I18nR.string.security_hub_appops_summary),
            value = appops,
            onValueChanged = { TelosClipboardShield.setAppOpsLockdown(context, it) },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
            label = { Text(stringResource(I18nR.string.security_hub_search_blocked)) },
        )
        visible.forEach { row ->
            val on = row.packageName in blocked
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HubIcon(row.packageName)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(row.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(I18nR.string.security_hub_read_clipboard_rule),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = on,
                    onCheckedChange = { TelosClipboardShield.setBlocked(context, row.packageName, it) },
                )
            }
        }
    }
}

@Composable
private fun HubIcon(packageName: String) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf(TelosIconCache.getCached(packageName)) }
    LaunchedEffect(packageName) {
        if (bitmap == null) bitmap = TelosIconCache.getOrLoad(context, packageName)
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(40.dp))
    }
}
