package de.mm20.launcher2.ui.network

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.AdaptiveNavigationBar
import de.mm20.launcher2.ui.component.AdaptiveNavigationItem
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.network.firewall.NetworkFirewallRoute
import de.mm20.launcher2.ui.network.logs.NetworkLogsRoute

internal enum class NetworkSection { Home, Stats, Logs, Apps, Settings }

/** Shared Rethink-style destinations retain Telos typography, foregrounds and natural label heights. */
@Composable
internal fun NetworkNavigationBar(selected: NetworkSection) {
    val backStack = LocalBackStack.current
    val entries = listOf(
        Triple(NetworkSection.Home, R.string.net_hub_title, de.mm20.launcher2.base.R.drawable.ic_glyph_network),
        Triple(NetworkSection.Stats, R.string.netstats_title, de.mm20.launcher2.base.R.drawable.bar_chart_24px),
        Triple(NetworkSection.Logs, R.string.net_hub_logs, de.mm20.launcher2.base.R.drawable.manage_search_24px),
        Triple(NetworkSection.Apps, R.string.netfw_l_apps, de.mm20.launcher2.base.R.drawable.apps_24px),
        Triple(NetworkSection.Settings, R.string.net_hub_settings, de.mm20.launcher2.base.R.drawable.settings_24px),
    )
    AdaptiveNavigationBar {
        entries.forEach { (section, title, glyph) ->
            AdaptiveNavigationItem(selected = section == selected, onClick = {
                if (section != selected) {
                    val route = when (section) {
                        NetworkSection.Home -> NetworkRoute
                        NetworkSection.Stats -> NetworkStatsRoute
                        NetworkSection.Logs -> NetworkLogsRoute
                        NetworkSection.Apps -> NetworkFirewallRoute
                        NetworkSection.Settings -> NetworkSettingsRoute
                    }
                    // Reuse the existing root when present rather than accumulating repeated home routes.
                    if (NetworkRoute in backStack) {
                        while (backStack.lastOrNull() != NetworkRoute) backStack.removeLastOrNull()
                        if (section != NetworkSection.Home) backStack.add(route)
                    } else {
                        if (backStack.lastOrNull() in listOf(NetworkStatsRoute, NetworkLogsRoute, NetworkFirewallRoute, NetworkSettingsRoute)) backStack.removeLastOrNull()
                        backStack.add(route)
                    }
                }
            }, icon = { Icon(painterResource(glyph), null) }, label = { Text(stringResource(title)) })
        }
    }
}
