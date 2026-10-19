package de.mm20.launcher2.ui.terminal

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.serialization.Serializable

@Serializable
data object TerminalRoute : NavKey

/** Official Termux owns its required prefix, UID and signed add-on ecosystem. */
@Composable
fun TerminalScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var resolving by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val manager = remember { context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
    val preferences = remember { context.getSharedPreferences("terminal_installer", Context.MODE_PRIVATE) }
    var downloadId by rememberSaveable { mutableLongStateOf(preferences.getLong("download_id", -1)) }
    var status by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    fun open(intent: Intent) {
        try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { Toast.makeText(context, R.string.terminal_open_failed, Toast.LENGTH_LONG).show() }
    }
    fun website(url: String) = open(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    fun download() {
        scope.launch {
          resolving = true
          try {
            val version = withContext(Dispatchers.IO) {
                val connection = URL("https://f-droid.org/api/v1/packages/com.termux").openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                try {
                    check(connection.responseCode == 200)
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (output.size() <= 16_384) {
                            val count = input.read(buffer, 0, minOf(buffer.size, 16_385 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    check(bytes.size <= 16_384)
                    val metadata = JSONObject(String(bytes, Charsets.UTF_8))
                    check(metadata.getString("packageName") == "com.termux")
                    metadata.getLong("suggestedVersionCode").also { check(it > 0) }
                } finally { connection.disconnect() }
            }
            // Termux installs/configures its bootstrap under its own required prefix on first launch.
            downloadId = manager.enqueue(DownloadManager.Request(Uri.parse("https://f-droid.org/repo/com.termux_${version}.apk"))
                .setTitle("Termux · F-Droid")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "termux-fdroid-${System.currentTimeMillis()}.apk"))
            preferences.edit().putLong("download_id", downloadId).apply()
          } catch (_: Exception) { Toast.makeText(context, R.string.terminal_open_failed, Toast.LENGTH_LONG).show() }
          finally { resolving = false }
        }
    }
    LaunchedEffect(downloadId, refresh) {
        if (downloadId < 0) return@LaunchedEffect
        while (true) {
            var found = false
            runCatching {
                manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                    if (cursor.moveToFirst()) {
                        found = true
                        status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val received = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        progress = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f
                    }
                }
            }
            if (!found || status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) break
            delay(750)
        }
    }
    val downloading = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED
    PreferenceScreen(title = { Text(stringResource(R.string.terminal_title)) }) {
        item(key = "runtime") {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.terminal_runtime_summary))
                Button(onClick = {
                    val launch = context.packageManager.getLaunchIntentForPackage("com.termux")
                    if (launch != null) open(launch) else download()
                }, enabled = !downloading && !resolving, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text(stringResource(if (context.packageManager.getLaunchIntentForPackage("com.termux") != null) R.string.hc_open else R.string.hc_install) + " · Termux")
                }
                if (downloading) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    TextButton(onClick = { manager.remove(downloadId); downloadId = -1; status = 0; preferences.edit().remove("download_id").apply() }) { Text(stringResource(R.string.hc_cancel)) }
                }
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    Button(onClick = {
                        val uri = manager.getUriForDownloadedFile(downloadId)
                        if (uri != null) open(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        else open(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.hc_install) + " · Termux · F-Droid") }
                }
                if (status == DownloadManager.STATUS_FAILED) {
                    Text(stringResource(R.string.hc_failed_try_again), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = ::download) { Text(stringResource(R.string.hc_retry)) }
                }
            }
        }
        item(key = "source") {
            Preference(title = { Text("Termux · F-Droid") }, onClick = { website("https://f-droid.org/packages/com.termux/") })
        }
        item(key = "packages") {
            Preference(title = { Text("pkg · apt · Python · Git · SSH") }, onClick = { website("https://github.com/termux/termux-packages/wiki/Package-Management") })
        }
        item(key = "plugins") {
            Preference(title = { Text("Termux:API · Boot · Widget · Tasker · Float · Styling") }, onClick = { website("https://github.com/termux/termux-app#termux-app-and-plugins") })
        }
    }
}
