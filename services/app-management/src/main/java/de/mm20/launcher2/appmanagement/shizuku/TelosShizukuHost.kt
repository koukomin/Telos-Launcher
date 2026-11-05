package de.mm20.launcher2.appmanagement.shizuku

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import de.mm20.launcher2.coroutines.TelosDispatchers
import de.mm20.launcher2.ktx.PrivilegedShell
import de.mm20.launcher2.ktx.ShizukuProcesses
import de.mm20.launcher2.ktx.TelosShellEngine
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku

/**
 * Built-in Shizuku manager: Telos ships the Next/Plus server classes and native
 * starter (`libshizuku.so`) and starts them as shell/root. Other Shizuku-API apps
 * receive the binder the same way as with the standalone manager.
 */
object TelosShizukuHost {
    private const val TAG = "TelosShizukuHost"

    enum class StartMethod { Auto, Wireless, Root, Usb, Tcp }

    enum class PairingPhase { Idle, Searching, WaitingForCode, Working, Done, Failed }

    data class PairingHint(
        val automatic: Boolean = true,
        val code: String = "",
        val host: String? = null,
        val port: Int? = null,
        val phase: PairingPhase = PairingPhase.Idle,
    )

    data class HostStatus(
        val running: Boolean = false,
        val authorized: Boolean = false,
        val uid: Int = -1,
        val version: Int = -1,
        val method: StartMethod = StartMethod.Auto,
        val lastLog: String = "",
        val starterPath: String = "",
        val apkPath: String = "",
        val pcAdbCommand: String = "",
        val lastPort: Int = 0,
        val pairing: PairingHint = PairingHint(),
    )

    private val _status = MutableStateFlow(HostStatus())
    val status: StateFlow<HostStatus> = _status.asStateFlow()
    private val pairingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pairingTask: Deferred<Result<String>>? = null
    private val pairingMutex = kotlinx.coroutines.sync.Mutex()
    private var pairingDiscovery: TelosAdbPairingDiscovery? = null

    fun starterFile(context: Context): File {
        val extracted = File(context.applicationInfo.nativeLibraryDir, "libshizuku.so")
        if (extracted.exists()) return extracted
        return File(context.applicationContext.filesDir, "libshizuku.so")
    }

    /** Copy libshizuku.so out of the APK when the installer left nativeLibraryDir empty. */
    private fun ensureStarter(context: Context): File? {
        val native = File(context.applicationInfo.nativeLibraryDir, "libshizuku.so")
        if (native.exists() && native.length() > 0L) return native
        val dest = File(context.applicationContext.filesDir, "libshizuku.so")
        if (dest.exists() && dest.length() > 0L) return dest
        val apks = buildList {
            add(context.applicationInfo.sourceDir)
            context.applicationInfo.publicSourceDir?.let(::add)
            context.applicationInfo.splitSourceDirs?.forEach(::add)
        }.map(::File).distinct()
        val abis = Build.SUPPORTED_ABIS.toList()
        for (apk in apks) {
            if (!apk.isFile) continue
            val copied = runCatching {
                ZipFile(apk).use { zip ->
                    val entry = abis.firstNotNullOfOrNull { zip.getEntry("lib/$it/libshizuku.so") }
                        ?: return@use false
                    dest.parentFile?.mkdirs()
                    dest.outputStream().use { out -> zip.getInputStream(entry).copyTo(out) }
                    true
                }
            }.getOrDefault(false)
            if (copied && dest.length() > 0L) {
                dest.setReadable(true, false)
                dest.setExecutable(true, false)
                return dest
            }
        }
        return dest.takeIf { it.exists() && it.length() > 0L }
    }

    fun pcAdbCommand(context: Context): String {
        val starter = starterFile(context).absolutePath
        val apk = context.applicationInfo.sourceDir
        return "adb shell $starter --apk=$apk"
    }

    private fun prefs(context: Context): SharedPreferences {
        val app = context.applicationContext
        val storage = if (Build.VERSION.SDK_INT >= 24) {
            app.createDeviceProtectedStorageContext()
        } else app
        return storage.getSharedPreferences("telos_shizuku_host", Context.MODE_PRIVATE)
    }

    fun lastPort(context: Context): Int = prefs(context).getInt("last_port", 0)

    fun saveLastPort(context: Context, port: Int) {
        if (port > 0) prefs(context).edit().putInt("last_port", port).apply()
    }

    fun onPairingCodeRead(context: Context, code: String, host: String?, port: Int?) {
        val prev = _status.value.pairing
        _status.value = _status.value.copy(
            pairing = prev.copy(
                code = code,
                host = host ?: prev.host,
                port = port ?: prev.port,
                phase = if (code.length == 6) PairingPhase.WaitingForCode else prev.phase,
            ),
        )
    }

    internal var wirelessNavigationRequestedAt = 0L
        private set

    fun requestWirelessNavigation() {
        wirelessNavigationRequestedAt = android.os.SystemClock.elapsedRealtime()
    }

    fun notifyPairingSearch(automatic: Boolean = true) {
        _status.value = _status.value.copy(pairing = PairingHint(automatic = automatic, phase = PairingPhase.Searching))
    }

