package com.telos.desktop.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telos.desktop.linux.Sys
import com.telos.desktop.ui.TelosColors
import com.telos.desktop.vault.LuksVaultManager

object SessionPower {
    var open = mutableStateOf(false)
    var locked = mutableStateOf(false)
}

object SessionPowerActions {
    fun lock() {
        SessionPower.locked.value = true
        SessionPower.open.value = false
    }

    fun logout(vault: LuksVaultManager, exit: () -> Unit) {
        vault.lock()
        exit()
    }

    fun suspend(vault: LuksVaultManager) {
        vault.lock()
        Sys.run("systemctl", "suspend", timeoutSec = 8)
    }

    fun reboot(vault: LuksVaultManager) {
        vault.lock()
        Sys.run("systemctl", "reboot", timeoutSec = 8)
    }

    fun poweroff(vault: LuksVaultManager) {
        vault.lock()
        Sys.run("systemctl", "poweroff", timeoutSec = 8)
    }
}

@Composable
fun SessionPowerDialog(vault: LuksVaultManager, onLogout: () -> Unit) {
    if (!SessionPower.open.value) return
    Column(Modifier.width(280.dp).background(TelosColors.surfaceContainer).padding(16.dp)) {
        Text("Power", color = TelosColors.secondary, modifier = Modifier.padding(bottom = 8.dp))
        Button(onClick = { SessionPowerActions.lock() }) { Text("Lock Screen") }
        Button(onClick = { SessionPowerActions.suspend(vault) }) { Text("Suspend") }
        Button(onClick = { SessionPowerActions.reboot(vault) }) { Text("Reboot") }
        Button(onClick = { SessionPowerActions.poweroff(vault) }) { Text("Power Off") }
        Button(onClick = { SessionPowerActions.logout(vault, onLogout) }) { Text("Logout") }
        Button(onClick = { SessionPower.open.value = false }) { Text("Cancel") }
    }
}
