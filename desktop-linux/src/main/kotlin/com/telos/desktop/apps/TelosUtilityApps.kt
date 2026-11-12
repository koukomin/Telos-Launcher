package com.telos.desktop.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.i18n.TelosI18n
import com.telos.desktop.i18n.TelosI18n.t
import com.telos.desktop.network.DesktopNetworkController
import com.telos.desktop.panel.ConnectivityManager
import com.telos.desktop.panel.ConnectivityState
import com.telos.desktop.ui.TelosColors
import com.telos.desktop.sync.DesktopSessionSync
import com.telos.desktop.vault.DesktopProfiles
import com.telos.desktop.settings.TelosDesktopSettings
import com.telos.desktop.vault.PhantomVault
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// === TELOS_PENDING_REVIEW_START: telos_window_drag_and_ui_fix ===
@Composable
fun TelosNotesApp() {
    var text by remember { mutableStateOf("") }
    de.mm20.launcher2.uikmp.SharedNotesScreen(
        title = t("notes_title"),
        placeholder = t("notes_widget_placeholder"),
        text = text,
        onText = { text = it },
    )
}

@Composable
fun TelosCalendarApp() {
    val month = remember { YearMonth.now() }
    val today = LocalDate.now().dayOfMonth
    de.mm20.launcher2.uikmp.SharedCalendarScreen("${month.month} ${month.year}", month.lengthOfMonth(), today)
}

@Composable
fun TelosCalculatorApp() {
    val engine = remember { de.mm20.launcher2.uikmp.CalculatorEngine() }
    var display by remember { mutableStateOf(engine.display) }
    de.mm20.launcher2.uikmp.SharedCalculatorScreen(
        title = t("calculator_title"),
        display = display,
        onKey = { key ->
            engine.key(key)
            display = engine.display
        },
    )
}

@Composable
fun TelosClockApp() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(500)
        }
    }
    de.mm20.launcher2.uikmp.SharedClockScreen(
        t("preference_screen_clockwidget"),
        now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
        now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")),
    )
}

private enum class NetworkTab { Status, Dns, Firewall, WireGuard, Tor }

