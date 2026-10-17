package de.mm20.launcher2.ui.terminal

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

@Serializable
data object TerminalRoute : NavKey

@Composable
fun TerminalScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showConsentDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var terminalSessionActive by remember { mutableStateOf(false) }
    var commandInput by remember { mutableStateOf("") }
    var consoleOutput by remember { mutableStateOf("Welcome to Telos Integrated Terminal (Termux Core)\nType commands below (e.g. 'pkg update', 'uname -a', 'python')\n\n") }

    val termuxRoot = File(context.filesDir, "termux_root")
    val hasBootstrap = termuxRoot.exists() && File(termuxRoot, "usr/bin/bash").exists()

    fun open(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, R.string.terminal_open_failed, Toast.LENGTH_LONG).show()
        }
    }
    fun website(url: String) = open(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    if (terminalSessionActive && hasBootstrap) {
        // Embedded Interactive Terminal UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Telos Shell", color = Color.Green, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
                TextButton(onClick = { terminalSessionActive = false }) {
                    Text("Exit", color = Color.Red)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF111111), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = consoleOutput,
                            color = Color(0xFF00FF00),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    placeholder = { Text("Enter command...", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Green,
                        unfocusedTextColor = Color.Green,
                        focusedContainerColor = Color(0xFF1c1c1e),
                        unfocusedContainerColor = Color(0xFF1c1c1e)
                    )
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val cmd = commandInput
                        commandInput = ""
                        consoleOutput += "$ $cmd\n"
                        scope.launch(Dispatchers.IO) {
                            try {
                                val bashBin = File(termuxRoot, "usr/bin/bash").absolutePath
                                val processBuilder = ProcessBuilder(bashBin, "-c", cmd)
                                processBuilder.directory(context.filesDir)
                                val env = processBuilder.environment()
                                env["HOME"] = context.filesDir.absolutePath
                                env["PREFIX"] = File(termuxRoot, "usr").absolutePath
                                env["PATH"] = "${File(termuxRoot, "usr/bin").absolutePath}:/system/bin:/system/xbin"
                                processBuilder.redirectErrorStream(true)
                                val process = processBuilder.start()
                                val output = process.inputStream.bufferedReader().readText()
                                process.waitFor()
                                withContext(Dispatchers.Main) {
                                    consoleOutput += output + "\n"
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    consoleOutput += "Error: ${e.message}\n\n"
                                }
                            }
                        }
                    }
                ) {
                    Text("Run")
                }
            }
        }
        return
    }

    PreferenceScreen(title = { Text(stringResource(R.string.terminal_title)) }) {
        item(key = "runtime") {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.terminal_runtime_summary))
                
                if (hasBootstrap) {
                    Button(
                        onClick = { terminalSessionActive = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Open Integrated Terminal")
                    }
                } else {
                    Button(
                        onClick = { showConsentDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        Text("Download & Install Termux Runtime (On-Demand)")
                    }
                }

                Button(onClick = {
                    val launch = context.packageManager.getLaunchIntentForPackage("com.termux")
                    if (launch != null) open(launch)
                    else website("https://f-droid.org/packages/com.termux/")
                }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(R.string.hc_open) + " · External Termux App")
                }
            }
        }
        item(key = "install") {
            Preference(title = { Text(stringResource(R.string.hc_install) + " · Termux") },
                summary = { Text("F-Droid") },
                onClick = { website("https://f-droid.org/packages/com.termux/") })
        }
        item(key = "packages") {
            Preference(title = { Text("pkg · apt · Python · Git · SSH") },
                onClick = { website("https://github.com/termux/termux-packages/wiki/Package-Management") })
        }
    }

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDownloading) showConsentDialog = false },
            title = { Text("Download Termux Core Runtime") },
            text = {
                Column {
                    Text("This will download the official Termux bootstrap environment (~20MB) compatible with Termux packages (pkg, apt, python, bash) directly into Telos Launcher's private secure storage.")
                    Spacer(Modifier.height(12.dp))
                    Text("Do you want to proceed with the download?", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    if (isDownloading) {
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("Downloading and configuring runtime...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                if (!isDownloading) {
                    TextButton(
                        onClick = {
                            isDownloading = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val abi = when {
                                        Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "aarch64"
                                        Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "arm"
                                        Build.SUPPORTED_ABIS.contains("x86_64") -> "x86_64"
                                        else -> "aarch64"
                                    }
                                    val url = URL("https://github.com/termux/termux-packages/releases/download/bootstrap-2024.01.27/$abi-bootstrap.zip")
                                    val zipFile = File(context.cacheDir, "bootstrap.zip")
                                    
                                    val connection = url.openConnection() as java.net.HttpURLConnection
                                    connection.instanceFollowRedirects = true
                                    val fileSize = connection.contentLength.toFloat()
                                    var downloaded = 0L
                                    
                                    connection.inputStream.use { input ->
                                        FileOutputStream(zipFile).use { output ->
                                            val buffer = ByteArray(8192)
                                            var bytesRead: Int
                                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                                output.write(buffer, 0, bytesRead)
                                                downloaded += bytesRead
                                                if (fileSize > 0) {
                                                    downloadProgress = (downloaded / fileSize).coerceIn(0f, 1f)
                                                }
                                            }
                                        }
                                    }

                                    // Extract zip
                                    if (termuxRoot.exists()) termuxRoot.deleteRecursively()
                                    termuxRoot.mkdirs()

                                    ZipInputStream(zipFile.inputStream()).use { zis ->
                                        var ze = zis.nextEntry
                                        while (ze != null) {
                                            val newFile = File(termuxRoot, ze.name)
                                            if (ze.isDirectory) {
                                                newFile.mkdirs()
                                            } else {
                                                newFile.parentFile?.mkdirs()
                                                FileOutputStream(newFile).use { fos ->
                                                    val bytes = ByteArray(8192)
                                                    var len: Int
                                                    while (zis.read(bytes).also { len = it } > 0) {
                                                        fos.write(bytes, 0, len)
                                                    }
                                                }
                                            }
                                            zis.closeEntry()
                                            ze = zis.nextEntry
                                        }
                                    }
                                    zipFile.delete()

                                    // Set execute permissions
                                    val binDir = File(termuxRoot, "usr/bin")
                                    if (binDir.exists()) {
                                        binDir.walkTopDown().forEach { file ->
                                            if (file.isFile) {
                                                file.setExecutable(true, false)
                                            }
                                        }
                                    }

                                    withContext(Dispatchers.Main) {
                                        isDownloading = false
                                        showConsentDialog = false
                                        terminalSessionActive = true
                                        Toast.makeText(context, "Termux Runtime installed successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isDownloading = false
                                        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Download & Install")
                    }
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { showConsentDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}
