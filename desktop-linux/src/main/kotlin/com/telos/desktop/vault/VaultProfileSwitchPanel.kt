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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    vaultUnlocked: Boolean = runCatching { PhantomVault.engine.unlocked }.getOrDefault(false),
    lockVault: (() -> Unit)? = {
        runCatching { PhantomVault.engine.lock(syncCloud = false) }
        Unit
    },
    onChanged: (() -> Unit)? = null,
) {
    var tick by remember { mutableStateOf(0) }
    val entries = remember(tick, vaultConfigDir) { DesktopProfiles.listVaultEntries(vaultConfigDir) }
    val active = remember(tick, vaultConfigDir) { DesktopProfiles.activeProfile(vaultConfigDir) }
    var newName by remember { mutableStateOf("") }
    var portablePath by remember { mutableStateOf("") }
    var reauthPassword by remember { mutableStateOf("") }
    var exportPass by remember { mutableStateOf("") }
    var importPass by remember { mutableStateOf("") }
    var importPath by remember { mutableStateOf("") }
    var importConfirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val showPortable = remember {
        PlatformEnvironment.settings.showPortableDesktopShell || PortableTraceGuard.isPortable()
    }
    val shape = remember { RoundedCornerShape(10.dp) }
    val currentHost = remember { DesktopProfiles.currentHostFingerprint() }

    fun refresh(ok: String? = null, err: String? = null) {
        tick++
        message = ok
        error = err
        onChanged?.invoke()
    }

    fun passwordAuthGate(): (() -> Boolean)? {
        if (!vaultUnlocked) return null
        return { reauthPassword.isNotEmpty() }
    }

    fun handleSwitchOutcome(outcome: DesktopProfiles.SwitchOutcome, successLabel: String) {
        when (outcome) {
            is DesktopProfiles.SwitchOutcome.Switched -> {
                reauthPassword = ""
                val lockedNote = if (outcome.lockedFirst) {
                    " " + t("desktop_vos_profiles_locked_need_unlock")
                } else ""
                refresh(successLabel + lockedNote)
            }
            is DesktopProfiles.SwitchOutcome.Failed -> {
                val err = when (outcome.error) {
                    is DesktopProfiles.ProfileError.AuthRequired,
                    is DesktopProfiles.ProfileError.AuthFailed,
                    -> t("desktop_vos_profiles_auth_required")
                    is DesktopProfiles.ProfileError.InvalidName -> t("desktop_vos_profiles_invalid_name")
                    else -> outcome.error.message
                }
                refresh(err = err)
            }
        }
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
        Text(t("desktop_vos_profiles_host_current", currentHost.label), color = TelosColors.onSurface.copy(0.65f), fontSize = 11.sp)
        if (vaultUnlocked) {
            Text(t("desktop_vos_profiles_lock_gate_hint"), color = TelosColors.onSurface.copy(0.7f), fontSize = 11.sp)
            OutlinedTextField(
                reauthPassword,
                { reauthPassword = it },
                label = { Text(t("desktop_vos_profiles_reauth")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
                    if (entry.kind == DesktopProfiles.VaultKind.NamedProfile) {
                        when (entry.hostAwareness) {
                            DesktopProfiles.HostAwareness.DifferentHost -> Text(
                                t("desktop_vos_profiles_host_other", entry.hostLabel ?: t("desktop_vos_profiles_none")),
                                color = TelosColors.primary, fontSize = 10.sp,
                            )
                            DesktopProfiles.HostAwareness.SameHost -> entry.hostLabel?.let {
                                Text(t("desktop_vos_profiles_host_same", it), color = TelosColors.onSurface.copy(0.55f), fontSize = 10.sp)
                            }
                            DesktopProfiles.HostAwareness.Unknown -> Unit
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (entry.kind) {
                            DesktopProfiles.VaultKind.NamedProfile -> {
                                TextButton(
                                    onClick = {
                                        val outcome = DesktopProfiles.switchToProfileLockGated(
                                            name = entry.displayName,
                                            vaultUnlocked = vaultUnlocked,
                                            passwordAuth = passwordAuthGate(),
                                            lockVault = lockVault,
                                            vaultConfigDir = vaultConfigDir,
                                        )
                                        handleSwitchOutcome(outcome, t("desktop_vos_profiles_switched", entry.displayName))
                                    },
                                    enabled = !entry.isActive,
                                ) { Text(t("desktop_vos_profiles_switch")) }
                                TextButton(
                                    onClick = {
                                        if (exportPass.isEmpty()) {
                                            refresh(err = t("desktop_vos_profiles_export_pass_required"))
                                            return@TextButton
                                        }
                                        val pass = exportPass.toCharArray()
                                        val dest = PortableProfileExport.defaultExportFile(entry.displayName)
                                        val result = PortableProfileExport.exportProfile(entry.displayName, dest, pass, vaultConfigDir)
                                        pass.fill('\u0000')
                                        exportPass = ""
                                        if (result.isSuccess) refresh(t("desktop_vos_profiles_exported", dest.name))
                                        else refresh(err = result.exceptionOrNull()?.message)
                                    },
                                ) { Text(t("desktop_vos_profiles_export")) }
                            }
                            DesktopProfiles.VaultKind.PortableRoot -> TextButton(
                                onClick = {
                                    val outcome = DesktopProfiles.selectPortableVaultRootLockGated(
                                        root = entry.directory,
                                        vaultUnlocked = vaultUnlocked,
                                        passwordAuth = passwordAuthGate(),
                                        lockVault = lockVault,
                                    )
                                    handleSwitchOutcome(outcome, t("desktop_vos_profiles_portable_selected", entry.displayName))
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
                val outcome = DesktopProfiles.switchToProfileLockGated(
                    name = newName,
                    vaultUnlocked = vaultUnlocked,
                    passwordAuth = passwordAuthGate(),
                    lockVault = lockVault,
                    vaultConfigDir = vaultConfigDir,
                )
                when (outcome) {
                    is DesktopProfiles.SwitchOutcome.Switched -> {
                        val name = outcome.folder?.name ?: newName
                        newName = ""
                        handleSwitchOutcome(outcome, t("desktop_vos_profiles_created", name))
                    }
                    is DesktopProfiles.SwitchOutcome.Failed -> handleSwitchOutcome(outcome, "")
                }
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
                    val outcome = DesktopProfiles.selectPortableVaultRootLockGated(
                        root = root,
                        vaultUnlocked = vaultUnlocked,
                        passwordAuth = passwordAuthGate(),
                        lockVault = lockVault,
                    )
                    when (outcome) {
                        is DesktopProfiles.SwitchOutcome.Switched -> {
                            portablePath = ""
                            handleSwitchOutcome(outcome, t("desktop_vos_profiles_portable_selected", root.name.ifBlank { root.absolutePath }))
                        }
                        is DesktopProfiles.SwitchOutcome.Failed -> {
                            if (outcome.error is DesktopProfiles.ProfileError.AuthRequired ||
                                outcome.error is DesktopProfiles.ProfileError.AuthFailed
                            ) handleSwitchOutcome(outcome, "")
                            else refresh(err = t("desktop_vos_profiles_portable_reject"))
                        }
                    }
                },
                enabled = portablePath.isNotBlank(),
            ) { Text(t("desktop_vos_profiles_add_portable")) }
        }
        Text(t("desktop_vos_profiles_export_hint"), color = TelosColors.onSurface.copy(0.7f), fontSize = 11.sp)
        OutlinedTextField(exportPass, { exportPass = it }, label = { Text(t("desktop_vos_profiles_export_pass")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(importPath, { importPath = it }, label = { Text(t("desktop_vos_profiles_import_path")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(importPass, { importPass = it }, label = { Text(t("desktop_vos_profiles_import_pass")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { importConfirm = !importConfirm }) {
                Text(if (importConfirm) t("desktop_vos_profiles_import_confirmed") else t("desktop_vos_profiles_import_confirm"))
            }
            Button(
                onClick = {
                    if (!importConfirm) { refresh(err = t("desktop_vos_profiles_import_need_confirm")); return@Button }
                    if (importPass.isEmpty() || importPath.isBlank()) { refresh(err = t("desktop_vos_profiles_export_pass_required")); return@Button }
                    val pass = importPass.toCharArray()
                    val result = PortableProfileExport.importProfile(
                        src = File(importPath.trim()),
                        passphrase = pass,
                        confirmed = true,
                        vaultConfigDir = vaultConfigDir,
                        activate = false,
                    )
                    pass.fill('\u0000')
                    importPass = ""
                    importConfirm = false
                    if (result.isSuccess) {
                        importPath = ""
                        refresh(t("desktop_vos_profiles_imported", result.getOrNull()!!.name))
                    } else refresh(err = result.exceptionOrNull()?.message)
                },
                enabled = importPath.isNotBlank() && importPass.isNotBlank(),
            ) { Text(t("desktop_vos_profiles_import")) }
        }
        message?.let { Text(it, color = TelosColors.secondary, fontSize = 12.sp) }
        error?.let { Text(it, color = TelosColors.primary, fontSize = 12.sp) }
    }
}