@Composable
fun TelosNetworkApp() {
    var tab by remember { mutableIntStateOf(NetworkTab.Status.ordinal) }
    var net by remember { mutableStateOf(ConnectivityState(false, null, null, emptyList(), false, emptyList())) }
    var fwActive by remember { mutableStateOf(false) }
    var wgStatus by remember { mutableStateOf(DesktopNetworkController.wireGuardStatus()) }
    var torStatus by remember { mutableStateOf(DesktopNetworkController.torStatus()) }
    var dnsText by remember { mutableStateOf(DesktopNetworkController.readDnsPreference()) }
    var allowText by remember { mutableStateOf(DesktopNetworkController.readAllowlistText()) }
    var wgConfDraft by remember {
        mutableStateOf(
            DesktopNetworkController.wireguardConfFile().takeIf { it.isFile }?.readText().orEmpty(),
        )
    }
    var dnsApply by remember { mutableStateOf(DesktopNetworkController.DnsApplyResult.PreferenceOnly) }
    val profiles = remember { DesktopProfiles.listProfileFolders() }
    val activeProfile = remember { DesktopProfiles.activeProfile() }
    val scope = rememberCoroutineScope()
    val cardShape = remember { RoundedCornerShape(10.dp) }
    val neon = TelosColors.secondary
    val refresh: suspend () -> Unit = {
        withContext(Dispatchers.IO) {
            net = DesktopNetworkController.snapshotConnectivity()
            fwActive = DesktopNetworkController.sessionFirewallActive()
            wgStatus = DesktopNetworkController.wireGuardStatus()
            torStatus = DesktopNetworkController.torStatus()
            dnsApply = DesktopNetworkController.applyDnsPreference()
        }
    }
    LaunchedEffect(Unit) {
        refresh()
        while (true) {
            delay(2500)
            refresh()
        }
    }
    Column(Modifier.fillMaxSize().background(TelosColors.surface).padding(14.dp)) {
        Text(t("net_hub_title"), color = neon, fontSize = 22.sp)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NetworkTab.entries.forEach { entry ->
                val selected = tab == entry.ordinal
                FilterChip(
                    selected = selected,
                    onClick = { tab = entry.ordinal },
                    label = {
                        Text(
                            networkTabLabel(entry),
                            fontSize = 12.sp,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TelosColors.surfaceHigh,
                        selectedLabelColor = neon,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = neon.copy(alpha = if (selected) 0.9f else 0.35f),
                    ),
                )
            }
        }
        when (NetworkTab.entries[tab]) {
            NetworkTab.Status -> NetworkStatusPane(
                net = net,
                fwActive = fwActive,
                wgStatus = wgStatus,
                torStatus = torStatus,
                profiles = profiles,
                activeProfile = activeProfile,
                cardShape = cardShape,
                neon = neon,
                onRefreshFw = {
                    scope.launch(Dispatchers.IO) {
                        fwActive = DesktopNetworkController.sessionFirewallActive()
                    }
                },
                onApplyFw = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.applySessionFirewall()
                        fwActive = DesktopNetworkController.sessionFirewallActive()
                    }
                },
                onClearFw = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.clearSessionFirewall()
                        fwActive = DesktopNetworkController.sessionFirewallActive()
                    }
                },
            )
            NetworkTab.Dns -> NetworkDnsPane(
                dnsText = dnsText,
                onDnsText = { dnsText = it },
                dnsApply = dnsApply,
                cardShape = cardShape,
                neon = neon,
                onSave = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.writeDnsPreference(dnsText)
                        dnsApply = DesktopNetworkController.applyDnsPreference()
                    }
                },
            )
            NetworkTab.Firewall -> NetworkFirewallPane(
                allowText = allowText,
                onAllowText = { allowText = it },
                fwActive = fwActive,
                cardShape = cardShape,
                neon = neon,
                onSave = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.writeAllowlistText(allowText)
                        DesktopNetworkController.applySessionFirewall()
                        fwActive = DesktopNetworkController.sessionFirewallActive()
                    }
                },
                onApply = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.applySessionFirewall()
                        fwActive = DesktopNetworkController.sessionFirewallActive()
                    }
                },
                onClear = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.clearSessionFirewall()
                        fwActive = DesktopNetworkController.sessionFirewallActive()
                    }
                },
            )
            NetworkTab.WireGuard -> NetworkWireGuardPane(
                draft = wgConfDraft,
                onDraft = { wgConfDraft = it },
                status = wgStatus,
                cardShape = cardShape,
                neon = neon,
                onImport = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.importWireGuardConfig(wgConfDraft)
                        wgStatus = DesktopNetworkController.wireGuardStatus()
                    }
                },
                onStart = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.startWireGuard()
                        wgStatus = DesktopNetworkController.wireGuardStatus()
                    }
                },
                onStop = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.stopWireGuard()
                        wgStatus = DesktopNetworkController.wireGuardStatus()
                    }
                },
            )
            NetworkTab.Tor -> NetworkTorPane(
                status = torStatus,
                cardShape = cardShape,
                neon = neon,
                onStart = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.startTor()
                        torStatus = DesktopNetworkController.torStatus()
                    }
                },
                onStop = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.stopTor()
                        torStatus = DesktopNetworkController.torStatus()
                    }
                },
            )
        }
    }
}

@Composable
private fun networkTabLabel(tab: NetworkTab): String = when (tab) {
    NetworkTab.Status -> t("net_hub_status")
    NetworkTab.Dns -> t("net_hub_dns")
    NetworkTab.Firewall -> t("netfw_title")
    NetworkTab.WireGuard -> t("net_hub_wireguard")
    NetworkTab.Tor -> t("nt_title")
}

