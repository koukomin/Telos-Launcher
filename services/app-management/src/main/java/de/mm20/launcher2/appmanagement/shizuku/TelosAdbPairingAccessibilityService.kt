package de.mm20.launcher2.appmanagement.shizuku

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reads the wireless-debugging pairing dialog (Settings only) the same way Shizuku-Next
 * does: 6-digit code and optional host:port from the accessibility tree. No OCR, no AI.
 */
class TelosAdbPairingAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pairing = false
    private var lastAttempt: String? = null
    private var readsLeft = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (pairing || event == null) return
        if (!hostsPairingDialog(event.packageName?.toString())) return
        handler.removeCallbacks(readAgain)
        readsLeft = 12
        readAgain.run()
    }

    override fun onInterrupt() = Unit

    private val readAgain = object : Runnable {
        override fun run() {
            if (pairing) return
            val dialog = readPairingDialog() ?: run {
                if (readsLeft-- > 0) handler.postDelayed(this, 80)
                return
            }
            val signature = listOfNotNull(dialog.code, dialog.host, dialog.port?.toString()).joinToString(":")
            if (signature == lastAttempt) return
            lastAttempt = signature
            pairing = true
            TelosShizukuHost.onPairingCodeRead(
                this@TelosAdbPairingAccessibilityService,
                dialog.code,
                dialog.host,
                dialog.port,
            )
            scope.launch {
                TelosShizukuHost.start(this@TelosAdbPairingAccessibilityService, TelosShizukuHost.StartMethod.Wireless)
                pairing = false
            }
        }
    }

    private fun hostsPairingDialog(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        return pkg == "com.android.settings" ||
            pkg == "com.android.settings.intelligence" ||
            pkg == "com.samsung.android.settings" ||
            pkg.contains("settings", ignoreCase = true)
    }

    private data class Dialog(val code: String, val host: String?, val port: Int?)

    private fun readPairingDialog(): Dialog? {
        val windows = runCatching { windows }.getOrNull().orEmpty()
        for (window in windows) {
            val root = window?.root ?: continue
            if (!hostsPairingDialog(root.packageName?.toString())) continue
            val texts = mutableListOf<String>()
            collectText(root, texts)
            if (texts.none {
                    it.contains("pairing code", ignoreCase = true) ||
                        it.contains("pair with device", ignoreCase = true) ||
                        it.contains("κωδικός", ignoreCase = true)
                }
            ) continue
            val code = texts.firstOrNull { CODE.matches(it) } ?: continue
            val hostPort = texts.asSequence().mapNotNull { HOST_PORT.find(it) }.firstOrNull()
            return Dialog(
                code = code,
                host = hostPort?.groupValues?.get(1),
                port = hostPort?.groupValues?.get(2)?.toIntOrNull(),
            )
        }
        return null
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, out)
            child.recycle()
        }
    }

    companion object {
        private val CODE = Regex("^\\d{6}$")
        private val HOST_PORT = Regex("(\\d{1,3}(?:\\.\\d{1,3}){3}):(\\d{2,5})")
    }
}
