package com.telos.desktop.vault

import com.telos.desktop.linux.Sys
import com.telos.desktop.platform.PlatformEnvironment
import com.telos.desktop.session.SessionCompositor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/**
 * Session OpSec helpers (VOS-6 first slice): USB dead-man, core-dump block,
 * secure clipboard vaulting / auto-clear, and light host attestation into the audit log.
 */
class TelosOpSecGuard {
    var usbSerial: String? = null
    var panic: (() -> Unit)? = null
    var secureClipboardEnabled: Boolean = true
    /** Auto-clear system clipboard after this many seconds (0 = off). */
    var clipboardClearSeconds: Int = DEFAULT_CLEAR_SECONDS

    private var usbJob: Job? = null
    private var clipboardJob: Job? = null
    @Volatile private var vaultedClip: String? = null
    @Volatile private var lastSeenClip: String? = null
    @Volatile private var lastSeenAtMs: Long = 0L

    fun enableCoreDumpBlock() {
        Sys.run("bash", "-lc", "ulimit -c 0")
        runCatching { File("/proc/self/coredump_filter").writeText("0") }
    }

    fun startUsbDeadMan(scope: CoroutineScope) {
        val serial = usbSerial ?: return
        usbJob?.cancel()
        usbJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(2000)
                if (!usbPresent(serial)) panic?.invoke()
            }
        }
    }

    fun startSessionGuards(scope: CoroutineScope) {
        startUsbDeadMan(scope)
        startClipboardWatch(scope)
    }

    fun stop() {
        usbJob?.cancel()
        usbJob = null
        clipboardJob?.cancel()
        clipboardJob = null
    }

    fun onUnlocked(detail: String = "") {
        TelosSessionAuditLog.append("unlock", detail.ifBlank { PlatformEnvironment.current.name })
        recordAttestation()
    }

    fun onPanic() {
        clearSystemClipboard()
        clearVaultedClip()
        runCatching { com.telos.desktop.overlay.TelosClipboardManager.clearHistory() }
        TelosSessionAuditLog.append("panic", "clipboard_cleared")
    }

    fun recordAttestation() {
        val snap = hostIntegritySnapshot()
        val detail = snap.entries.joinToString("; ") { "${it.key}=${it.value}" }
        TelosSessionAuditLog.append("attestation", detail)
    }

    fun hostIntegritySnapshot(): Map<String, String> {
        val os = prettyOsName()
        val compositor = resolveCompositor()
        val portable = PortableTraceGuard.isPortable()
        return linkedMapOf(
            "os" to os,
            "compositor" to compositor,
            "portable" to portable.toString(),
            "platform" to PlatformEnvironment.current.name,
        )
    }

    fun vaultedClip(): String? = vaultedClip

    fun recallVaultedClip(): String? {
        val text = vaultedClip ?: return null
        setSystemClipboard(text)
        armClearTimer()
        return text
    }

    fun clearVaultedClip() {
        vaultedClip = null
    }

    /**
     * When sensitive material is detected, keep it only in the session vault and
     * clear the system clipboard so untrusted apps cannot paste it (Linux wl-copy /
     * AWT; portable hosts: best-effort clear).
     */
    fun handleClipboardText(text: String?): Boolean {
        if (!secureClipboardEnabled || text.isNullOrBlank()) return false
        lastSeenClip = text
        lastSeenAtMs = System.currentTimeMillis()
        if (!looksSensitive(text)) {
            armClearTimer()
            return false
        }
        vaultedClip = text
        clearSystemClipboard()
        TelosSessionAuditLog.append("clipboard_vault", "sensitive_cleared")
        return true
    }

    fun clearSystemClipboard() {
        val copy = Sys.which("wl-copy")
        if (copy != null) {
            Sys.run(copy, "--clear", timeoutSec = 2)
            Sys.run(copy, timeoutSec = 2, input = "")
        }
        val xsel = Sys.which("xsel")
        if (xsel != null) {
            Sys.run(xsel, "-bc", timeoutSec = 2)
            Sys.run(xsel, "-c", timeoutSec = 2)
        }
        val xclip = Sys.which("xclip")
        if (xclip != null) {
            Sys.run(xclip, "-selection", "clipboard", timeoutSec = 2, input = "")
        }
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(""), null)
        }
    }

    fun startClipboardWatch(scope: CoroutineScope) {
        clipboardJob?.cancel()
        clipboardJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(900)
                if (!secureClipboardEnabled) continue
                val text = readSystemClipboard()
                if (!text.isNullOrBlank() && text != lastSeenClip) {
                    handleClipboardText(text)
                } else if (
                    clipboardClearSeconds > 0 &&
                    !lastSeenClip.isNullOrBlank() &&
                    System.currentTimeMillis() - lastSeenAtMs >= clipboardClearSeconds * 1000L
                ) {
                    clearSystemClipboard()
                    lastSeenClip = null
                    TelosSessionAuditLog.append("clipboard_clear", "timer_${clipboardClearSeconds}s")
                    lastSeenAtMs = System.currentTimeMillis()
                }
            }
        }
    }

    fun looksSensitive(text: String): Boolean {
        if (text.length < 8) return false
        if (PRIVATE_KEY_HEADER.containsMatchIn(text)) return true
        if (WG_KEY_LINE.containsMatchIn(text)) return true
        if (PEM_BLOCK.containsMatchIn(text)) return true
        if (IBAN.containsMatchIn(text)) return true
        if (CARD.containsMatchIn(text.replace(" ", "").replace("-", ""))) return true
        if (OTP_LINE.containsMatchIn(text) && text.length <= 12) return true
        return false
    }

    private fun armClearTimer() {
        lastSeenAtMs = System.currentTimeMillis()
    }

    private fun setSystemClipboard(text: String) {
        val copy = Sys.which("wl-copy")
        if (copy != null) Sys.run(copy, timeoutSec = 2, input = text)
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }
    }

    private fun readSystemClipboard(): String? {
        val wl = Sys.which("wl-paste")
        if (wl != null) {
            val t = Sys.run(wl, "-n", timeoutSec = 2).stdout.trim()
            if (t.isNotBlank()) return t
        }
        return runCatching {
            Toolkit.getDefaultToolkit().systemClipboard
                .getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun prettyOsName(): String {
        val release = File("/etc/os-release")
        if (release.isFile) {
            val pretty = release.readLines()
                .firstOrNull { it.startsWith("PRETTY_NAME=") }
                ?.substringAfter('=')
                ?.trim()
                ?.trim('"')
            if (!pretty.isNullOrBlank()) return pretty
        }
        return listOfNotNull(
            System.getProperty("os.name"),
            System.getProperty("os.version"),
        ).joinToString(" ").ifBlank { "unknown" }
    }

    private fun resolveCompositor(): String {
        System.getenv("TELOS_COMPOSITOR")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val hasLabwc = Sys.which("labwc") != null
        val hasNiri = Sys.which("niri") != null
        if (hasLabwc || hasNiri) {
            return SessionCompositor.resolve(null, hasLabwc, hasNiri)
        }
        System.getenv("XDG_CURRENT_DESKTOP")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return when {
            System.getenv("WAYLAND_DISPLAY") != null -> "wayland"
            System.getenv("DISPLAY") != null -> "x11"
            else -> PlatformEnvironment.current.name
        }
    }

    private fun usbPresent(serial: String): Boolean {
        val roots = listOf(File("/dev/disk/by-id"), File("/sys/bus/usb/devices"), File("/run/media"), File("/media"))
        return roots.any { root ->
            root.exists() && root.walkTopDown().maxDepth(4).any { it.name.contains(serial, ignoreCase = true) }
        }
    }

    companion object {
        const val DEFAULT_CLEAR_SECONDS = 45

        private val PRIVATE_KEY_HEADER = Regex(
            "-----BEGIN (?:RSA |EC |OPENSSH |PRIVATE )?PRIVATE KEY-----",
            RegexOption.IGNORE_CASE,
        )
        private val PEM_BLOCK = Regex("-----BEGIN [A-Z0-9 ]+-----")
        private val WG_KEY_LINE = Regex(
            """(?im)^\s*(?:PrivateKey|PresharedKey)\s*=\s*\S+""",
        )
        private val IBAN = Regex("""\b[A-Z]{2}\d{2}[A-Z0-9]{11,30}\b""")
        private val CARD = Regex("""\b(?:4\d{12}(?:\d{3})?|5[1-5]\d{14}|3[47]\d{13})\b""")
        private val OTP_LINE = Regex("""^\s*\d{6,8}\s*$""")
    }
}
