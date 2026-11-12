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
import com.telos.desktop.sync.DesktopVaultRestore
import com.telos.desktop.update.DesktopSignedUpdate
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

private enum class NetworkTab { Status, Dns, Firewall, Apps, WireGuard, Tor }

@Composable
fun TelosNetworkApp() {
    var tab by remember { mutableIntStateOf(NetworkTab.Status.ordinal) }
    var net by remember { mutableStateOf(ConnectivityState(false, null, null, emptyList(), false, emptyList())) }
    var fwActive by remember { mutableStateOf(false) }
    var killStatus by remember { mutableStateOf(DesktopNetworkController.KillSwitchStatus.Disabled) }
    var wgStatus by remember { mutableStateOf(DesktopNetworkController.wireGuardStatus()) }
    var torStatus by remember { mutableStateOf(DesktopNetworkController.torStatus()) }
    var dnsText by remember { mutableStateOf(DesktopNetworkController.readDnsPreference()) }
    var dnsBlockText by remember { mutableStateOf(DesktopNetworkController.readDnsBlocklistText()) }
    var dnsAllowText by remember { mutableStateOf(DesktopNetworkController.readDnsAllowlistText()) }
    var nextDnsId by remember {
        mutableStateOf(DesktopNetworkController.readNextDnsConfigId().orEmpty())
    }
    var dnsFilterStatus by remember { mutableStateOf(DesktopNetworkController.dnsFilterStatus()) }
    var allowText by remember { mutableStateOf(DesktopNetworkController.readAllowlistText()) }
    var appRoutingText by remember { mutableStateOf(DesktopNetworkController.readAppRoutingText()) }
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
            killStatus = DesktopNetworkController.ensureKillSwitch()
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
                killStatus = killStatus,
                wgStatus = wgStatus,
                torStatus = torStatus,
                profiles = profiles,
                activeProfile = activeProfile,
                cardShape = cardShape,
                neon = neon,
                onRefreshFw = {
                    scope.launch(Dispatchers.IO) {
                        killStatus = DesktopNetworkController.ensureKillSwitch()
                        fwActive = DesktopNetworkController.sessionFirewallActive()
                    }
                },
                onApplyFw = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.applySessionFirewall()
                        killStatus = DesktopNetworkController.killSwitchStatus()
                        fwActive = DesktopNetworkController.sessionFirewallActive()
                    }
                },
                onClearFw = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.clearSessionFirewall()
                        killStatus = DesktopNetworkController.killSwitchStatus()
                        fwActive = DesktopNetworkController.sessionFirewallActive()
                    }
                },
                onToggleKillSwitch = {
                    scope.launch(Dispatchers.IO) {
                        val next = !DesktopNetworkController.readKillSwitchEnabled()
                        killStatus = DesktopNetworkController.setKillSwitchEnabled(next)
                        fwActive = DesktopNetworkController.sessionFirewallActive()
                    }
                },
            )
            NetworkTab.Dns -> NetworkDnsPane(
                dnsText = dnsText,
                onDnsText = { dnsText = it },
                dnsBlockText = dnsBlockText,
                onDnsBlockText = { dnsBlockText = it },
                dnsAllowDomainsText = dnsAllowText,
                onDnsAllowDomainsText = { dnsAllowText = it },
                nextDnsId = nextDnsId,
                onNextDnsId = { nextDnsId = it },
                dnsApply = dnsApply,
                dnsFilterStatus = dnsFilterStatus,
                cardShape = cardShape,
                neon = neon,
                onSave = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.writeDnsPreference(dnsText)
                        DesktopNetworkController.writeDnsBlocklistText(dnsBlockText)
                        DesktopNetworkController.writeDnsAllowlistText(dnsAllowText)
                        DesktopNetworkController.writeNextDnsConfigId(nextDnsId)
                        dnsApply = DesktopNetworkController.applyDnsPreference()
                        dnsFilterStatus = DesktopNetworkController.applyDnsFilters()
                        dnsText = DesktopNetworkController.readDnsPreference()
                        dnsBlockText = DesktopNetworkController.readDnsBlocklistText()
                        dnsAllowText = DesktopNetworkController.readDnsAllowlistText()
                        nextDnsId = DesktopNetworkController.readNextDnsConfigId().orEmpty()
                    }
                },
            )
            NetworkTab.Firewall -> NetworkFirewallPane(
                allowText = allowText,
                onAllowText = { allowText = it },
                fwActive = fwActive,
                killStatus = killStatus,
                cardShape = cardShape,
                neon = neon,
                onSave = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.writeAllowlistText(allowText)
                        DesktopNetworkController.applySessionFirewall()
                        killStatus = DesktopNetworkController.ensureKillSwitch()
                        fwActive = DesktopNetworkController.sessionFirewallActive()
                    }
                },
                onApply = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.applySessionFirewall()
                        killStatus = DesktopNetworkController.killSwitchStatus()
                        fwActive = DesktopNetworkController.sessionFirewallActive()
                    }
                },
                onClear = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.clearSessionFirewall()
                        killStatus = DesktopNetworkController.killSwitchStatus()
                        fwActive = DesktopNetworkController.sessionFirewallActive()
                    }
                },
            )
            NetworkTab.Apps -> NetworkAppsPane(
                routingText = appRoutingText,
                onRoutingText = { appRoutingText = it },
                cardShape = cardShape,
                neon = neon,
                onSave = {
                    scope.launch(Dispatchers.IO) {
                        DesktopNetworkController.writeAppRoutingText(appRoutingText)
                        appRoutingText = DesktopNetworkController.readAppRoutingText()
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
    NetworkTab.Apps -> t("desktop_net_apps_tab")
    NetworkTab.WireGuard -> t("net_hub_wireguard")
    NetworkTab.Tor -> t("nt_title")
}

@Composable
private fun NetworkStatusPane(
    net: ConnectivityState,
    fwActive: Boolean,
    killStatus: DesktopNetworkController.KillSwitchStatus,
    wgStatus: DesktopNetworkController.WireGuardStatus,
    torStatus: DesktopNetworkController.TorStatus,
    profiles: List<DesktopProfiles.ProfileFolder>,
    activeProfile: String?,
    cardShape: RoundedCornerShape,
    neon: androidx.compose.ui.graphics.Color,
    onApplyFw: () -> Unit,
    onClearFw: () -> Unit,
    onRefreshFw: () -> Unit,
    onToggleKillSwitch: () -> Unit,
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
    var restorePreview by remember { mutableStateOf<DesktopVaultRestore.RestorePreview?>(null) }
    var updateUrl by remember {
        mutableStateOf(
            runCatching {
                val s = TelosDesktopSettings(PhantomVault.engine.configDir).also { it.load() }
                DesktopSignedUpdate.preferredManifestUrl(s).orEmpty()
            }.getOrDefault(""),
        )
    }
    var updateOffer by remember { mutableStateOf<DesktopSignedUpdate.VerifiedOffer?>(null) }
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
                    t("desktop_net_kill_switch"),
                    killSwitchLabel(killStatus),
                )
                neonButton(
                    if (killStatus == DesktopNetworkController.KillSwitchStatus.Disabled) {
                        t("desktop_net_kill_switch_enable")
                    } else {
                        t("desktop_net_kill_switch_disable")
                    },
                    onToggleKillSwitch,
                    filled = killStatus == DesktopNetworkController.KillSwitchStatus.Disabled,
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
                    profiles.take(6).forEach { folder ->
                        Text(
                            "· ${folder.name}",
                            color = TelosColors.onSurface,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                DesktopProfiles.setActiveProfile(folder.name)
                            },
                        )
                    }
                    Text(t("desktop_net_profiles_sum"), color = TelosColors.onSurface.copy(alpha = 0.7f), fontSize = 11.sp)
                }
                var auditLines by remember {
                    mutableStateOf(com.telos.desktop.vault.TelosSessionAuditLog.readTail(8))
                }
                statusCard(
                    cardShape,
                    neon,
                    t("desktop_opsec_audit_title"),
                    if (auditLines.isEmpty()) t("desktop_opsec_audit_empty") else auditLines.last(),
                )
                TextButton(onClick = {
                    auditLines = com.telos.desktop.vault.TelosSessionAuditLog.readTail(8)
                }) {
                    Text(t("desktop_opsec_audit_refresh"), fontSize = 11.sp)
                }
                auditLines.takeLast(5).forEach { line ->
                    Text(line, color = TelosColors.onSurface.copy(alpha = 0.75f), fontSize = 10.sp)
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
                statusCard(cardShape, neon, t("desktop_vault_restore_title"), t("desktop_vault_restore_hint"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    neonButton(t("desktop_vault_restore_preview_btn"), onClick = {
                        scope.launch(Dispatchers.IO) {
                            val pass = syncPass.toCharArray()
                            if (pass.isEmpty()) {
                                syncMsg = t("desktop_session_sync_pass_required")
                                return@launch
                            }
                            val r = DesktopVaultRestore.preview(DesktopVaultRestore.defaultBundle(), pass)
                            pass.fill('\u0000')
                            if (r.isSuccess) {
                                val prev = r.getOrThrow()
                                restorePreview = prev
                                syncMsg = t(
                                    "desktop_vault_restore_preview",
                                    prev.telosConfigFileCount.toString(),
                                    if (prev.hasDesktopProperties) "yes" else "no",
                                )
                            } else {
                                restorePreview = null
                                syncMsg = r.exceptionOrNull()?.message
                            }
                        }
                    })
                    restorePreview?.let { preview ->
                        neonButton(t("desktop_vault_restore_confirm"), onClick = {
                            scope.launch(Dispatchers.IO) {
                                val pass = syncPass.toCharArray()
                                val settings = TelosDesktopSettings(PhantomVault.engine.configDir).also { it.load() }
                                val r = DesktopVaultRestore.applyConfirmed(
                                    preview.bundle,
                                    pass,
                                    confirmToken = preview.confirmToken,
                                    expectedToken = preview.confirmToken,
                                    vaultSettings = settings,
                                )
                                pass.fill('\u0000')
                                restorePreview = null
                                syncMsg = if (r.isSuccess) t("desktop_vault_restore_done")
                                else r.exceptionOrNull()?.message
                            }
                        })
                    }
                }
                statusCard(cardShape, neon, t("desktop_update_title"), t("desktop_update_hint"))
                OutlinedTextField(
                    updateUrl,
                    onValueChange = { updateUrl = it },
                    label = { Text(t("desktop_update_url_label")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    neonButton(t("desktop_update_check"), onClick = {
                        scope.launch(Dispatchers.IO) {
                            val settings = TelosDesktopSettings(PhantomVault.engine.configDir).also { it.load() }
                            DesktopSignedUpdate.saveManifestUrl(settings, updateUrl)
                            val url = updateUrl.trim().ifEmpty {
                                DesktopSignedUpdate.preferredManifestUrl(settings).orEmpty()
                            }
                            if (url.isEmpty()) {
                                updateOffer = null
                                syncMsg = t("desktop_update_no_url")
                                return@launch
                            }
                            val result = DesktopSignedUpdate.check(url)
                            when (result.status) {
                                DesktopSignedUpdate.CheckStatus.ReadyForConfirm -> {
                                    updateOffer = result.offer
                                    syncMsg = t("desktop_update_ready", result.offer?.version.orEmpty())
                                }
                                DesktopSignedUpdate.CheckStatus.SignatureInvalid -> {
                                    updateOffer = null
                                    syncMsg = t("desktop_update_sig_fail")
                                }
                                else -> {
                                    updateOffer = null
                                    syncMsg = result.detail.ifBlank { t("desktop_update_no_url") }
                                }
                            }
                        }
                    })
                    updateOffer?.let { offer ->
                        neonButton(t("desktop_update_install_confirm"), onClick = {
                            scope.launch(Dispatchers.IO) {
                                val r = DesktopSignedUpdate.installConfirmed(offer, offer.confirmToken)
                                updateOffer = null
                                syncMsg = if (r.isSuccess) {
                                    t("desktop_update_installed", r.getOrThrow().name)
                                } else {
                                    r.exceptionOrNull()?.message
                                }
                            }
                        })
                    }
                }
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
    dnsBlockText: String,
    onDnsBlockText: (String) -> Unit,
    dnsAllowDomainsText: String,
    onDnsAllowDomainsText: (String) -> Unit,
    nextDnsId: String,
    onNextDnsId: (String) -> Unit,
    dnsApply: DesktopNetworkController.DnsApplyResult,
    dnsFilterStatus: DesktopNetworkController.DnsFilterStatus,
    cardShape: RoundedCornerShape,
    neon: androidx.compose.ui.graphics.Color,
    onSave: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(t("net_dns_title"), color = neon, fontSize = 18.sp)
        Text(t("desktop_net_dns_pref_hint"), color = TelosColors.onSurface.copy(alpha = 0.85f), fontSize = 12.sp)
        OutlinedTextField(
            dnsText,
            onDnsText,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            label = { Text(t("net_hub_dns")) },
            minLines = 2,
        )
        Text(dnsApplyLabel(dnsApply), color = neon, fontSize = 12.sp)

        Text(t("desktop_net_dns_filters_title"), color = neon, fontSize = 16.sp)
        Text(
            t("desktop_net_dns_filters_hint"),
            color = TelosColors.onSurface.copy(alpha = 0.85f),
            fontSize = 12.sp,
        )
        OutlinedTextField(
            nextDnsId,
            onNextDnsId,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            label = { Text(t("desktop_net_nextdns_id")) },
            singleLine = true,
        )
        OutlinedTextField(
            dnsBlockText,
            onDnsBlockText,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            label = { Text(t("desktop_net_dns_blocklist")) },
            minLines = 5,
        )
        OutlinedTextField(
            dnsAllowDomainsText,
            onDnsAllowDomainsText,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            label = { Text(t("desktop_net_dns_allowlist")) },
            minLines = 4,
        )
        Text(
            "${t("desktop_net_dns_filter_status")}: ${dnsFilterStatusLabel(dnsFilterStatus)}",
            color = neon,
            fontSize = 12.sp,
        )
        neonButton(t("net_dns_save"), onSave)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NetworkFirewallPane(
    allowText: String,
    onAllowText: (String) -> Unit,
    fwActive: Boolean,
    killStatus: DesktopNetworkController.KillSwitchStatus,
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
        Text(
            "${t("desktop_net_kill_switch")}: ${killSwitchLabel(killStatus)}",
            color = TelosColors.onSurface.copy(alpha = 0.85f),
            fontSize = 12.sp,
        )
        if (killStatus != DesktopNetworkController.KillSwitchStatus.Disabled) {
            Text(
                t("desktop_net_kill_switch_clear_blocked"),
                color = TelosColors.onSurface.copy(alpha = 0.75f),
                fontSize = 11.sp,
            )
        }
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
private fun NetworkAppsPane(
    routingText: String,
    onRoutingText: (String) -> Unit,
    cardShape: RoundedCornerShape,
    neon: androidx.compose.ui.graphics.Color,
    onSave: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(t("desktop_net_apps_title"), color = neon, fontSize = 18.sp)
        Text(
            t("desktop_net_apps_hint"),
            color = TelosColors.onSurface.copy(alpha = 0.85f),
            fontSize = 12.sp,
        )
        OutlinedTextField(
            routingText,
            onRoutingText,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            label = { Text(t("desktop_net_apps_file")) },
            minLines = 10,
        )
        neonButton(t("nwg_save"), onSave)
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

private fun killSwitchLabel(status: DesktopNetworkController.KillSwitchStatus): String = when (status) {
    DesktopNetworkController.KillSwitchStatus.Disabled -> TelosI18n.t("net_hub_state_off")
    DesktopNetworkController.KillSwitchStatus.PreferenceOnly -> TelosI18n.t("desktop_net_kill_switch_pref_only")
    DesktopNetworkController.KillSwitchStatus.Enforced -> TelosI18n.t("desktop_net_kill_switch_enforced")
    DesktopNetworkController.KillSwitchStatus.Failed -> TelosI18n.t("desktop_net_kill_switch_failed")
}

private fun dnsFilterStatusLabel(status: DesktopNetworkController.DnsFilterStatus): String = when (status) {
    DesktopNetworkController.DnsFilterStatus.Disabled -> TelosI18n.t("net_hub_state_off")
    DesktopNetworkController.DnsFilterStatus.PreferenceOnly -> TelosI18n.t("desktop_net_dns_filter_pref_only")
    DesktopNetworkController.DnsFilterStatus.Enforced -> TelosI18n.t("desktop_net_dns_filter_enforced")
    DesktopNetworkController.DnsFilterStatus.Failed -> TelosI18n.t("desktop_net_dns_filter_failed")
    DesktopNetworkController.DnsFilterStatus.Invalid -> TelosI18n.t("desktop_net_nextdns_id_invalid")
}

@Composable
fun TelosStoreApp() {
    var installed by remember { mutableStateOf<List<FlatpakRef>>(emptyList()) }
    var remote by remember { mutableStateOf<List<FlatpakRef>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    var permissionsFor by remember { mutableStateOf<FlatpakRef?>(null) }
    var showPortalPerms by remember { mutableStateOf(false) }
    var winBackend by remember {
        mutableStateOf(WindowsPackageStore.availableBackends().firstOrNull())
    }
    var winInstalled by remember { mutableStateOf<List<WindowsPackageRef>>(emptyList()) }
    var winRemote by remember { mutableStateOf<List<WindowsPackageRef>>(emptyList()) }
    var pendingInstall by remember { mutableStateOf<WindowsPackageRef?>(null) }
    var storeStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val winStatus = remember { WindowsPackageStore.status() }
    val winBackends = remember { WindowsPackageStore.availableBackends() }
    val netnsCap = remember { AppNetnsLauncher.detectCapability() }
    LaunchedEffect(Unit) {
        installed = withContext(Dispatchers.IO) { FlatpakStore.listInstalled() }
    }
    LaunchedEffect(winBackend) {
        val backend = winBackend ?: return@LaunchedEffect
        winInstalled = withContext(Dispatchers.IO) { WindowsPackageStore.listInstalled(backend) }
    }
    LaunchedEffect(q, winBackend) {
        remote = if (q.length < 2) emptyList()
        else withContext(Dispatchers.IO) { FlatpakStore.search(q) }
        val backend = winBackend
        winRemote = if (backend == null || q.length < 2) emptyList()
        else withContext(Dispatchers.IO) { WindowsPackageStore.search(q, backend) }
    }
    val shown = remember(installed, q) {
        installed.filter { q.isBlank() || it.name.contains(q, true) || it.id.contains(q, true) }
    }
    if (showPortalPerms) {
        PortalDevicePermissionsPane(onBack = { showPortalPerms = false })
        return
    }
    val selected = permissionsFor
    if (selected != null) {
        FlatpakPermissionsPane(
            app = selected,
            onBack = { permissionsFor = null },
        )
        return
    }
    pendingInstall?.let { pkg ->
        Column(
            Modifier.fillMaxSize().background(TelosColors.surface).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(t("desktop_store_install_confirm_title"), color = TelosColors.secondary, fontSize = 18.sp)
            Text(
                t("desktop_store_install_confirm_body", pkg.name, pkg.id, pkg.source.name),
                color = TelosColors.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val ok = WindowsPackageStore.install(pkg.id, pkg.source)
                        storeStatus = if (ok) {
                            t("desktop_store_install_started", pkg.id)
                        } else {
                            t("desktop_store_install_failed", pkg.id)
                        }
                    }
                    pendingInstall = null
                }) { Text(t("action_install")) }
                TextButton(onClick = { pendingInstall = null }) { Text(t("close")) }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().background(TelosColors.surface).padding(12.dp)) {
        Text(t("hf_store_title"), color = TelosColors.secondary, fontSize = 20.sp)
        Text(t("desktop_store_backends"), color = TelosColors.secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Text(
            buildString {
                append(t("desktop_store_backend_flatpak"))
                append(": ")
                append(if (FlatpakStore.available()) t("net_hub_state_on") else t("net_hub_state_off"))
                append(" · ")
                append(t("desktop_store_backend_winget"))
                append(": ")
                append(if (WindowsPackageStore.wingetAvailable()) t("net_hub_state_on") else t("net_hub_state_off"))
                append(" · ")
                append(t("desktop_store_backend_scoop"))
                append(": ")
                append(if (WindowsPackageStore.scoopAvailable()) t("net_hub_state_on") else t("net_hub_state_off"))
            },
            color = TelosColors.onSurface.copy(alpha = 0.75f),
            fontSize = 12.sp,
        )
        when (winStatus) {
            WindowsPackageStatus.PreferenceOnly ->
                Text(t("desktop_store_windows_pref_only"), color = TelosColors.secondary, fontSize = 12.sp)
            WindowsPackageStatus.Unavailable ->
                Text(t("desktop_store_windows_unavailable"), color = TelosColors.secondary, fontSize = 12.sp)
            WindowsPackageStatus.Available -> Unit
        }
        if (!FlatpakStore.available()) {
            Text(t("desktop_flatpak_unavailable"), color = TelosColors.secondary, fontSize = 12.sp)
        }
        Text(
            when (netnsCap) {
                AppNetnsCapability.UnshareNet -> t("desktop_netns_cap_unshare")
                AppNetnsCapability.IpNetns -> t("desktop_netns_cap_ip")
                AppNetnsCapability.Unavailable -> t("desktop_netns_cap_unavailable")
            },
            color = TelosColors.onSurface.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showPortalPerms = true }) {
                Text(t("desktop_portal_device_title"))
            }
        }
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text(t("hc_filter")) }, singleLine = true)
        if (winBackends.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                winBackends.forEach { backend ->
                    val label = when (backend) {
                        WindowsPackageBackend.Winget -> t("desktop_store_backend_winget")
                        WindowsPackageBackend.Scoop -> t("desktop_store_backend_scoop")
                    }
                    FilterChip(
                        selected = winBackend == backend,
                        onClick = { winBackend = backend },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TelosColors.surfaceHigh,
                            selectedLabelColor = TelosColors.onSurface,
                        ),
                    )
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { FlatpakStore.updateAll() }) { Text(t("hc_check_for_updates")) }
        }
        storeStatus?.let {
            Text(it, color = TelosColors.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
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
                            // Flatpak owns --unshare=network; do not claim host netns here.
                            com.telos.desktop.linux.Sys.runBg("flatpak", "run", e.id)
                        },
                    )
                    Text(
                        t("desktop_store_permissions"),
                        color = TelosColors.secondary,
                        modifier = Modifier.clickable { permissionsFor = e }.padding(horizontal = 8.dp),
                    )
                    Text(
                        t("hc_delete"),
                        color = TelosColors.primary,
                        modifier = Modifier.clickable {
                            scope.launch(Dispatchers.IO) { FlatpakStore.uninstall(e.id) }
                            installed = installed.filter { it.id != e.id }
                        }.padding(start = 8.dp),
                    )
                }
            }
            if (winInstalled.isNotEmpty()) {
                item {
                    Text(t("desktop_store_windows_installed"), color = TelosColors.secondary, fontSize = 12.sp)
                }
                items(winInstalled.take(80), key = { "wi-${it.source}-${it.id}" }) { e ->
                    Text(
                        e.name + "  " + e.id + if (e.version.isNotBlank()) "  ${e.version}" else "",
                        color = TelosColors.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp),
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
            if (winRemote.isNotEmpty()) {
                item {
                    Text(t("desktop_store_windows_search"), color = TelosColors.secondary, fontSize = 12.sp)
                }
                items(winRemote.take(40), key = { "wr-${it.source}-${it.id}" }) { e ->
                    Text(
                        e.name + "  " + e.id,
                        color = TelosColors.onSurface,
                        modifier = Modifier.clickable { pendingInstall = e }.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PortalDevicePermissionsPane(onBack: () -> Unit) {
    var camera by remember {
        mutableStateOf(com.telos.desktop.session.PortalDeviceAccess.readRequested(
            com.telos.desktop.session.PortalDeviceKind.Camera,
        ))
    }
    var mic by remember {
        mutableStateOf(com.telos.desktop.session.PortalDeviceAccess.readRequested(
            com.telos.desktop.session.PortalDeviceKind.Microphone,
        ))
    }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val portalReady = com.telos.desktop.session.PortalDeviceAccess.available()
    fun labelFor(result: com.telos.desktop.session.PortalDeviceAskResult): String = when (result) {
        com.telos.desktop.session.PortalDeviceAskResult.Requested -> t("desktop_portal_device_requested")
        com.telos.desktop.session.PortalDeviceAskResult.PreferenceOnly -> t("desktop_portal_device_pref_only")
        com.telos.desktop.session.PortalDeviceAskResult.Unavailable -> t("desktop_portal_device_unavailable")
        com.telos.desktop.session.PortalDeviceAskResult.Failed -> t("desktop_portal_device_failed")
        com.telos.desktop.session.PortalDeviceAskResult.Invalid -> t("desktop_portal_device_failed")
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(TelosColors.surface)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        TextButton(onClick = onBack) { Text(t("close")) }
        Text(t("desktop_portal_device_title"), color = TelosColors.secondary, fontSize = 20.sp)
        Text(t("desktop_portal_device_hint"), color = TelosColors.onSurface.copy(alpha = 0.75f), fontSize = 12.sp)
        if (!portalReady) {
            Text(t("desktop_portal_device_pref_only"), color = TelosColors.secondary, fontSize = 12.sp)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        val next = !camera
                        val result = withContext(Dispatchers.IO) {
                            com.telos.desktop.session.PortalDeviceAccess.setRequested(
                                com.telos.desktop.session.PortalDeviceKind.Camera,
                                next,
                            )
                        }
                        camera = next
                        status = labelFor(result)
                    }
                }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(t("desktop_perm_camera"), color = TelosColors.onSurface, modifier = Modifier.weight(1f))
            Text(
                if (camera) t("net_hub_state_on") else t("net_hub_state_off"),
                color = if (camera) TelosColors.primary else TelosColors.secondary,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        val next = !mic
                        val result = withContext(Dispatchers.IO) {
                            com.telos.desktop.session.PortalDeviceAccess.setRequested(
                                com.telos.desktop.session.PortalDeviceKind.Microphone,
                                next,
                            )
                        }
                        mic = next
                        status = labelFor(result)
                    }
                }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(t("desktop_perm_microphone"), color = TelosColors.onSurface, modifier = Modifier.weight(1f))
            Text(
                if (mic) t("net_hub_state_on") else t("net_hub_state_off"),
                color = if (mic) TelosColors.primary else TelosColors.secondary,
            )
        }
        status?.let {
            Text(it, color = TelosColors.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun FlatpakPermissionsPane(
    app: FlatpakRef,
    onBack: () -> Unit,
) {
    var state by remember { mutableStateOf(FlatpakPermissionState.empty(app.id)) }
    var status by remember { mutableStateOf<String?>(null) }
    var camera by remember {
        mutableStateOf(
            com.telos.desktop.session.PortalDeviceAccess.readRequested(
                com.telos.desktop.session.PortalDeviceKind.Camera,
            ),
        )
    }
    var mic by remember {
        mutableStateOf(
            com.telos.desktop.session.PortalDeviceAccess.readRequested(
                com.telos.desktop.session.PortalDeviceKind.Microphone,
            ),
        )
    }
    val scope = rememberCoroutineScope()
    val sandboxReady = FlatpakSandbox.available()
    LaunchedEffect(app.id) {
        state = withContext(Dispatchers.IO) { FlatpakSandbox.read(app.id) }
    }
    fun applyResult(result: FlatpakOverrideResult) {
        status = when (result) {
            FlatpakOverrideResult.Applied -> t("desktop_flatpak_override_applied")
            FlatpakOverrideResult.Unavailable -> t("desktop_flatpak_unavailable")
            FlatpakOverrideResult.Failed -> t("desktop_flatpak_override_failed")
            FlatpakOverrideResult.Invalid -> t("desktop_flatpak_override_failed")
        }
    }
    fun portalLabel(result: com.telos.desktop.session.PortalDeviceAskResult): String = when (result) {
        com.telos.desktop.session.PortalDeviceAskResult.Requested -> t("desktop_portal_device_requested")
        com.telos.desktop.session.PortalDeviceAskResult.PreferenceOnly -> t("desktop_portal_device_pref_only")
        com.telos.desktop.session.PortalDeviceAskResult.Unavailable -> t("desktop_portal_device_unavailable")
        com.telos.desktop.session.PortalDeviceAskResult.Failed,
        com.telos.desktop.session.PortalDeviceAskResult.Invalid,
        -> t("desktop_portal_device_failed")
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(TelosColors.surface)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        TextButton(onClick = onBack) { Text(t("close")) }
        Text(app.name, color = TelosColors.secondary, fontSize = 20.sp)
        Text(app.id, color = TelosColors.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
        Text(t("desktop_store_permissions"), color = TelosColors.secondary, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
        if (!sandboxReady) {
            Text(t("desktop_flatpak_unavailable"), color = TelosColors.secondary)
        } else {
            Text(t("desktop_store_sandbox_tier"), color = TelosColors.onSurface, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FlatpakSandboxTier.entries.forEach { tier ->
                    val label = when (tier) {
                        FlatpakSandboxTier.Host -> t("desktop_sandbox_tier_host")
                        FlatpakSandboxTier.VaultFs -> t("desktop_sandbox_tier_vault_fs")
                        FlatpakSandboxTier.NetIsolated -> t("desktop_sandbox_tier_net_isolated")
                        FlatpakSandboxTier.Standard -> t("desktop_sandbox_tier_standard")
                    }
                    FilterChip(
                        selected = state.tier == tier,
                        onClick = {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { FlatpakSandbox.applyTier(app.id, tier) }
                                applyResult(result)
                                if (result == FlatpakOverrideResult.Applied) {
                                    state = withContext(Dispatchers.IO) { FlatpakSandbox.read(app.id) }
                                }
                            }
                        },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TelosColors.surfaceHigh,
                            selectedLabelColor = TelosColors.onSurface,
                        ),
                    )
                }
            }
            FlatpakPermission.entries.forEach { perm ->
                val enabled = state.permissions[perm] == true
                val label = when (perm) {
                    FlatpakPermission.FilesystemHome -> t("desktop_perm_filesystem_home")
                    FlatpakPermission.FilesystemHost -> t("desktop_perm_filesystem_host")
                    FlatpakPermission.Network -> t("desktop_perm_network")
                    FlatpakPermission.Gpu -> t("desktop_perm_gpu")
                    FlatpakPermission.Wayland -> t("desktop_perm_wayland")
                    FlatpakPermission.X11 -> t("desktop_perm_x11")
                    FlatpakPermission.PulseAudio -> t("desktop_perm_pulseaudio")
                    FlatpakPermission.Notifications -> t("desktop_perm_notifications")
                    FlatpakPermission.Camera -> t("desktop_perm_camera")
                    FlatpakPermission.Microphone -> t("desktop_perm_microphone")
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    FlatpakSandbox.applyPermission(app.id, perm, !enabled)
                                }
                                applyResult(result)
                                if (result == FlatpakOverrideResult.Applied) {
                                    state = withContext(Dispatchers.IO) { FlatpakSandbox.read(app.id) }
                                }
                            }
                        }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, color = TelosColors.onSurface, modifier = Modifier.weight(1f))
                    Text(
                        if (enabled) t("net_hub_state_on") else t("net_hub_state_off"),
                        color = if (enabled) TelosColors.primary else TelosColors.secondary,
                    )
                }
            }
            TextButton(
                onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { FlatpakSandbox.reset(app.id) }
                        applyResult(result)
                        if (result == FlatpakOverrideResult.Applied) {
                            state = withContext(Dispatchers.IO) { FlatpakSandbox.read(app.id) }
                        }
                    }
                },
            ) { Text(t("desktop_flatpak_reset_overrides")) }
            val netIsolate = state.tier == FlatpakSandboxTier.NetIsolated
            Text(
                if (netIsolate) {
                    when (AppNetnsLauncher.detectCapability()) {
                        AppNetnsCapability.UnshareNet,
                        AppNetnsCapability.IpNetns,
                        -> t("desktop_netns_flatpak_owned")
                        AppNetnsCapability.Unavailable -> t("desktop_netns_flatpak_no_tools")
                    }
                } else {
                    t("desktop_netns_not_isolated")
                },
                color = TelosColors.onSurface.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(t("desktop_portal_device_title"), color = TelosColors.secondary, fontSize = 16.sp, modifier = Modifier.padding(top = 16.dp))
        Text(t("desktop_portal_device_hint"), color = TelosColors.onSurface.copy(alpha = 0.75f), fontSize = 12.sp)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        val next = !camera
                        val result = withContext(Dispatchers.IO) {
                            com.telos.desktop.session.PortalDeviceAccess.setRequested(
                                com.telos.desktop.session.PortalDeviceKind.Camera,
                                next,
                            )
                        }
                        camera = next
                        status = portalLabel(result)
                    }
                }
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(t("desktop_perm_camera"), color = TelosColors.onSurface, modifier = Modifier.weight(1f))
            Text(
                if (camera) t("net_hub_state_on") else t("net_hub_state_off"),
                color = if (camera) TelosColors.primary else TelosColors.secondary,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        val next = !mic
                        val result = withContext(Dispatchers.IO) {
                            com.telos.desktop.session.PortalDeviceAccess.setRequested(
                                com.telos.desktop.session.PortalDeviceKind.Microphone,
                                next,
                            )
                        }
                        mic = next
                        status = portalLabel(result)
                    }
                }
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(t("desktop_perm_microphone"), color = TelosColors.onSurface, modifier = Modifier.weight(1f))
            Text(
                if (mic) t("net_hub_state_on") else t("net_hub_state_off"),
                color = if (mic) TelosColors.primary else TelosColors.secondary,
            )
        }
        status?.let { Text(it, color = TelosColors.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}

// === TELOS_PENDING_REVIEW_END: telos_window_drag_and_ui_fix ===