@Composable
private fun NetworkStatusPane(
    net: ConnectivityState,
    fwActive: Boolean,
    wgStatus: DesktopNetworkController.WireGuardStatus,
    torStatus: DesktopNetworkController.TorStatus,
    profiles: List<DesktopProfiles.ProfileFolder>,
    activeProfile: String?,
    cardShape: RoundedCornerShape,
    neon: androidx.compose.ui.graphics.Color,
    onApplyFw: () -> Unit,
    onClearFw: () -> Unit,
    onRefreshFw: () -> Unit,
) {
    var syncPass by remember { mutableStateOf("") }
    var kdbxPath by remember {
        mutableStateOf(
            File(DesktopNetworkController.configHome(), "sync/keepass.kdbx").absolutePath,
        )
    }
    var autoImport by remember {
        mutableStateOf(
            File(DesktopNetworkController.configHome(), DesktopSessionSync.AUTO_IMPORT_FLAG)
                .takeIf { it.isFile }
                ?.readText()
                ?.trim() == "true",
        )
    }
    var syncMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tall = maxHeight >= 520.dp
        val statusCol: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                statusCard(cardShape, neon, t("network_widget_wifi"), wifiLine(net))
                statusCard(
                    cardShape,
                    neon,
                    "VPN / NM",
                    net.vpn ?: t("net_hub_stat_wg_off"),
                )
                statusCard(
                    cardShape,
                    neon,
                    t("netfw_title"),
                    if (fwActive) t("net_hub_state_on") else t("net_hub_state_off"),
                )
                statusCard(
                    cardShape,
                    neon,
                    t("net_hub_wireguard"),
                    if (wgStatus.running) t("nwg_status_up") else t("nwg_status_off"),
                )
                statusCard(
                    cardShape,
                    neon,
                    t("nt_title"),
                    if (torStatus.running) t("net_hub_state_on") else t("net_hub_state_off"),
                )
                if (activeProfile != null || profiles.isNotEmpty()) {
                    statusCard(
                        cardShape,
                        neon,
                        t("preference_screen_context_profiles"),
                        activeProfile ?: "—",
                    )
                    profiles.take(6).forEach {
                        Text("· ${it.name}", color = TelosColors.onSurface, fontSize = 12.sp)
                    }
                    Text(t("desktop_net_profiles_sum"), color = TelosColors.onSurface.copy(alpha = 0.7f), fontSize = 11.sp)
                }
                statusCard(cardShape, neon, t("desktop_session_sync_title"), t("backup_group_keepass"))
                OutlinedTextField(
                    syncPass,
                    onValueChange = { syncPass = it },
                    label = { Text(t("desktop_session_sync_pass", DesktopSessionSync.DEFAULT_KEEPASS_ENTRY)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    kdbxPath,
                    onValueChange = { kdbxPath = it },
                    label = { Text(t("desktop_session_sync_keepass")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    neonButton(t("desktop_session_sync_export"), onClick = {
                        scope.launch(Dispatchers.IO) {
                            val pass = syncPass.toCharArray()
                            if (pass.isEmpty()) {
                                syncMsg = t("desktop_session_sync_pass_required")
                                return@launch
                            }
                            val dest = File(DesktopSessionSync.defaultExportPath(), DesktopSessionSync.BUNDLE_NAME)
                            val settings = TelosDesktopSettings(PhantomVault.engine.configDir).also { it.load() }
                            val r = DesktopSessionSync.exportBundle(dest, pass, settings)
                            if (r.isSuccess && autoImport) {
                                DesktopSessionSync.rememberSyncKey(pass, settings)
                            }
                            pass.fill('\u0000')
                            syncMsg = if (r.isSuccess) dest.name else r.exceptionOrNull()?.message
                        }
                    })
                    neonButton(
                        t("desktop_session_sync_import"),
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val pass = syncPass.toCharArray()
                                if (pass.isEmpty()) {
                                    syncMsg = t("desktop_session_sync_pass_required")
                                    return@launch
                                }
                                val bundle = File(DesktopSessionSync.defaultExportPath(), DesktopSessionSync.BUNDLE_NAME)
                                val settings = TelosDesktopSettings(PhantomVault.engine.configDir).also { it.load() }
                                val r = DesktopSessionSync.importBundle(bundle, pass, settings)
                                if (r.isSuccess && autoImport) {
                                    DesktopSessionSync.rememberSyncKey(pass, settings)
                                }
                                pass.fill('\u0000')
                                syncMsg = if (r.isSuccess) t("desktop_session_sync_imported") else r.exceptionOrNull()?.message
                            }
                        },
                        filled = false,
                    )
                }
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val flag = File(DesktopNetworkController.configHome(), DesktopSessionSync.AUTO_IMPORT_FLAG)
                        flag.parentFile.mkdirs()
                        val enabling = !autoImport
                        val settings = TelosDesktopSettings(PhantomVault.engine.configDir).also { it.load() }
                        if (enabling) {
                            val pass = syncPass.toCharArray()
                            when {
                                pass.isNotEmpty() -> {
                                    DesktopSessionSync.rememberSyncKey(pass, settings)
                                    pass.fill('\u0000')
                                    flag.writeText("true")
                                    autoImport = true
                                    syncMsg = t("desktop_session_sync_key_stored")
                                }
                                DesktopSessionSync.hasRememberedSyncKey(settings) -> {
                                    flag.writeText("true")
                                    autoImport = true
                                    syncMsg = t("desktop_session_sync_key_stored")
                                }
                                else -> {
                                    // Never invent credentials / never enable prompt-free boot
                                    // without a stored sync key.
                                    syncMsg = t("desktop_session_sync_key_missing")
                                }
                            }
                        } else {
                            flag.writeText("false")
                            autoImport = false
                        }
                    }
                }) {
                    Text(
                        t("desktop_session_sync_auto") + ": " + if (autoImport) t("net_hub_state_on") else t("net_hub_state_off"),
                    )
                }
                syncMsg?.let { Text(it, color = TelosColors.primary, fontSize = 11.sp) }
            }
        }
        val actionsCol: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    neonButton(t("net_hub_start"), onApplyFw)
                    neonButton(t("net_hub_stop"), onClearFw, filled = false)
                }
                Text(
                    t("desktop_net_allowlist_path") + ": " + DesktopNetworkController.firewallAllowlistPath().name,
                    color = TelosColors.onSurface.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onRefreshFw() },
                )
            }
        }
        if (tall) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                statusCol()
                actionsCol()
                LazyColumn(Modifier.height(200.dp)) {
                    items(net.aps, key = { it.ssid }) { ap ->
                        Text(
                            "${ap.ssid}  ${ap.signal}%",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { ConnectivityManager.wifiConnect(ap.ssid) }
                                .padding(vertical = 6.dp),
                            color = TelosColors.onSurface,
                        )
                    }
                }
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                statusCol()
                Spacer(Modifier.height(12.dp))
                actionsCol()
                net.aps.take(8).forEach { ap ->
                    Text(
                        "${ap.ssid}  ${ap.signal}%",
                        modifier = Modifier
                            .clickable { ConnectivityManager.wifiConnect(ap.ssid) }
                            .padding(vertical = 6.dp),
                        color = TelosColors.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkDnsPane(
    dnsText: String,
    onDnsText: (String) -> Unit,
    dnsApply: DesktopNetworkController.DnsApplyResult,
    cardShape: RoundedCornerShape,
    neon: androidx.compose.ui.graphics.Color,
    onSave: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(t("net_dns_title"), color = neon, fontSize = 18.sp)
        Text(t("desktop_net_dns_pref_hint"), color = TelosColors.onSurface.copy(alpha = 0.85f), fontSize = 12.sp)
        OutlinedTextField(
            dnsText,
            onDnsText,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            label = { Text(t("net_hub_dns")) },
            minLines = 3,
        )
        Text(dnsApplyLabel(dnsApply), color = neon, fontSize = 12.sp)
        neonButton(t("net_dns_save"), onSave)
    }
}

@Composable
private fun NetworkFirewallPane(
    allowText: String,
    onAllowText: (String) -> Unit,
    fwActive: Boolean,
    cardShape: RoundedCornerShape,
    neon: androidx.compose.ui.graphics.Color,
    onSave: () -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "${t("netfw_title")} · ${if (fwActive) t("net_hub_state_on") else t("net_hub_state_off")}",
            color = neon,
            fontSize = 18.sp,
        )
        OutlinedTextField(
            allowText,
            onAllowText,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            label = { Text(t("net_hub_fw_custom")) },
            minLines = 10,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            neonButton(t("nwg_save"), onSave)
            neonButton(t("net_hub_start"), onApply, filled = false)
            neonButton(t("net_hub_stop"), onClear, filled = false)
        }
    }
}

