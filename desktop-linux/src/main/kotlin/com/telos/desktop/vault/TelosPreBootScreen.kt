package com.telos.desktop.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.i18n.TelosI18n.t
import com.telos.desktop.theme.QuietOpsTokens
import com.telos.desktop.ui.UnlockTheatreLockedHeader
import java.io.File

@Composable
fun TelosPreBootScreen(
    engine: PhantomVaultEngine,
    onUnlocked: () -> Unit,
) {
    val portableShell = remember {
        com.telos.desktop.platform.PlatformEnvironment.settings.showPortableDesktopShell
    }
    val bootModes = remember(portableShell) {
        if (portableShell) {
            listOf(
                BootMode.PortableData,
                BootMode.PortableVault,
                BootMode.UnlockExisting,
                BootMode.CreateWizard,
                BootMode.HostHome,
            )
        } else {
            BootMode.entries.toList()
        }
    }
    var mode by remember {
        mutableStateOf(
            if (portableShell) BootMode.PortableData else BootMode.UnlockExisting,
        )
    }
    var password by remember { mutableStateOf("") }
    var outer by remember { mutableStateOf("") }
    var inner by remember { mutableStateOf("") }
    var duress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    // Path-only defaults: do not mkdir carriers / flip portable-active until unlock or create.
    var carrier by remember {
        mutableStateOf<File?>(
            if (portableShell) PortableStegoFs.carriersRoot() else null,
        )
    }
    var cloudRemote by remember { mutableStateOf("") }
    val hw = remember { HardwareCryptoAccelerator.probe() }
    val portableRoot = remember { PortableTraceGuard.dataRoot().absolutePath }
    val portableCarriers = remember { PortableStegoFs.carriersRoot().absolutePath }

    // Unlock theatre: Quiet Ops charcoal + vault glyph only — never user wallpaper.
    Box(Modifier.fillMaxSize().background(QuietOpsTokens.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(24.dp)) {
            UnlockTheatreLockedHeader()
            Text(hw.label, color = QuietOpsTokens.accent, fontSize = 13.sp)
            if (portableShell) {
                Text(t("desktop_portable_data_root"), color = QuietOpsTokens.onSurface.copy(0.75f), fontSize = 11.sp)
                Text(portableRoot, color = QuietOpsTokens.onSurface.copy(0.6f), fontSize = 10.sp)
                Text("Stego carriers: $portableCarriers", color = QuietOpsTokens.onSurface.copy(0.55f), fontSize = 10.sp)
            }
                        VaultProfileSwitchPanel(compact = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bootModes.forEach { m ->
                    Button(onClick = { mode = m; engine.bootMode = m }) {
                        Text(if (mode == m) "• ${m.name}" else m.name, fontSize = 11.sp)
                    }
                }
            }
            Text(bootModeDescription(mode), color = QuietOpsTokens.onSurface)
            if (mode == BootMode.HostHome && portableShell) {
                Text(t("desktop_host_home_trace_warning"), color = QuietOpsTokens.accent, fontSize = 12.sp)
            }
            if (mode == BootMode.UnlockExisting || mode == BootMode.CreateWizard || mode == BootMode.PortableVault) {
                Button(onClick = { picker = true }) {
                    Text(carrier?.absolutePath ?: "Select carrier file or folder")
                }
                if (mode != BootMode.PortableVault) {
                    Button(onClick = {
                        val rem = cloudRemote.ifBlank {
                            engine.cloud.listRcloneRemotes().firstOrNull()
                                ?: engine.cloud.detectLocalCloud().firstOrNull()?.absolutePath
                        }
                        if (rem != null) {
                            val dest = carrier
                                ?: if (portableShell) PortableStegoFs.ensureCarriersRoot()
                                else File(System.getProperty("user.home"), "Music")
                            engine.cloud.pullLatest(dest, rem)
                        }
                    }) { Text("Pull latest vault from cloud") }
                    OutlinedTextField(
                        cloudRemote,
                        { cloudRemote = it },
                        label = { Text("rclone remote or local cloud path") },
                        modifier = Modifier.width(420.dp),
                    )
                }
            }
            if (mode == BootMode.CreateWizard) {
                OutlinedTextField(outer, { outer = it }, label = { Text("Outer decoy password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.width(360.dp))
                OutlinedTextField(inner, { inner = it }, label = { Text("Inner shadow password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.width(360.dp))
                OutlinedTextField(duress, { duress = it }, label = { Text("Duress / panic password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.width(360.dp))
            } else if (mode.needsVaultPassword()) {
                OutlinedTextField(password, { password = it }, label = { Text("Vault password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.width(360.dp), singleLine = true)
            }
            error?.let { Text(it, color = QuietOpsTokens.accent) }
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    engine.bootMode = mode
                    val defaultPool =
                        if (portableShell) PortableStegoFs.ensureCarriersRoot()
                        else File(System.getProperty("user.home"), "Music")
                    val result = when (mode) {
                        BootMode.CreateWizard -> engine.createElastic(outer, inner, duress, carrier ?: defaultPool)
                        BootMode.HostHome -> engine.unlock("host", null, false)
                        BootMode.PortableData -> engine.unlock("", null, false)
                        BootMode.PortableVault -> {
                            val pool = carrier ?: PortableStegoFs.ensureCarriersRoot()
                            if (engine.shards.listCarriers(pool).isNotEmpty() ||
                                File(pool, StegoCarrierManifest.FILE_NAME).isFile
                            ) {
                                engine.openStegoCarriers(password, pool)
                            } else {
                                engine.unlock(password, null, true)
                            }
                        }
                        BootMode.UnlockExisting -> {
                            val pool = carrier
                            if (pool != null && (pool.isDirectory || pool.isFile)) {
                                engine.openStegoCarriers(password, pool)
                            } else {
                                engine.unlock(password, carrier, true)
                            }
                        }
                        else -> engine.unlock(password, carrier, true)
                    }
                    busy = false
                    if (result.ok) onUnlocked()
                    else error = (result as? LuksVaultManager.UnlockResult.Failed)?.message ?: "Unlock failed"
                },
            ) { Text(if (busy) "Working…" else "Continue") }
            Text("Ctrl+Alt+L panic unmount · USB dead-man · ulimit -c 0", color = QuietOpsTokens.muted, fontSize = 11.sp)
        }
        if (picker) {
            Box(Modifier.align(Alignment.CenterEnd).padding(24.dp)) {
                TelosFilePickerModal(
                    onPick = { carrier = it; picker = false },
                    onDismiss = { picker = false },
                )
            }
        }
    }
}

private fun BootMode.needsVaultPassword(): Boolean =
    this == BootMode.UnlockExisting ||
        this == BootMode.PortableVault ||
        this == BootMode.EphemeralGhost

@Composable
private fun bootModeDescription(mode: BootMode): String = when (mode) {
    BootMode.PortableData -> t("desktop_boot_portable_data")
    BootMode.PortableVault -> t("desktop_boot_portable_vault")
    BootMode.HostHome -> t("desktop_boot_host_home")
    BootMode.UnlockExisting -> t("desktop_boot_unlock_existing")
    BootMode.CreateWizard -> t("desktop_boot_create_wizard")
    BootMode.EphemeralGhost -> t("desktop_boot_ephemeral")
}
