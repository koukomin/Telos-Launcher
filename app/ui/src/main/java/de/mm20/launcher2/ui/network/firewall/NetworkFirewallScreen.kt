package de.mm20.launcher2.ui.network.firewall

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.network.api.AppDirectory
import de.mm20.launcher2.network.api.AppEntry
import de.mm20.launcher2.network.api.AppRule
import de.mm20.launcher2.network.api.FirewallController
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

private typealias IconsNetworkFirewallScreen = de.mm20.launcher2.base.R.drawable

@Serializable
data object NetworkFirewallRoute : NavKey

private const val FILTER_ALL = 0
private const val FILTER_USER = 1
private const val FILTER_SYSTEM = 2
private const val FILTER_RULES = 3

/** Per app firewall: switches for every connection type, bulk actions, and the way to the rule pages. */
@Composable
fun NetworkFirewallScreen() {
    val fw: FirewallController = koinInject()
    val directory: AppDirectory = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val backStack = LocalBackStack.current

    val apps by directory.apps.collectAsState()
    val rules by fw.appRules.collectAsState()
    val temp by fw.tempAllowed.collectAsState()
    val allowedNew by fw.allowedNewApps.collectAsState()
    val universal by fw.universalRules.collectAsState()
    val detection by fw.backgroundDetection.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
    var expanded by rememberSaveable { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }

    // the usage access permission is granted in the Android settings: look again when coming back
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) fw.refreshBackgroundDetection()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val shown = remember(apps, rules, query, filter) {
        val q = query.trim().lowercase()
        apps.filter { app ->
            app.hasInternet &&
                (q.isEmpty() || app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q)) &&
                when (filter) {
                    FILTER_USER -> !app.isSystem
                    FILTER_SYSTEM -> app.isSystem
                    FILTER_RULES -> rules.containsKey(app.appId)
                    else -> true
                }
        }
    }

    fun bulk(change: (AppRule) -> AppRule) {
        scope.launch {
            fw.setAppRules(shown.map { change(rules[it.appId] ?: AppRule(it.appId)) })
            Toast.makeText(context, context.getString(R.string.netfw_bulk_done, shown.size), Toast.LENGTH_SHORT).show()
        }
    }

    PreferenceScreen(
        title = { Text(stringResource(R.string.netfw_title)) },
        topBarActions = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(IconsNetworkFirewallScreen.more_vert_24px), contentDescription = stringResource(R.string.hc_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Text(
                        stringResource(R.string.netfw_bulk),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.netfw_bulk_block_all)) }, onClick = { menu = false; bulk { it.copy(blockAll = true) } })
                    DropdownMenuItem(text = { Text(stringResource(R.string.netfw_bulk_allow_all)) }, onClick = { menu = false; bulk { it.copy(blockAll = false) } })
                    DropdownMenuItem(text = { Text(stringResource(R.string.netfw_bulk_block_wifi)) }, onClick = { menu = false; bulk { it.copy(blockWifi = true) } })
                    DropdownMenuItem(text = { Text(stringResource(R.string.netfw_bulk_block_mobile)) }, onClick = { menu = false; bulk { it.copy(blockMobile = true) } })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.netfw_bulk_reset)) },
                        onClick = { menu = false; bulk { AppRule(it.appId) } },
                    )
                }
            }
        },
    ) {
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.netfw_universal_rules),
                    summary = stringResource(R.string.netfw_universal_rules_summary),
                    icon = IconsNetworkFirewallScreen.rule_settings_24px,
                    onClick = { backStack.add(NetworkUniversalRulesRoute) },
                )
                Preference(
                    title = stringResource(R.string.netfw_custom_rules),
                    summary = stringResource(R.string.netfw_custom_rules_summary),
                    icon = IconsNetworkFirewallScreen.tune_24px,
                    onClick = { backStack.add(NetworkCustomRulesRoute) },
                )
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.netfw_search_apps)) },
                leadingIcon = { Icon(painterResource(IconsNetworkFirewallScreen.search_24px), contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    FILTER_ALL to R.string.netfw_filter_all,
                    FILTER_USER to R.string.netfw_filter_user,
                    FILTER_SYSTEM to R.string.netfw_filter_system,
                    FILTER_RULES to R.string.netfw_filter_rules,
                ).forEach { (id, label) ->
                    FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(stringResource(label)) })
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.netfw_no_apps),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(shown.size, key = { shown[it].appId }) { index ->
            val app = shown[index]
            val rule = rules[app.appId] ?: AppRule(app.appId)
            val newBlocked = universal.blockNewApps && app.appId !in allowedNew && fw.isNewAppBlocked(app.appId)
            AppRuleRow(
                app = app,
                rule = rule,
                tempUntilMs = temp[app.appId]?.takeIf { it > System.currentTimeMillis() },
                newAppBlocked = newBlocked,
                backgroundDetection = detection,
                expanded = expanded == app.appId,
                onToggleExpanded = { expanded = if (expanded == app.appId) null else app.appId },
                onRule = { scope.launch { fw.setAppRule(it) } },
                onTempAllow = { ms -> scope.launch { fw.allowTemporarily(app.appId, ms) } },
                onCancelTemp = { scope.launch { fw.cancelTemporaryAllow(app.appId) } },
                onAllowNew = { scope.launch { fw.allowNewApp(app.appId) } },
                onGrantUsageAccess = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        // no such settings screen on this device
                    }
                },
            )
        }
    }
}

