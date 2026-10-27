package de.mm20.launcher2.ui.network.firewall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.network.api.AppRule
import de.mm20.launcher2.ui.R

/** Rethink-style primary actions; columns adapt to font scale without reducing label text. */
@Composable
internal fun NetworkAppRuleGrid(rule: AppRule, allowExclusion: Boolean, onRule: (AppRule) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
        val columns = (maxWidth / (100 * density.fontScale.coerceAtLeast(1f)).dp).toInt().coerceIn(1, 3)
        val width = (maxWidth - (columns - 1) * 8.dp) / columns
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            @Composable fun Tile(label: Int, icon: Int, active: Boolean, enabled: Boolean = true, action: () -> Unit) {
                val colors = MaterialTheme.colorScheme
                val background = if (!enabled) colors.surfaceContainerHighest else if (active) colors.secondaryContainer else colors.surfaceContainerLow
                val foreground = if (!enabled) colors.onSurfaceVariant else if (active) colors.onSecondaryContainer else colors.onSurface
                Column(Modifier.width(width).heightIn(min = 112.dp).clip(MaterialTheme.shapes.large).background(background)
                    .toggleable(active, enabled = enabled, role = Role.Checkbox) { action() }.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(icon), null, Modifier.size(28.dp), tint = foreground)
                    Text(stringResource(label), color = foreground, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            val editable = !rule.blockAll && !rule.bypassFirewall && !rule.excludeFromVpn
            Tile(R.string.netstats_unmetered, R.drawable.wifi_24px, !rule.blockUnmetered, editable) { onRule(rule.copy(blockUnmetered = !rule.blockUnmetered)) }
            Tile(R.string.netstats_metered, R.drawable.signal_cellular_alt_24px, !rule.blockMetered, editable) { onRule(rule.copy(blockMetered = !rule.blockMetered)) }
            Tile(R.string.netfw_blocked, R.drawable.rule_settings_24px, rule.blockAll) { onRule(if (rule.blockAll) rule.copy(blockAll = false) else rule.copy(blockAll = true, bypassFirewall = false, excludeFromVpn = false)) }
            Tile(R.string.netfw_bypass_firewall, R.drawable.check_circle_24px, rule.bypassFirewall) { onRule(if (rule.bypassFirewall) rule.copy(bypassFirewall = false) else rule.copy(bypassFirewall = true, blockAll = false, excludeFromVpn = false)) }
            Tile(R.string.netfw_ignore_universal, R.drawable.public_24px, rule.ignoreUniversalRules) { onRule(rule.copy(ignoreUniversalRules = !rule.ignoreUniversalRules)) }
            Tile(R.string.netfw_exclude_vpn, R.drawable.close_24px, rule.excludeFromVpn, allowExclusion) { onRule(if (rule.excludeFromVpn) rule.copy(excludeFromVpn = false) else rule.copy(excludeFromVpn = true, blockAll = false, bypassFirewall = false)) }
        }
    }
}
