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
    private val blockedHosts: de.mm20.launcher2.comms.blocklist.DomainSet by lazy { loadHosts(context) }

    init {
        // load the user's downloaded block lists off the main thread
        de.mm20.launcher2.comms.blocklist.BlockLists.ensureWebLoaded(context)
    }

    fun shouldBlock(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        // DomainSet lowercases and walks the parent domains with hash lookups (no per-entry scan)
        if (blockedHosts.matches(host)) return true
        return de.mm20.launcher2.comms.blocklist.BlockLists.isWebBlocked(host)
    }

    /** An empty 200 response - blocks the request without surfacing a network error to the page. */
    fun blockedResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    private fun loadHosts(context: Context): de.mm20.launcher2.comms.blocklist.DomainSet = try {
        context.resources.openRawResource(R.raw.web_app_blocklist).bufferedReader().useLines { lines ->
            de.mm20.launcher2.comms.blocklist.DomainSet.of(
                lines
                    .map { it.substringBefore('#').trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .toList()
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load web app ad block list", e)
        de.mm20.launcher2.comms.blocklist.DomainSet.EMPTY
    }

    private companion object {
        const val TAG = "WebAdBlocker"
    }
}
