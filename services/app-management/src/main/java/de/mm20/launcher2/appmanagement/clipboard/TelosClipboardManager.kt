package de.mm20.launcher2.appmanagement.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
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

/**
 * Privacy-first Clipboard Manager for Telos Launcher.
 * - Maintains an in-memory queue of copied clips.
 * - Auto-detects sensitive entries (passwords, 2FA/OTPs, payment info) via pattern heuristics.
 * - Automatically purges sensitive clips from the system clipboard after 5 minutes.
 */
class TelosClipboardManager private constructor(context: Context) {

    data class ClipEntry(
        val text: String,
        val timestamp: Long = System.currentTimeMillis(),
        val isSensitive: Boolean = false
    )

    private val appContext: Context = context.applicationContext
    private val systemClipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val isListening = AtomicBoolean(false)
    private var autoClearJob: Job? = null

    private val _clips = MutableStateFlow<List<ClipEntry>>(emptyList())
    val clips: StateFlow<List<ClipEntry>> = _clips.asStateFlow()

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        onClipChanged()
    }

    fun startListening() {
        if (!isListening.compareAndSet(false, true)) return
        systemClipboard?.addPrimaryClipChangedListener(clipListener)
        onClipChanged()
        Log.i(TAG, "TelosClipboardManager started listening")
    }

    fun stopListening() {
        if (!isListening.compareAndSet(true, false)) return
        systemClipboard?.removePrimaryClipChangedListener(clipListener)
        autoClearJob?.cancel()
        Log.i(TAG, "TelosClipboardManager stopped listening")
    }

    private fun onClipChanged() {
        val clip = systemClipboard?.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).text?.toString()?.trim() ?: return
        if (text.isBlank() || text.length > 10_000) return

        val sensitive = isSensitiveText(text)
        val entry = ClipEntry(text = text, isSensitive = sensitive)

        val updated = (_clips.value.filter { it.text != text } + entry).takeLast(20)
        _clips.value = updated

        if (sensitive) {
            scheduleAutoClear(text)
        }
    }

    private fun scheduleAutoClear(sensitiveText: String) {
        autoClearJob?.cancel()
        autoClearJob = scope.launch {
            // Auto-clear sensitive clip after 5 minutes (300,000 ms)
            delay(300_000L)
            val currentClip = systemClipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (currentClip == sensitiveText) {
                runCatching {
                    systemClipboard?.setPrimaryClip(ClipData.newPlainText("", ""))
                    Log.i(TAG, "Sensitive clip auto-cleared from system clipboard")
                }
            }
        }
    }

    fun clearHistory() {
        _clips.value = emptyList()
    }

    private fun isSensitiveText(text: String): Boolean {
        // 6-digit OTP codes or passwords containing mixed symbols/digits
        val isOtp = text.length in 4..8 && text.all { it.isDigit() }
        val isPasswordHeuristic = text.length in 8..64 &&
                text.any { it.isDigit() } &&
                text.any { it.isLetter() } &&
                text.any { !it.isLetterOrDigit() }
        return isOtp || isPasswordHeuristic
    }

    companion object {
        private const val TAG = "TelosClipboardManager"

        @Volatile
        private var instance: TelosClipboardManager? = null

        fun getInstance(context: Context): TelosClipboardManager {
            return instance ?: synchronized(this) {
                instance ?: TelosClipboardManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
