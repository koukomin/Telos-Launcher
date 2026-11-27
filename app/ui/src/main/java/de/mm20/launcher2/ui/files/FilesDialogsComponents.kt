package de.mm20.launcher2.ui.files

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.component.TelosCyberHeading
import kotlinx.coroutines.launch

internal sealed interface FilesDialog {
    data object NewFolder : FilesDialog
    data object NewFile : FilesDialog
    data object Sort : FilesDialog
    data object EnableRoot : FilesDialog
    data class Rename(val entry: FsEntry) : FilesDialog
    data class BatchRename(val entries: List<FsEntry>) : FilesDialog
    data class Delete(val entries: List<FsEntry>) : FilesDialog
    data class Compress(val entries: List<FsEntry>) : FilesDialog
    data class Properties(val entry: FsEntry) : FilesDialog
    data class Archive(val entry: FsEntry) : FilesDialog
    data class Unlock(val entry: FsEntry) : FilesDialog
    data class ShareLarge(val entries: List<FsEntry>, val bytes: Long) : FilesDialog
    data class TelosDrop(val path: String) : FilesDialog
}

@Composable
internal fun FilesDialogs(vm: FilesViewModel, dialog: FilesDialog?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    when (dialog) {
        null -> {}
        FilesDialog.NewFolder -> NameDialog(stringResource(I18nR.string.hc_new_folder), "", stringResource(I18nR.string.hf_files_create), onDismiss) { vm.newFolder(it); onDismiss() }
        FilesDialog.NewFile -> NameDialog(stringResource(I18nR.string.hc_new_file), "", stringResource(I18nR.string.hf_files_create), onDismiss) { vm.newFile(it); onDismiss() }
        is FilesDialog.Rename -> NameDialog(stringResource(I18nR.string.hc_rename), dialog.entry.name, stringResource(I18nR.string.hc_rename), onDismiss) { vm.rename(dialog.entry, it); onDismiss() }
        is FilesDialog.BatchRename -> BatchRenameDialog(vm, dialog.entries, onDismiss)
        is FilesDialog.Compress -> CompressDialog(vm, dialog.entries, onDismiss)
        is FilesDialog.Delete -> de.mm20.launcher2.ui.trash.TrashDeleteDialog(
            count = dialog.entries.size,
            canTrash = vm.canTrash(dialog.entries),
            onTrash = { vm.moveToTrash(dialog.entries) },
            onPermanent = { vm.delete(dialog.entries) },
            onDismiss = onDismiss,
            warning = if (dialog.entries.any { FileActions.isSystemPath(it.path, vm.rootMode) }) stringResource(I18nR.string.hf_files_delete_system_warning) else null,
        )
        is FilesDialog.ShareLarge -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au9_share_large_title))  } },
            text = { Text(stringResource(I18nR.string.au9_share_large_message, formatSize(dialog.bytes))) },
            confirmButton = { TextButton(onClick = { vm.shareEntries(dialog.entries); onDismiss() }) { Text(stringResource(I18nR.string.au9_share_large_confirm)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
        )
        FilesDialog.Sort -> SortDialog(vm, onDismiss)
        FilesDialog.EnableRoot -> RootWarningDialog(onDismiss) { vm.enableRoot { onDismiss() } }
        is FilesDialog.Archive -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { TelosCyberHeading {  Text(dialog.entry.name)  } },
            text = { Text(stringResource(I18nR.string.hc_look_inside_the_archive_or_unpack_it_int)) },
            confirmButton = { TextButton(onClick = { vm.open(ArchivePath.build(dialog.entry.path, "/")); onDismiss() }) { Text(stringResource(I18nR.string.hc_browse)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.extract(dialog.entry); onDismiss() }) { Text(stringResource(I18nR.string.hc_extract_here)) }
                    TextButton(onClick = { scope.launch { FileActions.openWith(context, dialog.entry, vm.rootMode, vm.shizukuMode); onDismiss() } }) { Text(stringResource(I18nR.string.hc_open_with_2)) }
                }
            },
        )
        is FilesDialog.Unlock -> UnlockVaultDialog(vm, dialog.entry, onDismiss)
        is FilesDialog.Properties -> PropertiesDialog(vm, dialog.entry, onDismiss)
        is FilesDialog.TelosDrop -> FilesSharingDialog(dialog.path, onDismiss)
    }
}

/** Asks for the password of an encrypted archive. A wrong password keeps the dialog open. */
@Composable
internal fun ArchivePasswordDialog(vm: FilesViewModel, prompt: ArchivePrompt) {
    var password by remember(prompt) { mutableStateOf("") }
    var visible by remember(prompt) { mutableStateOf(false) }
    var error by remember(prompt) { mutableStateOf<String?>(null) }
    var busy by remember(prompt) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) vm.cancelArchivePrompt() },
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au21_arch_pw_title))  } },
        text = {
            Column {
                Text(stringResource(I18nR.string.au21_arch_pw_info, nameOf(prompt.archive)))
                OutlinedTextField(
                    password, { password = it; error = null }, label = { Text(stringResource(I18nR.string.hc_password)) }, singleLine = true,
                    visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(stringResource(if (visible) I18nR.string.au21_arch_hide else I18nR.string.au21_arch_show)) } },
                    isError = error != null, supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty() && !busy, onClick = {
                busy = true
                vm.unlockArchive(password.toCharArray(), onError = { error = it; busy = false }, onDone = { busy = false })
            }) { Text(if (busy) stringResource(I18nR.string.au21_arch_checking) else stringResource(I18nR.string.au21_arch_ok)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { vm.cancelArchivePrompt() }) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}

