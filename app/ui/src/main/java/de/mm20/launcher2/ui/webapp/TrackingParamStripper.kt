package de.mm20.launcher2.ui.webapp

import android.net.Uri
import androidx.core.net.toUri

/**
 * Strips well-known tracking query parameters (UTM tags, click ids, etc.) from a URL, for the
 * embedded web app WebView. Only touches parameters on this list - anything else in the query
 * string is left untouched, since it may be meaningful to the page itself.
 */
object TrackingParamStripper {
    fun strip(url: String): String {
        val uri = try {
            url.toUri()
        } catch (e: Exception) {
            return url
        }
        if (uri.query.isNullOrEmpty()) return url

        val names = uri.queryParameterNames
        if (names.none { it.lowercase() in TRACKING_PARAMS }) return url

        val builder = uri.buildUpon().clearQuery()
        for (name in names) {
            if (name.lowercase() in TRACKING_PARAMS) continue
            for (value in uri.getQueryParameters(name)) {
                builder.appendQueryParameter(name, value)
            }
        }
        return builder.build().toString()
    }

    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
        "utm_name", "utm_reader", "utm_social", "utm_viz_id",
        "gclid", "gclsrc", "dclid", "wbraid", "gbraid",
        "fbclid", "igshid",
        "msclkid", "twclid", "yclid", "ttclid",
        "mc_eid", "mc_cid",
        "_ga", "_gl",
        "ref_src", "ref_url", "vero_id", "mkt_tok",
    )
}
