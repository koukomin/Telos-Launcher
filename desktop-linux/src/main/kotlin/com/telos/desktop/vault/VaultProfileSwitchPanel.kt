package com.telos.desktop.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.i18n.TelosI18n.t
import com.telos.desktop.platform.PlatformEnvironment
import com.telos.desktop.ui.TelosColors
import java.io.File

@Composable
fun VaultProfileSwitchPanel(
    vaultConfigDir: File? = null,
    compact: Boolean = false,
    onChanged: (() -> Unit)? = null,
) {
    var tick by remember { mutableStateOf(0) }
    val entries = remember(tick, vaultConfigDir) { DesktopProfiles.listVaultEntries(vaultConfigDir) }
    val active = remember(tick, vaultConfigDir) { DesktopProfiles.activeProfile(vaultConfigDir) }
    var newName by remember { mutableStateOf("") }
    var portablePath by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val showPortable = remember {
        PlatformEnvironment.settings.showPortableDesktopShell || PortableTraceGuard.isPortable()
    }
    val shape = remember { RoundedCornerShape(10.dp) }

    fun refresh(ok: String? = null, err: String? = null) {
        tick++
        message = ok
        error = err
        onChanged?.invoke()
    }

    Column(
        Modifier.fillMaxWidth()
            .then(if (compact) Modifier else Modifier.verticalScroll(rememberScrollState()))
            .widthIn(max = 520.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(t("desktop_vos_profiles_title"), color = TelosColors.secondary, fontSize = if (compact) 14.sp else 16.sp)
        Text(t("desktop_vos_profiles_summary"), color = TelosColors.onSurface.copy(0.75f), fontSize = 11.sp)
        Text(t("desktop_vos_profiles_active", active ?: t("desktop_vos_profiles_none")), color = TelosColors.primary, fontSize = 12.sp)
        if (entries.isEmpty()) {
            Text(t("desktop_vos_profiles_empty"), color = TelosColors.onSurface.copy(0.65f), fontSize = 12.sp)
        } else {
            entries.forEach { entry ->
                val border = if (entry.isActive) TelosColors.secondary else TelosColors.outline
                Column(
                    Modifier.fillMaxWidth().clip(shape).background(TelosColors.surfaceContainer)
                        .border(1.dp, border, shape).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(buildString { append(entry.displayName); if (entry.isActive) append(" ✓") }, color = TelosColors.onSurface, fontSize = 13.sp)
                    Text(
                        when (entry.kind) {
                            DesktopProfiles.VaultKind.NamedProfile -> t("desktop_vos_profiles_kind_named")
                            DesktopProfiles.VaultKind.PortableRoot -> t("desktop_vos_profiles_kind_portable")
                        },
                        color = TelosColors.onSurface.copy(0.65f), fontSize = 11.sp,
                    )
                    Text(entry.directory.absolutePath, color = TelosColors.onSurface.copy(0.5f), fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (entry.kind) {
                            DesktopProfiles.VaultKind.NamedProfile -> TextButton(
                                onClick = {
                                    val result = DesktopProfiles.switchToProfile(entry.displayName, vaultConfigDir)
                                    if (result.isSuccess) refresh(t("desktop_vos_profiles_switched", entry.displayName))
                                    else refresh(err = result.exceptionOrNull()?.message)
                                },
                                enabled = !entry.isActive,
                            ) { Text(t("desktop_vos_profiles_switch")) }
                            DesktopProfiles.VaultKind.PortableRoot -> TextButton(
                                onClick = {
                                    if (DesktopProfiles.selectPortableVaultRoot(entry.directory)) {
                                        refresh(t("desktop_vos_profiles_portable_selected", entry.displayName))
                                    } else refresh(err = t("desktop_vos_profiles_portable_reject"))
                                },
                                enabled = !entry.isActive,
                            ) { Text(t("desktop_vos_profiles_use_portable")) }
                        }
                    }
                }
            }
        }
        OutlinedTextField(newName, { newName = it }, label = { Text(t("desktop_vos_profiles_new_name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                val result = DesktopProfiles.switchToProfile(newName, vaultConfigDir)
                if (result.isSuccess) {
                    val name = result.getOrNull()!!.name
                    newName = ""
                    refresh(t("desktop_vos_profiles_created", name))
                } else refresh(err = t("desktop_vos_profiles_invalid_name"))
            },
            enabled = newName.isNotBlank(),
        ) { Text(t("desktop_vos_profiles_create")) }
        if (showPortable) {
            Text(t("desktop_vos_profiles_portable_hint"), color = TelosColors.onSurface.copy(0.7f), fontSize = 11.sp)
            OutlinedTextField(portablePath, { portablePath = it }, label = { Text(t("desktop_vos_profiles_portable_path")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val root = File(portablePath.trim())
                    if (portablePath.isBlank()) { refresh(err = t("desktop_vos_profiles_portable_reject")); return@Button }
                    if (DesktopProfiles.selectPortableVaultRoot(root)) {
                        portablePath = ""
                        refresh(t("desktop_vos_profiles_portable_selected", root.name.ifBlank { root.absolutePath }))
                    } else refresh(err = t("desktop_vos_profiles_portable_reject"))
                },
                enabled = portablePath.isNotBlank(),
            ) { Text(t("desktop_vos_profiles_add_portable")) }
        }
        message?.let { Text(it, color = TelosColors.secondary, fontSize = 12.sp) }
        error?.let { Text(it, color = TelosColors.primary, fontSize = 12.sp) }
    }
}
