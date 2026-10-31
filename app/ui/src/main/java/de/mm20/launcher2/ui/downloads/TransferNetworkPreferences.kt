package de.mm20.launcher2.ui.downloads
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.comms.network.TransferNetworkGate
import de.mm20.launcher2.comms.network.TransferNetworkPolicy
import de.mm20.launcher2.ui.R

/** Torrent rules are shared by the torrent session in Downloads, Video and Stremio. */
@Composable
fun TransferNetworkPreferences(scope: String) {
    val context = LocalContext.current
    val gate = remember(context, scope) { TransferNetworkGate.get(context, scope) }
    val saved by gate.policy.collectAsStateWithLifecycle()
    val policy = saved
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(I18nR.string.dl_settings_rules), style = MaterialTheme.typography.titleSmall)
        @Composable fun Rule(label: Int, blocked: Boolean, change: (TransferNetworkPolicy, Boolean) -> TransferNetworkPolicy) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(label), Modifier.weight(1f).padding(end = 8.dp))
                Switch(checked = blocked, onCheckedChange = { gate.update(change(policy, it)) })
            }
        }
        Rule(I18nR.string.netfw_block_wifi, !policy.wifi) { p, blocked -> p.copy(wifi = !blocked) }
        Rule(I18nR.string.netfw_block_mobile, !policy.mobile) { p, blocked -> p.copy(mobile = !blocked) }
        Rule(I18nR.string.netfw_block_roaming, !policy.roaming) { p, blocked -> p.copy(roaming = !blocked) }
        Rule(I18nR.string.netfw_u_metered, !policy.metered) { p, blocked -> p.copy(metered = !blocked) }
        if (scope == "torrent") Text(stringResource(I18nR.string.dl_t_set_shared_note), style = MaterialTheme.typography.bodySmall)
    }
}
