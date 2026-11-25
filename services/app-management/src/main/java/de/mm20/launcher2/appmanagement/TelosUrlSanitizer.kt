package de.mm20.launcher2.appmanagement

import android.net.Uri

/**
 * URL Tracking Parameter Sanitizer for Telos Launcher.
 * Strips tracking and analytics parameters (utm_*, fbclid, gclid, si, igshid, etc.)
 * from URLs to preserve user privacy when copying or sharing links.
 */
object TelosUrlSanitizer {

    private val trackingParams = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "dclid", "msclkid", "si", "igshid", "mc_eid",
        "ref", "ref_src", "ref_url", "_hsenc", "_hsmi", "feature"
    )

    /**
     * Sanitizes a given URL string by stripping known tracking query parameters.
     */
    fun sanitizeUrl(urlStr: String): String {
        if (urlStr.isBlank()) return urlStr

        return try {
            val uri = Uri.parse(urlStr)
            if (uri.scheme == null || (!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true))) {
                return urlStr
            }

            val queryParams = uri.queryParameterNames
            if (queryParams.isEmpty()) return urlStr

            val builder = uri.buildUpon().clearQuery()
            var modified = false

            for (param in queryParams) {
                if (param.lowercase() in trackingParams || param.lowercase().startsWith("utm_")) {
                    modified = true
                } else {
                    for (value in uri.getQueryParameters(param)) {
                        builder.appendQueryParameter(param, value)
                    }
                }
            }

            if (modified) builder.build().toString() else urlStr
        } catch (e: Exception) {
            urlStr
        }
    }
}
