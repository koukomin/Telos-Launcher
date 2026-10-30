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
import com.telos.desktop.ui.TelosColors
import java.io.File

@Composable
fun TelosPreBootScreen(
    engine: PhantomVaultEngine,
    onUnlocked: () -> Unit,
) {
    var mode by remember { mutableStateOf(BootMode.UnlockExisting) }
    var password by remember { mutableStateOf("") }
    var outer by remember { mutableStateOf("") }
    var inner by remember { mutableStateOf("") }
    var duress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var carrier by remember { mutableStateOf<File?>(null) }
    var cloudRemote by remember { mutableStateOf("") }
    val hw = remember { HardwareCryptoAccelerator.probe() }

    Box(Modifier.fillMaxSize().background(TelosColors.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(24.dp)) {
            Text("Telos Phantom Vault", color = TelosColors.secondary, fontSize = 28.sp)
            Text(hw.label, color = TelosColors.primary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BootMode.entries.forEach { m ->
                    Button(onClick = { mode = m; engine.bootMode = m }) { Text(if (mode == m) "• ${m.name}" else m.name, fontSize = 11.sp) }
                }
            }
            Text(
                when (mode) {
                    BootMode.HostHome -> "Host Home Folder (Standard)"
                    BootMode.UnlockExisting -> "Unlock Existing Vault (file or stego folder)"
                    BootMode.CreateWizard -> "Create New Elastic / Stego / Deniable Vault"
                    BootMode.EphemeralGhost -> "Ephemeral RAM-Only Ghost Session (tmpfs)"
                },
                color = TelosColors.onSurface,
            )
            if (mode == BootMode.UnlockExisting || mode == BootMode.CreateWizard) {
                Button(onClick = { picker = true }) { Text(carrier?.absolutePath ?: "Select carrier file or folder") }
                Button(onClick = {
                    val rem = cloudRemote.ifBlank { engine.cloud.listRcloneRemotes().firstOrNull() ?: engine.cloud.detectLocalCloud().firstOrNull()?.absolutePath }
                    if (rem != null) {
                        val dest = carrier ?: File(System.getProperty("user.home"), "Music")
                        engine.cloud.pullLatest(dest, rem)
                    }
                }) { Text("Pull latest vault from cloud") }
                OutlinedTextField(cloudRemote, { cloudRemote = it }, label = { Text("rclone remote or local cloud path") }, modifier = Modifier.width(420.dp))
            }
            if (mode == BootMode.CreateWizard) {
                OutlinedTextField(outer, { outer = it }, label = { Text("Outer decoy password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.width(360.dp))
                OutlinedTextField(inner, { inner = it }, label = { Text("Inner shadow password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.width(360.dp))
                OutlinedTextField(duress, { duress = it }, label = { Text("Duress / panic password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.width(360.dp))
            } else if (mode != BootMode.HostHome) {
                OutlinedTextField(password, { password = it }, label = { Text("Vault password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.width(360.dp), singleLine = true)
            }
            error?.let { Text(it, color = TelosColors.primary) }
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    engine.bootMode = mode
                    val result = when (mode) {
                        BootMode.CreateWizard -> engine.createElastic(outer, inner, duress, carrier ?: File(System.getProperty("user.home"), "Music"))
                        BootMode.HostHome -> engine.unlock("host", null, false)
                        else -> engine.unlock(password, carrier, true)
                    }
                    busy = false
                    if (result.ok) onUnlocked()
                    else error = (result as? LuksVaultManager.UnlockResult.Failed)?.message ?: "Unlock failed"
                },
            ) { Text(if (busy) "Working…" else "Continue") }
            Text("Ctrl+Alt+L panic unmount · USB dead-man · ulimit -c 0", color = TelosColors.onSurface.copy(0.5f), fontSize = 11.sp)
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
