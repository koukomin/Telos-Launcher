package de.mm20.launcher2.appmanagement.shizuku

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import de.mm20.launcher2.i18n.R as I18nR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reads the Wireless debugging pairing dialog (Shizuku-Next style) and pairs
 * without leaving that Settings screen. A TYPE_ACCESSIBILITY_OVERLAY field
 * stays on top if the user still needs to type the 6 digits.
 */
class TelosAdbPairingAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pairing = false
    private var lastAttempt: String? = null
    private var readsLeft = 0
    private var overlay: TelosAdbPairingOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        scope.launch {
            TelosShizukuHost.status.collect { status ->
                if (status.pairing.phase == TelosShizukuHost.PairingPhase.Searching) lastAttempt = null
                if (status.pairing.phase in setOf(TelosShizukuHost.PairingPhase.Idle, TelosShizukuHost.PairingPhase.Done, TelosShizukuHost.PairingPhase.Failed)) {
                    overlay?.dismiss()
                    overlay = null
                } else if (status.pairing.phase != TelosShizukuHost.PairingPhase.Working) {
                    val type = TelosAdbPairingOverlay.overlayTypeFor(this@TelosAdbPairingAccessibilityService, true)!!
                    val sheet = overlay ?: TelosAdbPairingOverlay(this@TelosAdbPairingAccessibilityService, type).also { overlay = it }
                    sheet.show("", status.pairing.host, status.pairing.port)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || pairing || event.packageName?.toString() != "com.android.settings") return
        val age = android.os.SystemClock.elapsedRealtime() - TelosShizukuHost.wirelessNavigationRequestedAt
        if (age in 0..15_000 && TelosShizukuHost.wirelessNavigationRequestedAt != 0L) {
            rootInActiveWindow?.takeIf { it.packageName?.toString() == "com.android.settings" }?.let { root ->
                val resources = packageManager.getResourcesForApplication("com.android.settings")
                val id = resources.getIdentifier("enable_adb_wireless", "string", "com.android.settings")
                val label = if (id != 0) resources.getString(id) else getString(I18nR.string.telos_shizuku_wireless)
                for (node in root.findAccessibilityNodeInfosByText(label)) {
                    if (node.text?.toString() != label) continue
                    var row: AccessibilityNodeInfo? = node
                    while (row != null && !row.isClickable) row = row.parent
                    if (row?.isCheckable == false) row.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
        }
        val phase = TelosShizukuHost.status.value.pairing.phase
        if (!TelosShizukuHost.status.value.pairing.automatic) return
        if (phase != TelosShizukuHost.PairingPhase.Searching && phase != TelosShizukuHost.PairingPhase.WaitingForCode) return
        handler.removeCallbacks(readAgain)
        readsLeft = 20
        readAgain.run()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        connected = false
        handler.removeCallbacksAndMessages(null)
        overlay?.dismiss()
        overlay = null
        scope.cancel()
        super.onDestroy()
    }

    private val readAgain = object : Runnable {
        override fun run() {
            if (!TelosShizukuHost.status.value.pairing.automatic) return
            if (pairing || TelosShizukuHost.status.value.pairing.phase !in setOf(TelosShizukuHost.PairingPhase.Searching, TelosShizukuHost.PairingPhase.WaitingForCode)) return
            val dialog = readPairingDialog()
            if (dialog == null) {
                if (readsLeft-- > 0) handler.postDelayed(this, 250)
                return
            }
            val signature = listOfNotNull(dialog.code, dialog.host, dialog.port?.toString()).joinToString(":")
            if (signature == lastAttempt) return
            if (dialog.code.length != 6) return
            lastAttempt = signature
            pairing = true
            TelosShizukuHost.onPairingCodeRead(this@TelosAdbPairingAccessibilityService, dialog.code, dialog.host, dialog.port)
            scope.launch {
                val r = TelosShizukuHost.submitPairingCode(
                    this@TelosAdbPairingAccessibilityService,
                    dialog.code,
                    dialog.host,
                    dialog.port,
                )
                Toast.makeText(
                    applicationContext,
                    if (r.isSuccess) getString(I18nR.string.telos_shizuku_pair_ok)
                    else r.exceptionOrNull()?.message ?: getString(I18nR.string.telos_shizuku_pair_fail),
                    Toast.LENGTH_LONG,
                ).show()
                if (r.isSuccess) {
                    overlay?.dismiss()
                    overlay = null
                } else {
                    // Retry only a new code or a new user-initiated session.
                }
                pairing = false
            }
        }
    }

    private fun ensureOverlay(dialog: Dialog) {
        val type = TelosAdbPairingOverlay.overlayTypeFor(this, accessibility = true) ?: return
        val sheet = overlay ?: TelosAdbPairingOverlay(this, type).also { overlay = it }
        sheet.show(dialog.code, dialog.host, dialog.port)
    }

    private data class Dialog(val code: String, val host: String?, val port: Int?)

    private fun readPairingDialog(): Dialog? {
        val windows = runCatching { windows }.getOrNull().orEmpty()
        for (window in windows) {
            val root = window?.root ?: continue
            if (root.packageName?.toString() != "com.android.settings") continue
            val texts = mutableListOf<String>()
            collectText(root, texts)
            if (texts.none { looksLikePairingDialog(it) }) continue
            val code = extractCode(texts).orEmpty()
            val hostPort = texts.asSequence().mapNotNull { HOST_PORT.find(it) }.firstOrNull()
            return Dialog(
                code = code,
                host = hostPort?.groupValues?.get(1),
                port = hostPort?.groupValues?.get(2)?.toIntOrNull(),
            )
        }
        return null
    }

    private fun extractCode(texts: List<String>): String? = PairingCodeParser.extract(texts)

    private fun looksLikePairingDialog(text: String): Boolean {
        val t = text.lowercase()
        val greek = (t.contains("κωδικ") && (t.contains("συζευξ") || t.contains("ζευξ") || t.contains("σύζευξ") || t.contains("ζεύξ")))
        return t.contains("pairing code") ||
            t.contains("pair with device") ||
            t.contains("pair device with pairing") ||
            t.contains("wifi pairing") ||
            t.contains("wi-fi pairing") ||
            t.contains("pairing-code") ||
            t.contains("kopplungscode") ||
            t.contains("code d'association") ||
            t.contains("codigo de emparej") ||
            t.contains("código de emparej") ||
            greek
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            node.hintText?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, out)
            child.recycle()
        }
    }

    companion object {
        private val CODE = Regex("^\\d{6}$")
        private val SPACED_CODE = Regex("^\\d{3}\\s*\\d{3}$")
        @Volatile internal var connected = false
            private set
        private val HOST_PORT = Regex("(\\d{1,3}(?:\\.\\d{1,3}){3}):(\\d{2,5})")
    }
}
