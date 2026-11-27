package de.mm20.launcher2.ui.common.share

import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import de.mm20.launcher2.i18n.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.files.FilesEncryptionDialog
import de.mm20.launcher2.ui.theme.LauncherTheme
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** Opens a file from Telos's own share sheet and exports only the authenticated payload. */
class ExtractSharedFileActivity : BaseActivity() {
    private val staging by lazy { File(cacheDir, "carrier-extraction/${UUID.randomUUID()}") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data?.takeIf { ShareActions.isTelosCarrierShare(this, it) }
        if (uri == null) {
            finish()
            return
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    var input by remember { mutableStateOf<File?>(null) }
                    var output by remember { mutableStateOf<File?>(null) }
                    val scope = rememberCoroutineScope()
                    val export = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("application/octet-stream"),
                    ) { destination ->
                        val payload = output
                        if (destination == null || payload == null) {
                            finish()
                        } else {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        checkNotNull(contentResolver.openOutputStream(destination, "w")).use { target ->
                                            payload.inputStream().use { it.copyTo(target) }
                                        }
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    showFailure()
                                } finally {
                                    finish()
                                }
                            }
                        }
                    }
                    LaunchedEffect(uri) {
                        try {
                            input = withContext(Dispatchers.IO) {
                                check(staging.mkdirs())
                                val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
                                val file = File(staging, ShareActions.safeName(name.orEmpty(), "carrier"))
                                checkNotNull(contentResolver.openInputStream(uri)).use { source ->
                                    file.outputStream().use { target ->
                                        val buffer = ByteArray(65536)
                                        while (true) {
                                            ensureActive()
                                            val count = source.read(buffer)
                                            if (count < 0) break
                                            target.write(buffer, 0, count)
                                        }
                                    }
                                }
                                file
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            showFailure()
                            finish()
                        }
                    }
                    input?.takeIf { output == null }?.let { source ->
                        val standalone = source.name.endsWith(".telos-aes", ignoreCase = true)
                        FilesEncryptionDialog(
                            source = source,
                            decrypt = true,
                            onDismiss = ::finish,
                            onComplete = {},
                            carrierMode = !standalone,
                            onExport = { payload ->
                                output = payload
                                export.launch(payload.name)
                            },
                        )
                    }
                }
            }
        }
    }

    private fun showFailure() {
        Toast.makeText(this, R.string.telos_files_encryption_failed, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        intent.getStringExtra("ermis_staging")
            ?.takeIf { id -> runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false) }
            ?.let { File(cacheDir, "ermis-carrier/$it").deleteRecursively() }
        staging.deleteRecursively()
        super.onDestroy()
    }
}
