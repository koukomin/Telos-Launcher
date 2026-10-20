package de.mm20.launcher2.ui.files.remote

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

object FilesFtpServer {
    private var serverSocket: ServerSocket? = null
    var isRunning by mutableStateOf(false)
        private set

    var serverIpAddress by mutableStateOf("0.0.0.0")
        private set

    const val PORT = 2121

    fun start(context: Context) {
        if (isRunning) return

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wifiManager.connectionInfo.ipAddress
        serverIpAddress = Formatter.formatIpAddress(ipInt)

        isRunning = true

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(PORT)
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    handleClient(socket, context)
                }
            } catch (_: Exception) {
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleClient(socket: Socket, context: Context) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)

                writer.println("220 Telos Files Wi-Fi FTP Server Ready")

                var line: String? = null
                while (isRunning && reader.readLine().also { line = it } != null) {
                    val cmd = line?.trim().orEmpty()
                    when {
                        cmd.startsWith("USER", ignoreCase = true) -> writer.println("331 Password required")
                        cmd.startsWith("PASS", ignoreCase = true) -> writer.println("230 User logged in")
                        cmd.startsWith("SYST", ignoreCase = true) -> writer.println("215 UNIX Type: L8")
                        cmd.startsWith("PWD", ignoreCase = true) -> writer.println("257 \"/\" is current directory")
                        cmd.startsWith("TYPE", ignoreCase = true) -> writer.println("200 Type set")
                        cmd.startsWith("QUIT", ignoreCase = true) -> {
                            writer.println("221 Goodbye")
                            break
                        }
                        else -> writer.println("502 Command not implemented")
                    }
                }
                socket.close()
            } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesFtpServerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.wifi_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text("Διακομιστής Wi-Fi FTP", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Μοιραστείτε αρχεία με τον υπολογιστή ή την τηλεόρασή σας ασύρματα μέσω Wi-Fi χωρίς καλώδιο.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(20.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (FilesFtpServer.isRunning)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (FilesFtpServer.isRunning) "ΕΝΕΡΓΟΣ" else "ΑΝΕΝΕΡΓΟΣ",
                            fontWeight = FontWeight.Bold,
                            color = if (FilesFtpServer.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (FilesFtpServer.isRunning) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "ftp://${FilesFtpServer.serverIpAddress}:${FilesFtpServer.PORT}",
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Πληκτρολογήστε τη διεύθυνση στον file manager του υπολογιστή σας",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (FilesFtpServer.isRunning) {
                        FilesFtpServer.stop()
                    } else {
                        FilesFtpServer.start(context)
                    }
                }
            ) {
                Text(if (FilesFtpServer.isRunning) "Διακοπή FTP" else "Έναρξη FTP")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Κλείσιμο")
            }
        }
    )
}
