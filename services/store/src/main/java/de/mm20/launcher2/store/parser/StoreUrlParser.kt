package de.mm20.launcher2.store.parser

import android.net.Uri
import de.mm20.launcher2.store.model.AppSource

object StoreUrlParser {

    /**
     * Parses a user-provided URL into an [AppSource].
     * Example GitHub: "https://github.com/MM2-0/Kvaesitso" -> AppSource.GitHub("MM2-0", "Kvaesitso")
     * Example F-Droid: "https://f-droid.org/packages/org.mozilla.fennec_fdroid/" -> AppSource.FDroid("org.mozilla.fennec_fdroid")
     */
    fun parseUrl(urlStr: String): AppSource? {
        val uri = try {
            Uri.parse(urlStr.trim())
        } catch (e: Exception) {
            return null
        }

        val host = uri.host?.lowercase() ?: return null
        val pathSegments = uri.pathSegments ?: return null

        if (host == "github.com" || host == "www.github.com") {
            if (pathSegments.size >= 2) {
                val owner = pathSegments[0]
                val repo = pathSegments[1]
                return AppSource.GitHub(owner, repo)
            }
        }

        if (host == "f-droid.org" || host == "www.f-droid.org") {
            // Usually format is /en/packages/<pkg>/ or /packages/<pkg>/
            val packagesIndex = pathSegments.indexOf("packages")
            if (packagesIndex >= 0 && pathSegments.size > packagesIndex + 1) {
                val packageName = pathSegments[packagesIndex + 1]
                return AppSource.FDroid(packageName)
            }
        }

        return null
    }
}
