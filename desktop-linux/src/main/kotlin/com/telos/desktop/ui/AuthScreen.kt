package com.telos.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
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
import com.telos.desktop.vault.LuksVaultManager

@Composable
fun AuthScreen(
    vault: LuksVaultManager,
    onUnlocked: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(TelosColors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text("Telos Vault", color = TelosColors.secondary, fontSize = 28.sp)
            Text("Enter the master password to unlock the encrypted desktop vault.", color = TelosColors.onSurface)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text("Master password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.width(360.dp),
            )
            if (error != null) {
                Text(error!!, color = TelosColors.primary)
            }
            Button(
                enabled = password.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    val result = vault.unlock(password, initializeIfMissing = true)
                    busy = false
                    if (result.ok) onUnlocked()
                    else error = (result as? LuksVaultManager.UnlockResult.Failed)?.message
                        ?: "Unlock failed. cryptsetup, udisksctl or pkexec is required."
                },
            ) {
                Text(if (busy) "Unlocking…" else "Unlock vault")
            }
            Text(
                "Vault image: ${vault.vaultImage.absolutePath}\nMount: ${vault.mountPoint.absolutePath}",
                color = TelosColors.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
    }
}
