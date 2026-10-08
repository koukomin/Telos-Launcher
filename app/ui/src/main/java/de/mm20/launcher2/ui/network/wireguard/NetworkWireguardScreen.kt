package de.mm20.launcher2.ui.network.wireguard

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import de.mm20.launcher2.network.api.WgConfigError
import de.mm20.launcher2.network.api.WgConfigException
import de.mm20.launcher2.network.api.WgInterface
import de.mm20.launcher2.network.api.WgPeer
import de.mm20.launcher2.network.api.WgStatus
import de.mm20.launcher2.network.api.WireguardConfig
import de.mm20.launcher2.network.api.WireguardController
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

private typealias Icons = de.mm20.launcher2.base.R.drawable

/** The list of WireGuard tunnels. */
@Serializable
data object NetworkWireguardRoute : NavKey

/** Which tunnel each app uses. */
@Serializable
data object NetworkWireguardAppsRoute : NavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkWireguardScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val wg: WireguardController = koinInject()
    val scope = rememberCoroutineScope()
    val configs by wg.configs.collectAsState()
    val status by wg.status.collectAsState()
    val stats by wg.stats.collectAsState()
    val systemDefault by wg.systemDefault.collectAsState()

    var editing by remember { mutableStateOf<WireguardConfig?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var defaultMenu by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }

    fun report(result: Result<WireguardConfig>) {
        result.exceptionOrNull()?.let { Toast.makeText(context, errorText(context, it), Toast.LENGTH_LONG).show() }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { readText(context, uri) }
            if (text == null) Toast.makeText(context, context.getString(R.string.nwg_err_other), Toast.LENGTH_LONG).show()
            else report(wg.importConf(text, null))
        }
    }
    val qrPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { decodeQr(context, uri) }
            if (text == null) Toast.makeText(context, context.getString(R.string.nwg_qr_not_found), Toast.LENGTH_LONG).show()
            else report(wg.importConf(text, null))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nwg_title)) },
                navigationIcon = {
                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(painterResource(Icons.arrow_back_24px), contentDescription = stringResource(R.string.nwg_back))
                    }
                },
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { addMenu = true }) {
                    Icon(painterResource(Icons.add_24px), contentDescription = stringResource(R.string.nwg_add))
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.nwg_add_new)) }, onClick = {
                        addMenu = false
                        editingIsNew = true
                        editing = WireguardConfig(
                            id = 0, name = "", wgInterface = WgInterface(privateKey = "", addresses = emptyList()),
                            peers = listOf(WgPeer(publicKey = "", allowedIps = listOf("0.0.0.0/0", "::/0"), persistentKeepalive = 25)),
                        )
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.nwg_add_file)) }, onClick = {
                        addMenu = false; filePicker.launch(arrayOf("*/*"))
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.nwg_add_qr)) }, onClick = {
                        addMenu = false; qrPicker.launch("image/*")
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.nwg_add_paste)) }, onClick = {
                        addMenu = false; pasting = true
                    })
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Box {
                    val current = configs.firstOrNull { it.id == systemDefault }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.nwg_system_default)) },
                        supportingContent = {
                            Column {
                                Text(current?.name ?: stringResource(R.string.nwg_none), color = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.nwg_system_default_summary))
                            }
                        },
                        modifier = Modifier.clickable { defaultMenu = true },
                    )
                    DropdownMenu(expanded = defaultMenu, onDismissRequest = { defaultMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.nwg_none)) }, onClick = {
                            defaultMenu = false; scope.launch { wg.setSystemDefault(null) }
                        })
                        configs.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = {
                                defaultMenu = false; scope.launch { wg.setSystemDefault(c.id) }
                            })
                        }
                    }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.nwg_apps)) },
                    supportingContent = { Text(stringResource(R.string.nwg_apps_summary)) },
                    leadingContent = { Icon(painterResource(Icons.apps_24px), contentDescription = null) },
                    modifier = Modifier.clickable { backStack.add(NetworkWireguardAppsRoute) },
                )
                HorizontalDivider()
            }
            item {
                Text(
                    stringResource(R.string.nwg_configs),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            if (configs.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.nwg_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(configs, key = { it.id }) { c ->
                val st = status[c.id] ?: WgStatus.Off
                val s = stats[c.id]
                ListItem(
                    headlineContent = { Text(c.name) },
                    supportingContent = {
                        Column {
                            Text(statusText(st), color = if (st == WgStatus.Up) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (s != null && st != WgStatus.Off) {
                                Text(stringResource(R.string.nwg_traffic, Formatter.formatShortFileSize(context, s.rxBytes), Formatter.formatShortFileSize(context, s.txBytes)))
                                if (s.lastHandshakeMs > 0) {
                                    Text(stringResource(R.string.nwg_handshake, DateUtils.getRelativeTimeSpanString(s.lastHandshakeMs, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS).toString()))
                                }
                            }
                        }
                    },
                    trailingContent = {
                        Switch(checked = c.enabled, onCheckedChange = { on -> scope.launch { wg.setEnabled(c.id, on) } })
                    },
                    modifier = Modifier.clickable { editingIsNew = false; editing = c },
                )
            }
            item {
                if (configs.any { it.enabled } && status.isEmpty()) {
                    Text(
                        stringResource(R.string.nwg_vpn_off_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    editing?.let { draft ->
        WireguardEditor(
            initial = draft,
            isNew = editingIsNew,
            wg = wg,
            onDismiss = { editing = null },
        )
    }

    if (pasting) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pasting = false },
            title = { Text(stringResource(R.string.nwg_add_paste)) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 6, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    pasting = false
                    scope.launch { report(wg.importConf(text, null)) }
                }) { Text(stringResource(R.string.nwg_import)) }
            },
            dismissButton = { TextButton(onClick = { pasting = false }) { Text(stringResource(R.string.nwg_cancel)) } },
        )
    }
}

@Composable
private fun statusText(st: WgStatus): String = stringResource(
    when (st) {
        WgStatus.Off -> R.string.nwg_status_off
        WgStatus.Connecting -> R.string.nwg_status_connecting
        WgStatus.Up -> R.string.nwg_status_up
        WgStatus.Down -> R.string.nwg_status_down
        WgStatus.Error -> R.string.nwg_status_error
    }
)

internal fun errorText(context: Context, t: Throwable): String {
    val e = t as? WgConfigException ?: return context.getString(R.string.nwg_err_other)
    val d = e.detail
    return when (e.error) {
        WgConfigError.Syntax -> context.getString(R.string.nwg_err_syntax, d)
        WgConfigError.MissingInterface -> context.getString(R.string.nwg_err_missing, "[Interface]")
        WgConfigError.MissingPrivateKey -> context.getString(R.string.nwg_err_missing, "PrivateKey")
        WgConfigError.MissingAddress -> context.getString(R.string.nwg_err_missing, "Address")
        WgConfigError.MissingPeer -> context.getString(R.string.nwg_err_missing, "[Peer]")
        WgConfigError.MissingPublicKey -> context.getString(R.string.nwg_err_missing, "PublicKey")
        WgConfigError.MissingAllowedIps -> context.getString(R.string.nwg_err_missing, "AllowedIPs")
        WgConfigError.InvalidKey -> context.getString(R.string.nwg_err_invalid_key, d)
        WgConfigError.InvalidAddress, WgConfigError.InvalidDns, WgConfigError.InvalidAllowedIps,
        WgConfigError.InvalidEndpoint, WgConfigError.InvalidNumber -> context.getString(R.string.nwg_err_invalid_value, d)
        WgConfigError.InvalidName -> context.getString(R.string.nwg_err_invalid_name)
        WgConfigError.NotFound -> context.getString(R.string.nwg_err_not_found)
        WgConfigError.Storage -> context.getString(R.string.nwg_err_storage, d)
        WgConfigError.TooLarge, WgConfigError.Other -> context.getString(R.string.nwg_err_other)
    }
}

private fun readText(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val bytes = input.readNBytesCompat(300 * 1024)
        String(bytes, Charsets.UTF_8)
    }
} catch (e: Exception) {
    null
}

private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < max) {
        val n = read(buf)
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

private fun decodeQr(context: Context, uri: Uri): String? = try {
    val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    val source = RGBLuminanceSource(bmp.width, bmp.height, pixels)
    QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
} catch (e: Exception) {
    null
}

/** Opens the share sheet with [text]. */
internal fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
