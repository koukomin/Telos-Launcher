package de.mm20.launcher2.ui.network.wireguard

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.network.api.WgInterface
import de.mm20.launcher2.network.api.WgPeer
import de.mm20.launcher2.network.api.WireguardConfig
import de.mm20.launcher2.network.api.WireguardController
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.launch

private class PeerDraft(p: WgPeer) {
    var publicKey by mutableStateOf(p.publicKey)
    var preshared by mutableStateOf(p.presharedKey.orEmpty())
    var endpoint by mutableStateOf(p.endpoint.orEmpty())
    var allowed by mutableStateOf(p.allowedIps.joinToString(", "))
    var keepalive by mutableStateOf(if (p.persistentKeepalive > 0) p.persistentKeepalive.toString() else "")

    fun toPeer() = WgPeer(
        publicKey = publicKey,
        presharedKey = preshared.ifBlank { null },
        allowedIps = list(allowed),
        endpoint = endpoint.ifBlank { null },
        persistentKeepalive = keepalive.toIntOrNull() ?: 0,
    )
}

private fun list(s: String) = s.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }

/** Creates or edits a tunnel. Saves through [WireguardController.add] / [WireguardController.update]. */
@Composable
internal fun WireguardEditor(initial: WireguardConfig, isNew: Boolean, wg: WireguardController, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var privateKey by remember { mutableStateOf(initial.wgInterface.privateKey) }
    var publicKey by remember { mutableStateOf<String?>(null) }
    var addresses by remember { mutableStateOf(initial.wgInterface.addresses.joinToString(", ")) }
    var dns by remember { mutableStateOf(initial.wgInterface.dns.joinToString(", ")) }
    var mtu by remember { mutableStateOf(if (initial.wgInterface.mtu > 0) initial.wgInterface.mtu.toString() else "") }
    var mobileOnly by remember { mutableStateOf(initial.mobileOnly) }
    var lockdown by remember { mutableStateOf(initial.lockdown) }
    val peers = remember { mutableStateListOf<PeerDraft>().also { l -> initial.peers.forEach { l.add(PeerDraft(it)) } } }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(privateKey) { publicKey = wg.publicKeyOf(privateKey) }

    fun build() = initial.copy(
        name = name,
        wgInterface = WgInterface(
            privateKey = privateKey,
            addresses = list(addresses),
            dns = list(dns),
            mtu = mtu.toIntOrNull() ?: 0,
            listenPort = initial.wgInterface.listenPort,
        ),
        peers = peers.map { it.toPeer() },
        mobileOnly = mobileOnly,
        lockdown = lockdown,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.nwg_new_title else R.string.nwg_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(stringResource(R.string.nwg_name), name) { name = it }
                Field(stringResource(R.string.nwg_private_key), privateKey, password = true) { privateKey = it.trim() }
                publicKey?.let {
                    Text(stringResource(R.string.nwg_public_key, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    scope.launch { wg.generatePrivateKey().onSuccess { privateKey = it } }
                }) { Text(stringResource(R.string.nwg_generate)) }
                Field(stringResource(R.string.nwg_addresses), addresses, "10.2.0.2/32") { addresses = it }
                Field(stringResource(R.string.nwg_dns), dns, "1.1.1.1") { dns = it }
                Field(stringResource(R.string.nwg_mtu), mtu, number = true) { mtu = it.filter(Char::isDigit) }
                peers.forEachIndexed { index, p ->
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    Text(stringResource(R.string.nwg_peer, index + 1), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Field(stringResource(R.string.nwg_peer_public_key), p.publicKey) { p.publicKey = it.trim() }
                    Field(stringResource(R.string.nwg_preshared), p.preshared, password = true) { p.preshared = it.trim() }
                    TextButton(onClick = { p.preshared = wg.generatePresharedKey() }) { Text(stringResource(R.string.nwg_generate)) }
                    Field(stringResource(R.string.nwg_endpoint), p.endpoint, "vpn.example.com:51820") { p.endpoint = it }
                    Field(stringResource(R.string.nwg_allowed_ips), p.allowed, "0.0.0.0/0, ::/0") { p.allowed = it }
                    Field(stringResource(R.string.nwg_keepalive), p.keepalive, number = true) { p.keepalive = it.filter(Char::isDigit) }
                    if (peers.size > 1) TextButton(onClick = { peers.removeAt(index) }) { Text(stringResource(R.string.nwg_remove_peer)) }
                }
                TextButton(onClick = {
                    peers.add(PeerDraft(WgPeer(publicKey = "", allowedIps = listOf("0.0.0.0/0", "::/0"), persistentKeepalive = 25)))
                }) { Text(stringResource(R.string.nwg_add_peer)) }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.nwg_mobile_only), Modifier.weight(1f))
                    Switch(checked = mobileOnly, onCheckedChange = { mobileOnly = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.nwg_lockdown), Modifier.weight(1f))
                    Switch(checked = lockdown, onCheckedChange = { lockdown = it })
                }
                if (!isNew) {
                    TextButton(onClick = {
                        wg.exportConf(initial.id)?.let { shareText(context, it) }
                    }) { Text(stringResource(R.string.nwg_share)) }
                    Text(stringResource(R.string.nwg_share_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val config = build()
                    val result = if (isNew) wg.add(config).map { } else wg.update(config)
                    result.fold(
                        onSuccess = { onDismiss() },
                        onFailure = { Toast.makeText(context, errorText(context, it), Toast.LENGTH_LONG).show() },
                    )
                }
            }) { Text(stringResource(R.string.nwg_save)) }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.nwg_delete), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.nwg_cancel)) }
            }
        },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.nwg_delete)) },
            text = { Text(initial.name) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { wg.remove(initial.id); onDismiss() }
                }) { Text(stringResource(R.string.nwg_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.nwg_cancel)) } },
        )
    }
}

@Composable
private fun Field(label: String, value: String, placeholder: String = "", password: Boolean = false, number: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
    )
}
