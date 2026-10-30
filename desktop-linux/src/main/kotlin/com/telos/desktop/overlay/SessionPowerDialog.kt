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
import com.telos.desktop.vault.PhantomVaultEngine

object SessionPower {
    var open = mutableStateOf(false)
    var locked = mutableStateOf(false)
}

object SessionPowerActions {
    fun lock() {
        SessionPower.locked.value = true
        SessionPower.open.value = false
        Sys.run("loginctl", "lock-session", timeoutSec = 4)
    }

    // === TELOS_PENDING_REVIEW_START: telos_elastic_sharded_pqc_vault ===
    fun logout(vault: PhantomVaultEngine, exit: () -> Unit) {
        vault.lock(syncCloud = true)
        Sys.run("labwc", "--exit", timeoutSec = 5)
        exit()
    }

    fun suspend(vault: PhantomVaultEngine) {
        vault.lock(syncCloud = true)
        Sys.run("systemctl", "suspend", timeoutSec = 8)
    }

    fun reboot(vault: PhantomVaultEngine) {
        vault.lock(syncCloud = true)
        Sys.run("labwc", "--exit", timeoutSec = 5)
        Sys.run("systemctl", "reboot", timeoutSec = 8)
    }

    fun poweroff(vault: PhantomVaultEngine) {
        vault.lock(syncCloud = true)
        Sys.run("labwc", "--exit", timeoutSec = 5)
        Sys.run("systemctl", "poweroff", timeoutSec = 8)
    }
    // === TELOS_PENDING_REVIEW_END: telos_elastic_sharded_pqc_vault ===
}

@Composable
fun SessionPowerDialog(vault: PhantomVaultEngine, onLogout: () -> Unit) {
    if (!SessionPower.open.value) return
    Column(Modifier.width(280.dp).background(TelosColors.surfaceContainer).padding(16.dp)) {
        Text(com.telos.desktop.i18n.TelosI18n.t("telos_system_label"), color = TelosColors.secondary, modifier = Modifier.padding(bottom = 8.dp))
        Button(onClick = { SessionPowerActions.lock() }) { Text(com.telos.desktop.i18n.TelosI18n.t("hc_lock_vault")) }
        Button(onClick = { SessionPowerActions.suspend(vault) }) { Text(com.telos.desktop.i18n.TelosI18n.t("freeze_dashboard_state_suspended")) }
        Button(onClick = { SessionPowerActions.reboot(vault) }) { Text("Reboot") }
        Button(onClick = { SessionPowerActions.poweroff(vault) }) { Text("Power Off") }
        Button(onClick = { SessionPowerActions.logout(vault, onLogout) }) { Text(com.telos.desktop.i18n.TelosI18n.t("action_quit")) }
        Button(onClick = { SessionPower.open.value = false }) { Text(com.telos.desktop.i18n.TelosI18n.t("hc_cancel")) }
    }
}
