package de.mm20.launcher2.appmanagement.shizuku

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo


/**
 * Reads the wireless-debugging pairing dialog the same way Shizuku-Next does,
 * then pairs with [TelosAdbPairer] and pops a code dialog if the user still needs to confirm.
 */
class TelosAdbPairingAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var pairing = false
    private var lastAttempt: String? = null
    private var readsLeft = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!hostsPairingDialog(event.packageName?.toString())) return
        handler.removeCallbacks(readAgain)
        readsLeft = 15
        readAgain.run()
    }

    override fun onInterrupt() = Unit

    private val readAgain = object : Runnable {
        override fun run() {
            if (pairing) return
            val dialog = readPairingDialog() ?: run {
                if (readsLeft-- > 0) handler.postDelayed(this, 400)
                return
            }
            val signature = listOfNotNull(dialog.code, dialog.host, dialog.port?.toString()).joinToString(":")
            if (signature == lastAttempt) return
            lastAttempt = signature
            pairing = true
            TelosShizukuHost.showPairingPrompt(
                this@TelosAdbPairingAccessibilityService,
                dialog.code,
                dialog.host,
                dialog.port,
            )
            pairing = false
        }
    }

    private fun hostsPairingDialog(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        return pkg.contains("settings", ignoreCase = true) ||
            pkg.contains("debug", ignoreCase = true)
    }

    private data class Dialog(val code: String, val host: String?, val port: Int?)

    private fun readPairingDialog(): Dialog? {
        val windows = runCatching { windows }.getOrNull().orEmpty()
        for (window in windows) {
            val root = window?.root ?: continue
            if (!hostsPairingDialog(root.packageName?.toString())) continue
            val texts = mutableListOf<String>()
            collectText(root, texts)
            if (texts.none { looksLikePairingDialog(it) }) continue
            val code = texts.firstOrNull { CODE.matches(it.filter { ch -> ch.isDigit() } ) && it.filter { ch -> ch.isDigit() }.length == 6 }
                ?.filter { it.isDigit() }
                ?: texts.map { it.filter { ch -> ch.isDigit() } }.firstOrNull { it.length == 6 }
                ?: run {
                    val joined = texts.joinToString("") { it.filter { ch -> ch.isDigit() } }
                    Regex("(\\d{6})").find(joined)?.groupValues?.get(1)
                }
                ?: continue
            val hostPort = texts.asSequence().mapNotNull { HOST_PORT.find(it) }.firstOrNull()
            return Dialog(
                code = code,
                host = hostPort?.groupValues?.get(1),
                port = hostPort?.groupValues?.get(2)?.toIntOrNull(),
            )
        }
        return null
    }

    private fun looksLikePairingDialog(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("pairing code") ||
            t.contains("pair with device") ||
            t.contains("wifi pairing") ||
            t.contains("wi-fi pairing") ||
            t.contains("κωδικ") ||
            t.contains("σύζευξ") ||
            t.contains("pairing")
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