    @android.annotation.SuppressLint("NewApi")
    fun startPairingWait(context: Context) {
        stopPairingWait()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            _status.value = _status.value.copy(
                pairing = PairingHint(phase = PairingPhase.Failed),
                lastLog = "pairing needs Android 11",
            )
            return
        }
        _status.value = _status.value.copy(
            pairing = PairingHint(phase = PairingPhase.Searching),
        )
        val discovery = TelosAdbPairingDiscovery(context) { host, port ->
            val prev = _status.value.pairing
            _status.value = _status.value.copy(
                pairing = prev.copy(
                    host = host,
                    port = port,
                    phase = PairingPhase.WaitingForCode,
                ),
            )
        }
        pairingDiscovery = discovery
        discovery.start()
    }

    fun stopPairingWait() {
        pairingDiscovery?.stop()
        pairingDiscovery = null
        val prev = _status.value.pairing
        if (prev.phase == PairingPhase.Searching || prev.phase == PairingPhase.WaitingForCode) {
            _status.value = _status.value.copy(pairing = prev.copy(phase = PairingPhase.Idle))
        }
    }

    fun setPairingCode(code: String) {
        val digits = code.filter { it.isDigit() }.take(6)
        val prev = _status.value.pairing
        _status.value = _status.value.copy(pairing = prev.copy(code = digits))
    }

    suspend fun submitPairingCode(
        context: Context,
        code: String,
        host: String? = null,
        port: Int? = null,
    ): Result<String> {
        val app = context.applicationContext
        val task = synchronized(pairingScope) {
            pairingTask?.takeIf { it.isActive } ?: pairingScope.async {
                try {
                    withTimeout(60_000L) { performPairing(app, code, host, port) }
                } catch (error: TimeoutCancellationException) {
                    _status.value = _status.value.copy(pairing = _status.value.pairing.copy(code = "", phase = PairingPhase.Failed), lastLog = "Pairing timed out; reopen the Settings pairing dialog")
                    Result.failure(error)
                } catch (error: CancellationException) {
                    _status.value = _status.value.copy(pairing = PairingHint())
                    throw error
                } catch (error: Exception) {
                    _status.value = _status.value.copy(pairing = _status.value.pairing.copy(code = "", phase = PairingPhase.Failed), lastLog = error.message.orEmpty())
                    Result.failure(error)
                }
            }.also { pairingTask = it }
        }
        // Closing/rebinding an overlay must not cancel an in-flight pairing transaction.
        return task.await()
    }

    fun cancelPairing() {
        synchronized(pairingScope) { pairingTask?.cancel() }
        _status.value = _status.value.copy(pairing = PairingHint())
    }

    private suspend fun performPairing(context: Context, code: String, host: String?, port: Int?): Result<String> = pairingMutex.withLock {
        if (_status.value.pairing.phase == PairingPhase.Done) return@withLock Result.success("running")
        val digits = code.filter { it.isDigit() }.take(6)
        if (digits.length != 6) {
            return@withLock Result.failure(IllegalArgumentException("need 6 digits"))
        }
        val prev = _status.value.pairing
        val useHost = host ?: prev.host
        val usePort = port ?: prev.port
        onPairingCodeRead(context, digits, useHost, usePort)
        _status.value = _status.value.copy(pairing = _status.value.pairing.copy(phase = PairingPhase.Working))
        val paired = TelosAdbPairer.pair(context, digits, useHost, usePort)
        if (paired.isFailure) {
            _status.value = _status.value.copy(
                pairing = _status.value.pairing.copy(code = "", phase = PairingPhase.Failed),
                lastLog = paired.exceptionOrNull()?.message.orEmpty(),
            )
            return@withLock Result.failure(paired.exceptionOrNull() ?: IllegalStateException("pair failed"))
        }
        val result = start(context, StartMethod.Wireless)
        _status.value = _status.value.copy(
            pairing = _status.value.pairing.copy(
                code = "",
                phase = if (result.isSuccess) PairingPhase.Done else PairingPhase.Failed,
            ),
        )
        stopPairingWait()
        result
    }

    fun showPairingPrompt(context: Context, code: String, host: String?, port: Int?) {
        onPairingCodeRead(context, code, host, port)
    }

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
            apkPath = context.applicationInfo.sourceDir,
            pcAdbCommand = pcAdbCommand(context),
            lastPort = lastPort(context),
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
            val starter = ensureStarter(app)
                ?: return@withContext Result.failure(IllegalStateException("libshizuku.so missing"))
            if (!starter.exists() || starter.length() == 0L) {
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
                    val r = TelosShellEngine.execResult(cmd, timeoutMs = 45_000L)
                    log.append(r.stdout).append(r.stderr)
                    waitBinder()
                }
                StartMethod.Wireless, StartMethod.Usb, StartMethod.Tcp -> {
                    enableWirelessDebugging(app)
                    log.appendLine("wireless $cmd")
                    val r = TelosWirelessAdb.start(app, cmd)
                    log.append(r.getOrElse { it.message.orEmpty() })
                    r.isSuccess && waitBinder()
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