@Composable
private fun AppRuleRow(
    app: AppEntry,
    rule: AppRule,
    tempUntilMs: Long?,
    newAppBlocked: Boolean,
    backgroundDetection: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRule: (AppRule) -> Unit,
    onTempAllow: (Long) -> Unit,
    onCancelTemp: () -> Unit,
    onAllowNew: () -> Unit,
    onGrantUsageAccess: () -> Unit,
) {
    val context = LocalContext.current
    val blockedTypes = buildList {
        if (rule.blockWifi) add(stringResource(R.string.netfw_t_wifi))
        if (rule.blockMobile) add(stringResource(R.string.netfw_t_mobile))
        if (rule.blockRoaming) add(stringResource(R.string.netfw_t_roaming))
        if (rule.blockLan) add(stringResource(R.string.netfw_t_lan))
        if (rule.blockVpn) add(stringResource(R.string.netfw_t_vpn))
        if (rule.blockBackground) add(stringResource(R.string.netfw_t_background))
        if (rule.blockScreenOff) add(stringResource(R.string.netfw_t_screen_off))
    }
    val summary = when {
        tempUntilMs != null -> stringResource(R.string.netfw_temp_allowed_until, formatClock(context, tempUntilMs))
        rule.blockAll -> stringResource(R.string.netfw_blocked)
        newAppBlocked -> stringResource(R.string.netfw_new_app_blocked)
        blockedTypes.isNotEmpty() -> stringResource(R.string.netfw_summary_blocked_on, blockedTypes.joinToString(", "))
        else -> stringResource(R.string.netfw_no_rules)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceBright),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Preference(
            title = { Text(app.label, maxLines = 1) },
            summary = { Text(summary, maxLines = 2) },
            icon = { NetAppIcon(app.packageName) },
            onClick = onToggleExpanded,
            controls = {
                Switch(checked = rule.blockAll, onCheckedChange = { onRule(rule.copy(blockAll = it)) })
            },
        )
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (newAppBlocked) {
                    Preference(
                        title = stringResource(R.string.netfw_new_app_allow),
                        onClick = onAllowNew,
                    )
                }
                SwitchPreference(title = stringResource(R.string.netfw_block_wifi), icon = IconsNetworkFirewallScreen.wifi_24px, value = rule.blockWifi, onValueChanged = { onRule(rule.copy(blockWifi = it)) })
                SwitchPreference(title = stringResource(R.string.netfw_block_mobile), icon = IconsNetworkFirewallScreen.signal_cellular_alt_24px, value = rule.blockMobile, onValueChanged = { onRule(rule.copy(blockMobile = it)) })
                SwitchPreference(title = stringResource(R.string.netfw_block_roaming), icon = IconsNetworkFirewallScreen.public_24px, value = rule.blockRoaming, onValueChanged = { onRule(rule.copy(blockRoaming = it)) })
                SwitchPreference(title = stringResource(R.string.netfw_block_lan), icon = IconsNetworkFirewallScreen.lan_24px, value = rule.blockLan, onValueChanged = { onRule(rule.copy(blockLan = it)) })
                SwitchPreference(title = stringResource(R.string.netfw_block_vpn), icon = IconsNetworkFirewallScreen.lock_24px, value = rule.blockVpn, onValueChanged = { onRule(rule.copy(blockVpn = it)) })
                SwitchPreference(title = stringResource(R.string.netfw_block_background), icon = IconsNetworkFirewallScreen.pause_24px, value = rule.blockBackground, onValueChanged = { onRule(rule.copy(blockBackground = it)) })
                SwitchPreference(title = stringResource(R.string.netfw_block_screen_off), icon = IconsNetworkFirewallScreen.schedule_24px, value = rule.blockScreenOff, onValueChanged = { onRule(rule.copy(blockScreenOff = it)) })
                if (!backgroundDetection && rule.blockBackground) {
                    Preference(
                        title = stringResource(R.string.netfw_usage_needed),
                        controls = { TextButton(onClick = onGrantUsageAccess) { Text(stringResource(R.string.netfw_usage_grant)) } },
                    )
                }
                SwitchPreference(title = stringResource(R.string.netfw_ignore_universal), value = rule.ignoreUniversalRules, onValueChanged = { onRule(rule.copy(ignoreUniversalRules = it)) })
                SwitchPreference(title = stringResource(R.string.netfw_bypass_firewall), value = rule.bypassFirewall, onValueChanged = { onRule(rule.copy(bypassFirewall = it)) })
                SwitchPreference(title = stringResource(R.string.netfw_exclude_vpn), value = rule.excludeFromVpn, onValueChanged = { onRule(rule.copy(excludeFromVpn = it)) })
                if (tempUntilMs != null) {
                    Preference(
                        title = stringResource(R.string.netfw_temp_allowed_until, formatClock(context, tempUntilMs)),
                        controls = { TextButton(onClick = onCancelTemp) { Text(stringResource(R.string.netfw_temp_cancel)) } },
                    )
                } else {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        Preference(
                            title = stringResource(R.string.netfw_temp_allow),
                            icon = IconsNetworkFirewallScreen.timer_24px,
                            onClick = { open = true },
                        )
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            listOf(
                                R.string.netfw_temp_15m to 15L * 60_000,
                                R.string.netfw_temp_1h to 60L * 60_000,
                                R.string.netfw_temp_8h to 8L * 60 * 60_000,
                            ).forEach { (label, ms) ->
                                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { open = false; onTempAllow(ms) })
                            }
                        }
                    }
                }
            }
        }
    }
}
