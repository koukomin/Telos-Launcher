package de.mm20.launcher2.ui.files

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import de.mm20.launcher2.helper.EncryptedFileCarrier
import de.mm20.launcher2.helper.EncryptedPngCarrier
import de.mm20.launcher2.helper.PasswordFileEncryption
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** Single local file only. Remote, archive and vault entries are deliberately not accepted. */
@Composable
fun FilesEncryptionDialog(source: File, decrypt: Boolean, onDismiss: () -> Unit, onComplete: () -> Unit, carrierMode: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val action = when {
        carrierMode && decrypt -> I18nR.string.telos_files_carrier_extract
        carrierMode -> I18nR.string.telos_files_carrier_embed
        decrypt -> I18nR.string.telos_files_decrypt
        else -> I18nR.string.telos_files_encrypt
    }
    var usePixels by remember(source, carrierMode) { mutableStateOf(false) }
    var carrier by remember { mutableStateOf<Uri?>(null) }
    val carrierPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) carrier = uri
    }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var filename by remember(source, decrypt, carrierMode) {
        mutableStateOf(when {
            carrierMode && decrypt -> "payload.bin"
            carrierMode -> ""
            decrypt -> source.name.removeSuffix(".telos-aes").let { if (it == source.name) "$it.decrypted" else it }
            else -> "${source.name}.telos-aes"
        })
    }
    var job by remember { mutableStateOf<Job?>(null) }
    var failure by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    val valid = (!usePixels || decrypt || filename.endsWith(".png", true)) && (!carrierMode || decrypt || carrier != null) && password.length in (if (decrypt) 1 else 8)..1024 && (decrypt || confirmation == password) &&
        filename.isNotBlank() && filename !in setOf(".", "..") && filename.none { it == '/' || it == '\\' || it == '\u0000' } && filename != source.name
    fun dismiss() {
        if (job?.isActive == true) job?.cancel() else { password = ""; confirmation = ""; onDismiss() }
    }
    AlertDialog(onDismissRequest = { if (job?.isActive != true) dismiss() },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn, dismissOnBackPress = job?.isActive != true, dismissOnClickOutside = job?.isActive != true),
        title = { TelosCyberHeading {  Text(stringResource(action))  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(source.name, style = MaterialTheme.typography.titleSmall)
                if (carrierMode) {
                    Row {
                        Checkbox(
                            checked = usePixels,
                            enabled = job?.isActive != true,
                            onCheckedChange = {
                                usePixels = it
                                carrier = null
                            },
                        )
                        Text(
                            text = stringResource(I18nR.string.telos_files_carrier_png),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    Text(stringResource(if (usePixels) I18nR.string.telos_files_carrier_png_info else I18nR.string.telos_files_carrier_info))
                    if (!decrypt) TextButton(enabled = job?.isActive != true, onClick = { carrierPicker.launch(if (usePixels) "image/png" else "*/*") }) {
                        Text(stringResource(if (carrier == null) I18nR.string.telos_files_carrier_pick else I18nR.string.telos_files_carrier_change))
                    }
                } else {
                    Text(stringResource(I18nR.string.telos_files_encryption_info))
                    Text(stringResource(I18nR.string.telos_files_encryption_publish_note), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(filename, { filename = it }, enabled = job?.isActive != true, singleLine = true,
                    label = { Text(stringResource(I18nR.string.telos_files_encryption_output)) })
                OutlinedTextField(password, { if (it.length <= 1024) password = it }, enabled = job?.isActive != true, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(I18nR.string.telos_files_encryption_password)) })
                if (!decrypt) OutlinedTextField(confirmation, { if (it.length <= 1024) confirmation = it }, enabled = job?.isActive != true, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(I18nR.string.telos_files_encryption_confirm)) })
                if (job?.isActive == true) {
                    if (carrierMode) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                if (failure) Text(stringResource(I18nR.string.telos_files_encryption_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = valid && job?.isActive != true, onClick = {
                val secret = password.toCharArray(); password = ""; confirmation = ""; failure = false; progress = 0f
                val carrierUri = carrier
                val pixelMode = usePixels
                val output = File(source.parentFile, filename)
                job = scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val staging = File(context.noBackupFilesDir, "file-encryption/${UUID.randomUUID()}")
                            check(staging.mkdirs())
                            val execution = currentCoroutineContext()
                            try {
                                val checkpoint = { execution.ensureActive() }
                                val update: (Long, Long) -> Unit = { done, total -> progress = if (total > 0) (done.toDouble() / total).toFloat() else 1f }
                                if (pixelMode && decrypt) {
                                    EncryptedPngCarrier.extract(source, output, secret, staging, checkpoint)
                                } else if (pixelMode) {
                                    EncryptedPngCarrier.embed(
                                        { checkNotNull(context.contentResolver.openInputStream(checkNotNull(carrierUri))) },
                                        source, output, secret, staging, checkpoint,
                                    )
                                } else if (carrierMode && decrypt) {
                                    EncryptedFileCarrier.extract(source, output, secret, staging, checkpoint)
                                } else if (carrierMode) {
                                    checkNotNull(context.contentResolver.openInputStream(checkNotNull(carrierUri))).use {
                                        EncryptedFileCarrier.embed(it, source, output, secret, staging, checkpoint)
                                    }
                                } else if (decrypt) PasswordFileEncryption.decrypt(source, output, secret, staging, checkpoint, update)
                                else PasswordFileEncryption.encrypt(source, output, secret, staging, checkpoint, update)
                            } finally { staging.deleteRecursively() }
                        }
                        onComplete(); onDismiss()
                    } catch (_: CancellationException) { /* Helper cleans up owned work; original stays. */ }
                    catch (_: Exception) { failure = true }
                    finally { secret.fill('\u0000'); job = null }
                }
            }) { Text(stringResource(action)) }
        },
        dismissButton = { TextButton(onClick = ::dismiss) { Text(stringResource(I18nR.string.hc_cancel)) } })
}