/** Name, format (zip, 7z, tar.gz) and, for zip, an optional AES-256 password */
@Composable
internal fun CompressDialog(vm: FilesViewModel, entries: List<FsEntry>, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(entries.first().name.substringBeforeLast('.')) }
    var format by remember { mutableStateOf(CompressFormat.Zip) }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var split by remember { mutableStateOf(false) }
    var partMiB by remember { mutableStateOf("100") }
    val splitBytes = if (format == CompressFormat.Zip) splitZipBytes(split, partMiB) else null
    val splitValid = format != CompressFormat.Zip || !split || splitBytes != null
    val mismatch = format.supportsPassword && password.isNotEmpty() && password != repeat
    val hide = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au21_arch_compress))  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(I18nR.string.au21_arch_format), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    CompressFormat.values().forEach { f ->
                        Row(Modifier.clickable { format = f }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = format == f, onClick = { format = f })
                            Text(stringResource(when (f) {
                                CompressFormat.Zip -> I18nR.string.au21_arch_fmt_zip
                                CompressFormat.SevenZ -> I18nR.string.au21_arch_fmt_7z
                                CompressFormat.TarGz -> I18nR.string.au21_arch_fmt_targz
                            }))
                        }
                    }
                }
                if (format == CompressFormat.Zip) SplitZipOptions(split, { split = it }, partMiB, { partMiB = it })
                if (format.supportsPassword) {
                    OutlinedTextField(
                        password, { password = it }, label = { Text(stringResource(I18nR.string.au21_arch_pw_optional)) }, singleLine = true,
                        visualTransformation = hide,
                        trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(stringResource(if (visible) I18nR.string.au21_arch_hide else I18nR.string.au21_arch_show)) } },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    if (password.isNotEmpty()) {
                        OutlinedTextField(
                            repeat, { repeat = it }, label = { Text(stringResource(I18nR.string.au21_arch_pw_repeat)) }, singleLine = true,
                            visualTransformation = hide,
                            isError = mismatch, supportingText = if (mismatch) { { Text(stringResource(I18nR.string.au21_arch_pw_mismatch)) } } else null,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                    Text(stringResource(I18nR.string.au21_arch_zip_note), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                } else {
                    Text(stringResource(I18nR.string.au21_arch_no_pw_note), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && '/' !in name && !mismatch && splitValid, onClick = {
                val secret = if (format.supportsPassword && password.isNotEmpty()) password.toCharArray() else null
                vm.compress(entries, name.trim(), format, secret, splitBytes)
                vm.clearSelection()
                onDismiss()
            }) { Text(stringResource(I18nR.string.hf_files_compress_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}

@Composable
internal fun NameDialog(title: String, initial: String, action: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(title)  } },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = text.isNotBlank() && '/' !in text, onClick = { onDone(text.trim()) }) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}

@Composable
internal fun SortDialog(vm: FilesViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_sort_by))  } },
        text = {
            Column {
                SortKey.values().forEach { key ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.updateSort(vm.sort.copy(key = key)) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = vm.sort.key == key, onClick = { vm.updateSort(vm.sort.copy(key = key)) })
                        Text(stringResource(key.labelRes), Modifier.padding(start = 8.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SwitchRow(stringResource(I18nR.string.hf_files_sort_descending), !vm.sort.ascending) { vm.updateSort(vm.sort.copy(ascending = !it)) }
                SwitchRow(stringResource(I18nR.string.hf_files_sort_folders_first), vm.sort.foldersFirst) { vm.updateSort(vm.sort.copy(foldersFirst = it)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_done)) } },
    )
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun RootWarningDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var understood by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(BaseR.drawable.terminal_24px), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_root_explorer))  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(I18nR.string.hc_telos_files_will_ask_for_superuser_acces))
                Text(
                    stringResource(I18nR.string.hc_careless_changes_here_can_stop_apps_from) +
                        stringResource(I18nR.string.hf_files_root_warning_tail),
                    color = MaterialTheme.colorScheme.error,
                )
                Text(stringResource(I18nR.string.hc_your_root_manager_magisk_kernelsu_will_a))
                Row(Modifier.clickable { understood = !understood }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = understood, onCheckedChange = { understood = it })
                    Text(stringResource(I18nR.string.hc_i_understand_the_risks))
                }
            }
        },
        confirmButton = { TextButton(enabled = understood, onClick = onConfirm) { Text(stringResource(I18nR.string.hc_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}

@Composable
internal fun PropertiesDialog(vm: FilesViewModel, entry: FsEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var size by remember { mutableStateOf<Long?>(null) }
    var count by remember { mutableStateOf<Int?>(null) }
    var hashes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var mode by remember { mutableStateOf("") }
    val unavailableLabel = stringResource(I18nR.string.au_files_unavailable)
    var showCloudUpload by remember(entry.path) { mutableStateOf(false) }
    if (showCloudUpload) CloudUploadDialog(entry) { showCloudUpload = false }
    val scope = rememberCoroutineScope()
    LaunchedEffect(entry.path) {
        size = vm.sizeOf(entry)
        if (entry.isDir) count = vm.childCount(entry)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Detail(stringResource(I18nR.string.hf_files_prop_type), stringResource(entry.kind.labelRes) + if (entry.extension.isNotEmpty()) " (.${entry.extension})" else "")
                Detail(stringResource(I18nR.string.hf_files_prop_location), parentOf(entry.path) ?: "/")
                Detail(stringResource(I18nR.string.hf_files_prop_size), size?.let { if (it < 0) stringResource(I18nR.string.hf_files_unknown) else formatSize(it) + if (it >= 1024) " (" + stringResource(I18nR.string.hf_files_bytes_exact, it) + ")" else "" } ?: stringResource(I18nR.string.hf_files_calculating))
                count?.let { if (it >= 0) Detail(stringResource(I18nR.string.hf_files_prop_contains), "$it") }
                Detail(stringResource(I18nR.string.hf_files_prop_modified), formatDate(entry.modified).ifEmpty { stringResource(I18nR.string.hf_files_unknown) })
                if (entry.permissions.isNotEmpty()) Detail(stringResource(I18nR.string.hf_files_prop_permissions), entry.permissions)
                if (entry.owner.isNotEmpty()) Detail(stringResource(I18nR.string.hf_files_prop_owner), entry.owner)
                entry.linkTarget?.takeIf { it.isNotEmpty() }?.let { Detail(stringResource(I18nR.string.hf_files_prop_link_to), it) }
                if (!entry.isDir && !vm.rootMode && entry.path.startsWith("/") &&
                    !FileActions.isSystemPath(entry.path, true)
                ) {
                    TextButton(onClick = { showCloudUpload = true }) {
                        Text(stringResource(I18nR.string.files_cloud_upload))
                    }
                }
                if (!entry.isDir) {
                    HorizontalDivider()
                    Text(stringResource(I18nR.string.hc_checksums), style = MaterialTheme.typography.labelLarge)
                    listOf("MD5" to "MD5", "SHA-1" to "SHA-1", "SHA-256" to "SHA-256").forEach { (label, algorithm) ->
                        val value = hashes[label]
                        if (value == null) {
                            TextButton(onClick = { scope.launch { hashes = hashes + (label to vm.checksum(entry, algorithm).ifEmpty { unavailableLabel }) } }) { Text(stringResource(I18nR.string.hc_calculate_label, label)) }
                        } else Detail(label, value)
                    }
                }
                if (vm.rootMode) {
                    HorizontalDivider()
                    Text(stringResource(I18nR.string.hc_change_permissions_octal_for_example_644), style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(mode, { mode = it.filter { c -> c in '0'..'7' }.take(4) }, singleLine = true, modifier = Modifier.weight(1f))
                        TextButton(enabled = mode.length >= 3, onClick = { vm.chmod(entry, mode); onDismiss() }) { Text(stringResource(I18nR.string.hc_apply)) }
                    }
                    if (FileActions.isSystemPath(entry.path, true)) {
                        Text(stringResource(I18nR.string.hc_this_is_a_system_location_a_wrong_permis), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_close)) } },
        dismissButton = {
            if (!entry.isDir) Row {
                TextButton(onClick = { scope.launch { FileActions.openWith(context, entry, vm.rootMode, vm.shizukuMode) } }) { Text(stringResource(I18nR.string.hc_open_with_2)) }
                TextButton(onClick = { vm.shareEntries(listOf(entry)) }) { Text(stringResource(I18nR.string.hc_share)) }
                if (de.mm20.launcher2.ui.common.share.ShareActions.hasLocalSend(context)) {
                    TextButton(onClick = { vm.shareEntries(listOf(entry), localSend = true) }) {
                        Text(stringResource(I18nR.string.files_send_localsend))
                    }
                }
            }
        },
    )
}

@Composable
internal fun Detail(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun UnlockVaultDialog(vm: FilesViewModel, entry: FsEntry, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_unlock_vault))  } },
        text = {
            Column {
                Text(stringResource(I18nR.string.hf_files_vault_info, entry.name))
                OutlinedTextField(
                    password, { password = it; error = null }, label = { Text(stringResource(I18nR.string.hc_password)) }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    isError = error != null, supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty() && !busy, onClick = {
                busy = true
                vm.unlockVault(entry.path, password, onError = { error = it; busy = false }) { busy = false; onDismiss() }
            }) { Text(if (busy) stringResource(I18nR.string.hf_files_unlocking) else stringResource(I18nR.string.hc_unlock)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}
