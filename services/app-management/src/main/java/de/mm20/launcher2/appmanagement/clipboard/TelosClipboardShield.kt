package de.mm20.launcher2.appmanagement.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import de.mm20.launcher2.appmanagement.stealth.TelosPrivilegedDaemon
import de.mm20.launcher2.coroutines.TelosDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class ClipboardScrubMode {
    Off,
    Smart,
    Strict,
}

data class ClipboardVaultEntry(
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sensitive: Boolean = false,
)

object TelosClipboardShield {
    private const val TAG = "TelosClipboardShield"
    private const val PREFS = "telos_clipboard_shield"
    private const val KEY_MODE = "scrub_mode"
    private const val KEY_AUTO_CLEAR = "auto_clear"
    private const val KEY_AUTO_RESTORE = "auto_restore"
    private const val KEY_APPOPS = "appops_lockdown"
    private const val KEY_BLOCKED = "blocked_packages"
    private const val AUTO_CLEAR_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listening = AtomicBoolean(false)
    private var autoClearJob: Job? = null
    private val vault = AtomicReference<ClipboardVaultEntry?>(null)

    private val _vault = MutableStateFlow<ClipboardVaultEntry?>(null)
    val vaultEntry: StateFlow<ClipboardVaultEntry?> = _vault.asStateFlow()

    private val _mode = MutableStateFlow(ClipboardScrubMode.Smart)
    val mode: StateFlow<ClipboardScrubMode> = _mode.asStateFlow()

    private val _autoClear = MutableStateFlow(false)
    val autoClear: StateFlow<Boolean> = _autoClear.asStateFlow()

    private val _autoRestore = MutableStateFlow(true)
    val autoRestore: StateFlow<Boolean> = _autoRestore.asStateFlow()

