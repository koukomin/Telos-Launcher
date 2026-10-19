package de.mm20.launcher2.ui.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.network.NetworkEngine
import de.mm20.launcher2.network.NetState
import de.mm20.launcher2.network.api.NetworkSettings
import de.mm20.launcher2.network.api.WireguardController
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Shared control: changes the actual engine routing, never just the displayed tunnel name. */
@Composable
fun NetworkRouteChooser() {
    val settings: NetworkSettings = koinInject()
    val wireguard: WireguardController = koinInject()
    val engine: NetworkEngine = koinInject()
    val config by settings.values.collectAsState()
    val tunnels by wireguard.configs.collectAsState()
    val selected by wireguard.systemDefault.collectAsState()
    val engineState by engine.state.collectAsState()
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val label = if (config.torSystemDefault) "Tor" else tunnels.firstOrNull { it.id == selected }?.name ?: stringResource(R.string.nwg_none)
    FilledTonalButton(onClick = { expanded = true }, enabled = engineState !is NetState.Starting, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Icon(painterResource(if (config.torSystemDefault) R.drawable.visibility_off_24px else R.drawable.lan_24px), null)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.nwg_system_default), style = MaterialTheme.typography.labelMedium)
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
        Icon(painterResource(R.drawable.arrow_drop_down_24px), null)
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false },
        title = { Text(stringResource(R.string.nwg_system_default)) },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.nwg_system_default_summary), style = MaterialTheme.typography.bodySmall)
            fun choose(tor: Boolean, id: Int?) {
                expanded = false
                scope.launch {
                    settings.update { it.copy(torSystemDefault = tor, torEnabled = tor || it.torEnabled,
                        torWasActive = tor || it.torWasActive) }
                    settings.flush()
                    if (id != null) wireguard.setEnabled(id, true)
                    wireguard.setSystemDefault(id)
                    engine.closeAllConnections()
                    if (engine.state.value is NetState.On) engine.restart()
                }
            }
            TextButton(onClick = { choose(false, null) }) { Text(stringResource(R.string.nwg_none)) }
            TextButton(onClick = { choose(true, null) }) {
                Icon(painterResource(R.drawable.visibility_off_24px), null)
                Spacer(Modifier.width(8.dp)); Text("Tor")
            }
            tunnels.forEach { tunnel -> TextButton(onClick = { choose(false, tunnel.id) }) {
                Icon(painterResource(R.drawable.lock_24px), null)
                Spacer(Modifier.width(8.dp)); Text(tunnel.name)
            } }
        } }, confirmButton = { TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.hc_cancel)) } })
}
