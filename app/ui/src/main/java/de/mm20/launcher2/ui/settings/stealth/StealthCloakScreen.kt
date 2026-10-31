package de.mm20.launcher2.ui.settings.stealth

import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.appmanagement.stealth.TelosDevOptsCloak
import de.mm20.launcher2.appmanagement.stealth.TelosPrivilegedDaemon
import de.mm20.launcher2.appmanagement.stealth.TelosStealthBlacklist
import de.mm20.launcher2.packages.TelosIconCache
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object StealthCloakRoute : NavKey

@Composable
fun StealthCloakScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by TelosDevOptsCloak.status.collectAsStateWithLifecycle()
    val rows by TelosStealthBlacklist.rows.collectAsStateWithLifecycle()
    val newly by TelosStealthBlacklist.newlyDetected.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        TelosStealthBlacklist.refresh(context)
        TelosDevOptsCloak.refreshStatus(context)
    }

    val visible = remember(rows, query) {
        val term = query.trim()
        if (term.isEmpty()) rows
        else rows.filter {
            it.label.contains(term, true) || it.packageName.contains(term, true)
        }
    }

    PreferenceScreen(
        title = stringResource(I18nR.string.stealth_cloak_title),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (status.writeSecureSettings) {
                            stringResource(I18nR.string.stealth_cloak_wss_granted)
                        } else {
                            stringResource(I18nR.string.stealth_cloak_wss_missing)
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(
                            I18nR.string.stealth_cloak_devopts_state,
                            visibilityLabel(status.developmentEnabled),
                            visibilityLabel(status.adbEnabled),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        stringResource(
                            I18nR.string.stealth_cloak_mode,
                            status.mode.name,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (status.externalShizukuInstalled) {
                        Text(
                            stringResource(I18nR.string.stealth_cloak_shizuku_installed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch { TelosPrivilegedDaemon.hideExternalShizuku(context) }
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(stringResource(I18nR.string.stealth_cloak_hide_shizuku))
                        }
                    }
                }
            }
        }
        item {
            SwitchPreference(
                title = stringResource(I18nR.string.stealth_cloak_permanent),
                summary = stringResource(I18nR.string.stealth_cloak_permanent_summary),
                icon = BaseR.drawable.lock_24px,
                value = status.permanent,
                onValueChanged = { TelosDevOptsCloak.setPermanent(context, it) },
            )
            SwitchPreference(
                title = stringResource(I18nR.string.stealth_cloak_hide_shizuku_auto),
                summary = stringResource(I18nR.string.stealth_cloak_hide_shizuku_auto_summary),
                value = status.hideShizuku,
                onValueChanged = { TelosDevOptsCloak.setHideShizukuOnCloak(context, it) },
            )
        }
        if (newly.isNotEmpty()) {
            item {
                Text(
                    stringResource(I18nR.string.stealth_cloak_new_detected, newly.size),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        item {
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text(stringResource(I18nR.string.stealth_cloak_search)) },
            )
        }
        items(visible.size, key = { visible[it].packageName }) { index ->
            val row = visible[index]
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StealthRowIcon(row.packageName)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(row.packageName)
                            if (row.seeded || row.heuristic) {
                                append(" · ")
                                append(stringResource(I18nR.string.stealth_cloak_protected_badge))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = row.protected,
                    onCheckedChange = { TelosStealthBlacklist.setProtected(context, row.packageName, it) },
                )
            }
        }
    }
}

@Composable
private fun StealthRowIcon(packageName: String) {
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

private fun visibilityLabel(value: Int): String = when (value) {
    0 -> "Hidden (0)"
    1 -> "Visible (1)"
    else -> "?"
}