    private val _appOpsLockdown = MutableStateFlow(true)
    val appOpsLockdown: StateFlow<Boolean> = _appOpsLockdown.asStateFlow()

    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    val blockedPackages: StateFlow<Set<String>> = _blocked.asStateFlow()

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        onClipChanged()
    }

    private var appContext: Context? = null

    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        loadPrefs(app)
        if (!listening.compareAndSet(false, true)) return
        clipboard(app)?.addPrimaryClipChangedListener(clipListener)
    }

    fun setMode(context: Context, mode: ClipboardScrubMode) {
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
        _mode.value = mode
    }

    fun setAutoClear(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CLEAR, enabled).apply()
        _autoClear.value = enabled
        if (!enabled) autoClearJob?.cancel()
    }

    fun setAutoRestore(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_RESTORE, enabled).apply()
        _autoRestore.value = enabled
    }

    fun setAppOpsLockdown(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_APPOPS, enabled).apply()
        _appOpsLockdown.value = enabled
    }

    fun isBlocked(context: Context, packageName: String): Boolean {
        return blockedSet(context).contains(packageName)
    }

    fun setBlocked(context: Context, packageName: String, blocked: Boolean) {
        val app = context.applicationContext
        val set = blockedSet(app).toMutableSet()
        if (blocked) set.add(packageName) else set.remove(packageName)
        prefs(app).edit().putStringSet(KEY_BLOCKED, set).apply()
        _blocked.value = set
        if (_appOpsLockdown.value) {
            scope.launch(TelosDispatchers.Disk) {
                applyReadClipboardAppOp(packageName, ignore = blocked)
            }
        }
    }

    fun onPreLaunch(context: Context, packageName: String) {
        val app = context.applicationContext
        val shield = isBlocked(app, packageName) || _mode.value == ClipboardScrubMode.Strict
        val mode = _mode.value
        if (mode == ClipboardScrubMode.Off && !isBlocked(app, packageName)) return
        val cm = clipboard(app) ?: return
        val text = primaryText(cm) ?: return
        val sensitive = isSensitiveText(text)
        val shouldScrub = when {
            mode == ClipboardScrubMode.Strict -> true
            shield && mode == ClipboardScrubMode.Smart && sensitive -> true
            isBlocked(app, packageName) && (mode == ClipboardScrubMode.Smart && sensitive || mode == ClipboardScrubMode.Strict) -> true
            isBlocked(app, packageName) && mode != ClipboardScrubMode.Off -> true
            mode == ClipboardScrubMode.Smart && sensitive -> shield
            else -> false
        }
        if (!shouldScrub) return
        stash(text, sensitive)
        clearPrimary(cm)
    }

    fun onHomeResumed(context: Context) {
        if (!_autoRestore.value) return
        restoreVault(context)
    }

    fun restoreVault(context: Context) {
        val entry = vault.get() ?: return
        val cm = clipboard(context.applicationContext) ?: return
        runCatching {
            cm.setPrimaryClip(ClipData.newPlainText("telos-vault", entry.text))
        }
    }

    fun discardVault() {
        vault.set(null)
        _vault.value = null
    }

    fun isSensitiveText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 10_000) return false
        if (IBAN.containsMatchIn(trimmed.replace(" ", ""))) return true
        if (CARD.containsMatchIn(trimmed.replace(" ", "").replace("-", ""))) return true
        if (ETH.containsMatchIn(trimmed) || BTC.containsMatchIn(trimmed) || BECH32.containsMatchIn(trimmed)) return true
        if (trimmed.length in 4..8 && trimmed.all { it.isDigit() }) return true
        if (trimmed.length in 8..64 &&
            trimmed.any { it.isDigit() } &&
            trimmed.any { it.isLetter() } &&
            trimmed.any { !it.isLetterOrDigit() }
        ) {
            return true
        }
        return false
    }

    private fun onClipChanged() {
        val app = appContext ?: return
        val cm = clipboard(app) ?: return
        val text = primaryText(cm) ?: return
        if (_autoClear.value) {
            autoClearJob?.cancel()
            autoClearJob = scope.launch {
                delay(AUTO_CLEAR_MS)
                val current = primaryText(cm)
                if (current == text) {
                    stash(text, isSensitiveText(text))
                    clearPrimary(cm)
                }
            }
        }
    }

    private fun stash(text: String, sensitive: Boolean) {
        val entry = ClipboardVaultEntry(text = text, sensitive = sensitive)
        vault.set(entry)
        _vault.value = entry
    }

    private fun clearPrimary(cm: ClipboardManager) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                cm.clearPrimaryClip()
            } else {
                cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }.onFailure { Log.w(TAG, "clearPrimaryClip failed", it) }
    }

    private suspend fun applyReadClipboardAppOp(packageName: String, ignore: Boolean) {
        val mode = if (ignore) "ignore" else "allow"
        TelosPrivilegedDaemon.exec("cmd appops set $packageName READ_CLIPBOARD $mode")
    }

    private fun loadPrefs(context: Context) {
        val p = prefs(context)
        _mode.value = runCatching {
            ClipboardScrubMode.valueOf(p.getString(KEY_MODE, ClipboardScrubMode.Smart.name)!!)
        }.getOrDefault(ClipboardScrubMode.Smart)
        _autoClear.value = p.getBoolean(KEY_AUTO_CLEAR, false)
        _autoRestore.value = p.getBoolean(KEY_AUTO_RESTORE, true)
        _appOpsLockdown.value = p.getBoolean(KEY_APPOPS, true)
        _blocked.value = p.getStringSet(KEY_BLOCKED, emptySet()).orEmpty()
    }

    private fun blockedSet(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_BLOCKED, emptySet()).orEmpty()
    }

    private fun clipboard(context: Context): ClipboardManager? {
        return context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    private fun primaryText(cm: ClipboardManager): String? {
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val IBAN = Regex("\\b[A-Z]{2}\\d{2}[A-Z0-9]{10,30}\\b")
    private val CARD = Regex("\\b(?:\\d[ -]?){13,19}\\b")
    private val ETH = Regex("\\b0x[a-fA-F0-9]{40}\\b")
    private val BTC = Regex("\\b[13][a-km-zA-HJ-NP-Z1-9]{25,34}\\b")
    private val BECH32 = Regex("\\bbc1[a-z0-9]{25,62}\\b")
}
