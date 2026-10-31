package de.mm20.launcher2.ui.store
import de.mm20.launcher2.i18n.R as I18nR

import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.mm20.launcher2.data.store.installer.TelosPackageInstaller
import de.mm20.launcher2.data.store.security.ApkSecurityReport
import de.mm20.launcher2.data.store.security.ApkSecurityScanner
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.store.options.StoreOptions
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.theme.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File

class ApkInstallActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dataUri = intent.data ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

        setContent {
            LauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (dataUri != null) {
                        ApkInstallerScreen(
                            uri = dataUri,
                            onClose = { finish() },
                        )
                    } else {
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
private fun ApkInstallerScreen(
    uri: Uri,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val freezeManager: FreezeManager = koinInject()
    val storeOptions: StoreOptions = koinInject()

    var tempFile by remember { mutableStateOf<File?>(null) }
    var reportState by remember { mutableStateOf<ApkSecurityReport?>(null) }
    var isInstalling by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(true) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "apk-installer").apply { mkdirs() }
                val file = File(dir, "install_${System.currentTimeMillis()}.apk")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                tempFile = file
                reportState = ApkSecurityScanner.scanApk(context, file)
            }
        }
        isScanning = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("InstallerX Package Installer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        if (isScanning) {
            CircularProgressIndicator()
            Text(stringResource(I18nR.string.hf_appinfo_permissions))
        } else {
            val report = reportState
            if (report != null) {
                val riskColor = when (report.riskLevel) {
                    ApkSecurityReport.RiskLevel.CLEAN -> MaterialTheme.colorScheme.primary
                    ApkSecurityReport.RiskLevel.LOW -> MaterialTheme.colorScheme.secondary
                    ApkSecurityReport.RiskLevel.MODERATE -> MaterialTheme.colorScheme.tertiary
                    ApkSecurityReport.RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(report.packageName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Version: ${report.versionName ?: "Code " + report.versionCode}")
                        Text(stringResource(I18nR.string.hf_appinfo_permissions) + ": ${report.dangerousPermissions.size}", color = riskColor, fontWeight = FontWeight.Bold)
                        Text("SHA-256: ${report.sha256.take(24)}...", style = MaterialTheme.typography.bodySmall)

                        if (report.dangerousPermissions.isNotEmpty()) {
                            Text("Requested Permissions (${report.dangerousPermissions.size}):", fontWeight = FontWeight.SemiBold)
                            report.dangerousPermissions.take(5).forEach { perm ->
                                Text("• ${perm.substringAfterLast('.')}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (isInstalling) {
                    CircularProgressIndicator()
                    Text(stringResource(I18nR.string.hf_store_installing_dots))
                } else {
                    Button(
                        onClick = {
                            isInstalling = true
                            scope.launch {
                                val apk = tempFile
                                if (apk != null) {
                                    val installer = TelosPackageInstaller(context, freezeManager, storeOptions)
                                    val result = installer.install(apk, report.packageName)
                                    isInstalling = false
                                    if (result is de.mm20.launcher2.store.installer.InstallResult.Success) {
                                        Toast.makeText(context, "Installed successfully!", Toast.LENGTH_SHORT).show()
                                        onClose()
                                    } else {
                                        Toast.makeText(context, "Privileged install failed. Use System Fallback.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Install (InstallerX Engine)")
                    }

                    OutlinedButton(
                        onClick = {
                            val apk = tempFile
                            if (apk != null) {
                                val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
                                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(fileUri, "application/vnd.android.package-archive")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(fallbackIntent)
                                onClose()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Fallback to Default System Installer")
                    }
                }
            } else {
                Text("Failed to read APK file")
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onClose) {
            Text("Cancel")
        }
    }
}
