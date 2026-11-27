package de.mm20.launcher2.ui.network

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import de.mm20.launcher2.i18n.R
import de.mm20.launcher2.network.ddns.DynamicDnsClient
import de.mm20.launcher2.network.ddns.DynamicDnsProvider
import de.mm20.launcher2.ui.component.virtualAppPopup
import kotlinx.coroutines.launch

@Composable
internal fun DynamicDnsDialog(onDismiss: () -> Unit) {
    var provider by remember { mutableStateOf(DynamicDnsProvider.DuckDns) }
    var expanded by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var record by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<DynamicDnsClient.Result?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun detect(ipv6: Boolean) {
        busy = true
        result = null
        scope.launch {
            try {
                val detected = DynamicDnsClient.detectAddress(ipv6)
                if (detected != null) address = detected
                else result = DynamicDnsClient.Result.Unreachable
            } finally {
                busy = false
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = virtualAppPopup(),
        title = { Text(stringResource(R.string.ddns_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.ddns_summary))
                TextButton(onClick = { expanded = true }, enabled = !busy) { Text(provider.label) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DynamicDnsProvider.entries.forEach { choice ->
                        DropdownMenuItem(text = { Text(choice.label) }, onClick = {
                            if (provider != choice) {
                                provider = choice
                                secret = ""
                                account = ""
                                record = ""
                                host = ""
                                result = null
                            }
                            expanded = false
                        })
                    }
                }
                TextButton(enabled = !busy, onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.duckdns.org/")))
                    } catch (_: android.content.ActivityNotFoundException) {
                        result = DynamicDnsClient.Result.Unreachable
                    }
                }) { Text(stringResource(R.string.ddns_free_setup)) }
                DdnsField(host, { host = it }, R.string.ddns_host, !busy)
                DdnsField(address, { address = it }, R.string.ddns_address, !busy)
                TextButton(enabled = !busy, onClick = { detect(false) }) {
                    Text(stringResource(R.string.ddns_detect_ipv4))
                }
                if (provider != DynamicDnsProvider.FreeDns) {
                    TextButton(enabled = !busy, onClick = { detect(true) }) {
                        Text(stringResource(R.string.ddns_detect_ipv6))
                    }
                }
                when (provider) {
                    DynamicDnsProvider.Cloudflare -> {
                        DdnsField(account, { account = it }, R.string.ddns_zone, !busy)
                        DdnsField(record, { record = it }, R.string.ddns_record, !busy)
                        Text(stringResource(R.string.ddns_cloudflare_help))
                    }
                    DynamicDnsProvider.DuckDns -> Text(stringResource(R.string.ddns_duck_help))
                    DynamicDnsProvider.FreeDns -> Text(stringResource(R.string.ddns_free_help))
                    else -> DdnsField(account, { account = it }, R.string.ddns_account, !busy)
                }
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(stringResource(R.string.ddns_secret)) },
                    enabled = !busy,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                result?.let {
                    Text(stringResource(when (it) {
                        DynamicDnsClient.Result.Updated -> R.string.ddns_updated
                        DynamicDnsClient.Result.Unchanged -> R.string.ddns_unchanged
                        DynamicDnsClient.Result.Rejected -> R.string.ddns_rejected
                        DynamicDnsClient.Result.Unreachable -> R.string.ddns_unreachable
                        DynamicDnsClient.Result.Invalid -> R.string.ddns_invalid
                    }))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && host.isNotBlank() && address.isNotBlank() && secret.isNotBlank(),
                onClick = {
                    busy = true
                    result = null
                    scope.launch {
                        try {
                            result = DynamicDnsClient.update(provider, host, address, secret, account, record)
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(R.string.ddns_update)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun DdnsField(value: String, onChange: (String) -> Unit, label: Int, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
