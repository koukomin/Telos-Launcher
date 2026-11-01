package de.mm20.launcher2.appmanagement.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import de.mm20.launcher2.coroutines.TelosDispatchers
import de.mm20.launcher2.ktx.PrivilegedShell
import de.mm20.launcher2.ktx.ShizukuProcesses
import de.mm20.launcher2.ktx.TelosShellEngine
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Built-in Shizuku manager: Telos ships the Next/Plus server classes and native
 * starter (`libshizuku.so`) and starts them as shell/root. Other Shizuku-API apps
 * receive the binder the same way as with the standalone manager.
 */
object TelosShizukuHost {
    private const val TAG = "TelosShizukuHost"

    enum class StartMethod { Auto, Wireless, Root, Usb, Tcp }

    data class HostStatus(
        val running: Boolean = false,
        val authorized: Boolean = false,
        val uid: Int = -1,
        val version: Int = -1,
        val method: StartMethod = StartMethod.Auto,
        val lastLog: String = "",
        val starterPath: String = "",
    )

    private val _status = MutableStateFlow(HostStatus())
    val status: StateFlow<HostStatus> = _status.asStateFlow()

    fun starterFile(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libshizuku.so")

    fun refresh(context: Context) {
        ShizukuProcesses.init(context.applicationContext)
        val binder = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val authorized = binder && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        _status.value = _status.value.copy(
            running = binder,
            authorized = authorized,
            uid = if (binder) runCatching { Shizuku.getUid() }.getOrDefault(-1) else -1,
            version = if (binder) runCatching { Shizuku.getVersion() }.getOrDefault(-1) else -1,
            starterPath = starterFile(context).absolutePath,
        )
        TelosShizukuManager.refreshStatus(context)
    }

    suspend fun start(context: Context, method: StartMethod = StartMethod.Auto): Result<String> =
        withContext(TelosDispatchers.Disk) {
            val app = context.applicationContext
            ShizukuProcesses.init(app)
            if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
                refresh(app)
                return@withContext Result.success("running")
            }
            val starter = starterFile(app)
            if (!starter.canExecute() && !starter.exists()) {
                return@withContext Result.failure(IllegalStateException("libshizuku.so missing"))
            }
            val apk = app.applicationInfo.sourceDir
            val cmd = "${starter.absolutePath} --apk=$apk"
            val chosen = when (method) {
                StartMethod.Root -> StartMethod.Root
                StartMethod.Wireless, StartMethod.Usb, StartMethod.Tcp -> method
                StartMethod.Auto -> if (PrivilegedShell.rootAvailable()) StartMethod.Root else StartMethod.Wireless
            }
            val log = StringBuilder()
            val ok = when (chosen) {
                StartMethod.Root -> {
                    log.appendLine("root $cmd")
                    val r = TelosShellEngine.execResult("su -c '$cmd'", timeoutMs = 45_000L)
                    log.append(r.stdout).append(r.stderr)
                    r.isSuccess || waitBinder()
                }
                StartMethod.Wireless, StartMethod.Usb, StartMethod.Tcp -> {
                    enableWirelessDebugging(app)
                    log.appendLine("wireless $cmd")
                    val r = TelosShellEngine.execResult(cmd, timeoutMs = 45_000L)
                    log.append(r.stdout).append(r.stderr)
                    waitBinder() || r.isSuccess
                }
                else -> false
            }
            refresh(app)
            if (ok || _status.value.running) {
                runCatching { Shizuku.requestPermission(0x51) }
                Result.success(log.toString())
            } else {
                Result.failure(IllegalStateException(log.toString().ifBlank { "start failed" }))
            }
        }.also { result ->
            _status.value = _status.value.copy(
                lastLog = result.exceptionOrNull()?.message ?: result.getOrNull().orEmpty(),
                method = method,
            )
        }

    suspend fun stop(): Boolean = withContext(TelosDispatchers.Disk) {
        val r = TelosShellEngine.execResult("killall shizuku_server", timeoutMs = 8_000L)
        delay(400)
        !runCatching { Shizuku.pingBinder() }.getOrDefault(false) || r.isSuccess
    }

    suspend fun watchdogTick(context: Context, autostart: Boolean, method: StartMethod) {
        if (!autostart) return
        refresh(context)
        if (!_status.value.running) start(context, method)
    }

    private suspend fun waitBinder(): Boolean {
        repeat(20) {
            if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return true
            delay(500)
        }
        return false
    }

    private fun enableWirelessDebugging(context: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        val cr = context.contentResolver
        runCatching {
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
        }.onFailure { Log.w(TAG, "wireless debug", it) }
    }

    fun startIntent(context: Context, action: String) {
        context.sendBroadcast(
            Intent(action).setPackage(context.packageName).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
        )
    }
}
