package de.mm20.launcher2.ui.terminal

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException

@Serializable
data object TerminalRoute : NavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()

    val termuxDir = remember { File(context.filesDir, "termux_usr") }
    val bashExecutable = remember { File(termuxDir, "bin/bash") }
    var isInstalled by remember { mutableStateOf(bashExecutable.exists()) }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("") }

    var commandInput by remember { mutableStateOf("") }
    val terminalOutput = remember { mutableStateListOf<String>("Telos Interactive Termux-Compatible Shell", "Type 'help' or any bash command...") }

    fun runCommand(cmd: String) {
        if (cmd.isBlank()) return
        terminalOutput.add("$ $cmd")
        commandInput = ""

        scope.launch(Dispatchers.IO) {
            try {
                val env = mapOf(
                    "PATH" to "${termuxDir.absolutePath}/bin:${termuxDir.absolutePath}/usr/bin",
                    "HOME" to context.filesDir.absolutePath,
                    "TERM" to "xterm-256color"
                )
                val pb = ProcessBuilder(bashExecutable.absolutePath, "-c", cmd)
                pb.environment().putAll(env)
                pb.directory(context.filesDir)
                pb.redirectErrorStream(true)

                val process = pb.start()
                val reader = process.inputStream.bufferedReader()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val outLine = line ?: ""
                    withContext(Dispatchers.Main) {
                        terminalOutput.add(outLine)
                    }
                }
                process.waitFor()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    terminalOutput.add("Error executing command: ${e.message}")
                }
            }
        }
    }

    fun downloadCoreRuntime() {
        isDownloading = true
        statusText = "Λήψη Termux Core Runtime..."
        downloadProgress = 0f

        scope.launch(Dispatchers.IO) {
            try {
                val abi = when {
                    Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "aarch64"
                    Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "arm"
                    Build.SUPPORTED_ABIS.contains("x86_64") -> "x86_64"
                    else -> "aarch64"
                }

                val downloadUrlStr = "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.01.27/$abi-bootstrap.zip"
                val zipFile = File(context.cacheDir, "bootstrap.zip")

                // Handle Redirects (302/307)
                var currentUrlStr = downloadUrlStr
                var redirects = 0
                var conn: HttpURLConnection
                while (redirects < 10) {
                    val url = URL(currentUrlStr)
                    conn = url.openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    val code = conn.responseCode
                    if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                        val loc = conn.getHeaderField("Location")
                        if (loc != null) {
                            currentUrlStr = loc
                            redirects++
                            continue
                        }
                    }

                    val totalSize = conn.contentLength.toFloat()
                    var downloaded = 0L

                    conn.inputStream.use { input ->
                        FileOutputStream(zipFile).use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (downloaded > 64L * 1024 * 1024) throw IOException("Runtime download size limit exceeded")
                                ensureActive()
                                if (totalSize > 0) {
                                    withContext(Dispatchers.Main) {
                                        downloadProgress = (downloaded / totalSize).coerceIn(0f, 1f)
                                    }
                                }
                            }
                        }
                    }
                    break
                }

                withContext(Dispatchers.Main) {
                    statusText = "Εγκατάσταση & Ρύθμιση..."
                }

                TerminalRuntimeArchive.extract(zipFile.inputStream(), termuxDir, checkActive = { ensureActive() })

                zipFile.delete()
                check(bashExecutable.isFile && bashExecutable.canExecute())

                withContext(Dispatchers.Main) {
                    isDownloading = false
                    isInstalled = true
                    Toast.makeText(context, "Το Termux Core εγκαταστάθηκε επιτυχώς!", Toast.LENGTH_SHORT).show()
                }

            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    statusText = context.getString(R.string.au_files_download_failed)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telos Terminal (Termux Core)", fontWeight = FontWeight.Bold, color = Color.Green) },
                navigationIcon = {
                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "Back", tint = Color.Green)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        if (!isInstalled) {
            // On-Demand Installer View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(de.mm20.launcher2.base.R.drawable.ic_app_terminal_full),
                    contentDescription = "Terminal",
                    modifier = Modifier.size(80.dp),
                    tint = Color.Green
                )
                Spacer(Modifier.height(16.dp))
                Text("Termux-Compatible Core Runtime", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Για να διατηρηθεί το μέγεθος της εφαρμογής Telos μικρό, το περιβάλλον Termux Core (~20MB) λαμβάνεται on-demand απευθείας στη συσκευή.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(24.dp))

                if (isDownloading) {
                    LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(statusText, color = Color.Green, fontSize = 12.sp)
                } else {
                    Button(
                        onClick = { downloadCoreRuntime() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00AA00)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Λήψη Termux Core On-Demand", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Interactive Embedded Terminal Session View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.Black)
                    .padding(8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    reverseLayout = false
                ) {
                    items(terminalOutput) { line ->
                        Text(
                            text = line,
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    placeholder = { Text("Enter command...", color = Color.DarkGray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { runCommand(commandInput) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Green,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.Green,
                        unfocusedTextColor = Color.Green
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
