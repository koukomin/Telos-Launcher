package de.mm20.launcher2.webappshortcuts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Converts any website URL into an isolated Virtual Web App (PWA Shortcut)
 * with a standalone viewport, custom icon, and ad-blocking flags.
 */
object TelosWebAppCreator {

    private const val TAG = "TelosWebAppCreator"

    data class VirtualWebAppSpec(
        val url: String,
        val title: String,
        val domain: String,
        val isAdBlockEnabled: Boolean = true,
        val isDesktopModeEnabled: Boolean = false
    )

    suspend fun createWebAppSpec(urlInput: String, customTitle: String? = null): VirtualWebAppSpec? = withContext(Dispatchers.Default) {
        runCatching {
            val formattedUrl = if (!urlInput.startsWith("http://") && !urlInput.startsWith("https://")) {
                "https://$urlInput"
            } else {
                urlInput
            }

            val parsedUrl = URL(formattedUrl)
            val domain = parsedUrl.host.removePrefix("www.")
            val title = customTitle?.takeIf { it.isNotBlank() }
                ?: domain.replaceFirstChar { it.uppercase() }

            VirtualWebAppSpec(
                url = formattedUrl,
                title = title,
                domain = domain,
                isAdBlockEnabled = true
            ).also {
                Log.i(TAG, "Created Virtual Web App spec for ${it.title} (${it.url})")
            }
        }.getOrNull()
    }
}