@Composable
private fun NetworkWireGuardPane(
    draft: String,
    onDraft: (String) -> Unit,
    status: DesktopNetworkController.WireGuardStatus,
    cardShape: RoundedCornerShape,
    neon: androidx.compose.ui.graphics.Color,
    onImport: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(t("nwg_title"), color = neon, fontSize = 18.sp)
        Text(
            if (status.running) t("nwg_status_up") else t("nwg_status_off"),
            color = TelosColors.onSurface,
            fontSize = 13.sp,
        )
        OutlinedTextField(
            draft,
            onDraft,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            label = { Text(t("nwg_add_paste")) },
            minLines = 8,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            neonButton(t("nwg_import"), onImport, filled = false)
            neonButton(t("net_hub_start"), onStart)
            neonButton(t("net_hub_stop"), onStop, filled = false)
        }
    }
}

@Composable
private fun NetworkTorPane(
    status: DesktopNetworkController.TorStatus,
    cardShape: RoundedCornerShape,
    neon: androidx.compose.ui.graphics.Color,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("nt_title"), color = neon, fontSize = 18.sp)
        Text(
            if (status.running) t("net_hub_state_on") else t("net_hub_state_off"),
            color = TelosColors.onSurface,
        )
        if (!status.binaryPresent) {
            Text(t("net_hub_state_error"), color = TelosColors.onSurface.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            neonButton(t("net_hub_start"), onStart)
            neonButton(t("net_hub_stop"), onStop, filled = false)
        }
    }
}

