package de.mm20.launcher2.ui.webapp

import android.content.Context
import android.util.Log
import android.webkit.WebResourceResponse
import de.mm20.launcher2.ui.R
import java.io.ByteArrayInputStream

/**
 * Hosts-file-style ad/tracker blocking for the embedded web app WebView. Not a full
 * EasyList/EasyPrivacy cosmetic-filter engine - see [R.raw.web_app_blocklist] for the domain
 * list and rationale. Block lists the user switched on (see BlockLists) are checked in addition.
 */
class WebAdBlocker(context: Context) {
    private val blockedHosts: Set<String> by lazy { loadHosts(context) }

    init {
        // load the user's downloaded block lists off the main thread
        de.mm20.launcher2.comms.blocklist.BlockLists.ensureWebLoaded(context)
    }

    fun shouldBlock(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val lower = host.lowercase()
        if (blockedHosts.any { lower == it || lower.endsWith(".$it") }) return true
        return de.mm20.launcher2.comms.blocklist.BlockLists.isWebBlocked(lower)
    }

    /** An empty 200 response - blocks the request without surfacing a network error to the page. */
    fun blockedResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    private fun loadHosts(context: Context): Set<String> = try {
        context.resources.openRawResource(R.raw.web_app_blocklist).bufferedReader().useLines { lines ->
            lines
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load web app ad block list", e)
        emptySet()
    }

    private companion object {
        const val TAG = "WebAdBlocker"
    }
}
