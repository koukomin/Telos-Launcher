package de.mm20.launcher2.appmanagement

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sensitive Clipboard Guard & Auto-Timeout Helper for Telos Launcher.
 * Marks sensitive copied text with EXTRA_IS_SENSITIVE (Android 13+) to prevent preview overlays,
 * and automatically schedules clipboard clearing after a configurable timeout (default 60s).
 */
object TelosClipboardGuard {

    /**
     * Copies text to system clipboard with sensitive flag and automatic auto-clear after [timeoutMs].
     */
    fun copySensitiveText(
        context: Context,
        label: String,
        text: String,
        scope: CoroutineScope,
        timeoutMs: Long = 60000L
    ) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
            clip.description.extras = extras
        }

        manager.setPrimaryClip(clip)

        // Schedule automatic clearing
        scope.launch(Dispatchers.Main) {
            delay(timeoutMs)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    manager.clearPrimaryClip()
                } else {
                    manager.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            } catch (e: Exception) {
                // Ignore non-fatal clipboard clear errors
            }
        }
    }
}