@Composable
private fun statusCard(
    shape: RoundedCornerShape,
    border: androidx.compose.ui.graphics.Color,
    title: String,
    value: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, border.copy(alpha = 0.55f), shape)
            .background(TelosColors.surfaceContainer.copy(alpha = 0.92f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(title, color = border, fontSize = 12.sp)
        Text(value, color = TelosColors.onSurface, fontSize = 15.sp)
    }
}

@Composable
private fun neonButton(label: String, onClick: () -> Unit, filled: Boolean = true) {
    Button(
        onClick = onClick,
        colors = if (filled) {
            ButtonDefaults.buttonColors(containerColor = TelosColors.secondary, contentColor = TelosColors.surface)
        } else {
            ButtonDefaults.outlinedButtonColors(contentColor = TelosColors.secondary)
        },
    ) {
        Text(label, fontSize = 12.sp)
    }
}

private fun wifiLine(net: ConnectivityState): String {
    val state = if (net.wifiOn) TelosI18n.t("network_widget_connected") else TelosI18n.t("network_widget_disconnected")
    return "$state  ${net.activeSsid ?: "—"}"
}

private fun dnsApplyLabel(result: DesktopNetworkController.DnsApplyResult): String = when (result) {
    DesktopNetworkController.DnsApplyResult.Applied -> TelosI18n.t("net_hub_state_on")
    DesktopNetworkController.DnsApplyResult.PreferenceOnly -> TelosI18n.t("desktop_net_dns_pref_hint")
    DesktopNetworkController.DnsApplyResult.Failed -> TelosI18n.t("net_hub_state_error")
    DesktopNetworkController.DnsApplyResult.Invalid -> TelosI18n.t("nwg_err_syntax")
}

@Composable
fun TelosStoreApp() {
    var installed by remember { mutableStateOf<List<FlatpakRef>>(emptyList()) }
    var remote by remember { mutableStateOf<List<FlatpakRef>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        installed = withContext(Dispatchers.IO) { FlatpakStore.listInstalled() }
    }
    LaunchedEffect(q) {
        remote = if (q.length < 2) emptyList()
        else withContext(Dispatchers.IO) { FlatpakStore.search(q) }
    }
    val shown = remember(installed, q) {
        installed.filter { q.isBlank() || it.name.contains(q, true) || it.id.contains(q, true) }
    }
    Column(Modifier.fillMaxSize().background(TelosColors.surface).padding(12.dp)) {
        Text(t("hf_store_title"), color = TelosColors.secondary, fontSize = 20.sp)
        if (!FlatpakStore.available()) {
            Text(t("hc_add_an_app"), color = TelosColors.secondary)
        }
        OutlinedTextField(q, { q = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t("hc_filter")) }, singleLine = true)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { FlatpakStore.updateAll() }) { Text(t("hc_check_for_updates")) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            item {
                Text(t("hc_add_installed_apps"), color = TelosColors.secondary, fontSize = 12.sp)
            }
            items(shown.take(80), key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        e.name + "  " + e.id,
                        color = TelosColors.onSurface,
                        modifier = Modifier.weight(1f).clickable {
                            com.telos.desktop.linux.Sys.runBg("flatpak", "run", e.id)
                        },
                    )
                    Text(
                        t("hc_delete"),
                        color = TelosColors.primary,
                        modifier = Modifier.clickable {
                            FlatpakStore.uninstall(e.id)
                            installed = installed.filter { it.id != e.id }
                        }.padding(start = 8.dp),
                    )
                }
            }
            if (remote.isNotEmpty()) {
                item { Text(t("action_install"), color = TelosColors.secondary, fontSize = 12.sp) }
                items(remote.take(40), key = { "r-" + it.id }) { e ->
                    Text(
                        e.name + "  " + e.id,
                        color = TelosColors.onSurface,
                        modifier = Modifier.clickable { FlatpakStore.install(e.id) }.padding(8.dp),
                    )
                }
            }
        }
    }
}
// === TELOS_PENDING_REVIEW_END: telos_window_drag_and_ui_fix ===
