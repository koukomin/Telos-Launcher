package de.mm20.launcher2.ui.comms.radio

import android.content.Context
import android.content.Intent
import android.net.Uri
import de.mm20.launcher2.comms.model.RadioStation
import de.mm20.launcher2.ui.R

/** A station that came in from outside (share text, deep link or stream link), not yet confirmed by the user */
data class RadioCandidate(val name: String, val url: String, val logo: String)

/** Sharing of stations as text with a telos-radio://add deep link, and safe parsing of what comes back in. */
object RadioShare {
    const val SCHEME = "telos-radio"
    const val MAX_URL = 2048
    const val MAX_NAME = 120
    const val MAX_TEXT = 8192

    /** http(s) only, no credentials, bounded length. Returns the cleaned URL or null. */
    fun cleanUrl(raw: String?): String? {
        var s = raw?.trim().orEmpty()
        if (s.isEmpty() || s.length > MAX_URL) return null
        // icy:// is the old Shoutcast spelling of a plain http stream
        if (s.startsWith("icy://", ignoreCase = true)) s = "http://" + s.substring(6)
        val uri = runCatching { Uri.parse(s) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
        return s
    }

    fun cleanName(raw: String?): String =
        raw.orEmpty().filter { !it.isISOControl() }.trim().take(MAX_NAME)

    fun shareText(station: RadioStation): String {
        val link = Uri.Builder().scheme(SCHEME).authority("add")
            .appendQueryParameter("name", station.name)
            .appendQueryParameter("url", station.streamUrl)
            .apply {
                if (station.faviconUrl.length in 1..512 && cleanUrl(station.faviconUrl) != null)
                    appendQueryParameter("logo", station.faviconUrl)
            }
            .build().toString()
        val web = cleanUrl(station.homepage) ?: cleanUrl(station.streamUrl)
        return listOfNotNull(station.name, web, link).joinToString("\n")
    }

    fun share(context: Context, station: RadioStation) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, station.name)
            putExtra(Intent.EXTRA_TEXT, shareText(station))
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.au10_radio_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private val urlRegex = Regex("""(?:https?|icy)://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val deepRegex = Regex("""telos-radio://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    private fun fromDeepLink(uri: Uri): RadioCandidate? {
        if (!uri.scheme.equals(SCHEME, true) || !uri.host.equals("add", true)) return null
        val url = cleanUrl(uri.getQueryParameter("url")) ?: return null
        val logo = cleanUrl(uri.getQueryParameter("logo")).orEmpty()
        val name = cleanName(uri.getQueryParameter("name")).ifBlank { runCatching { Uri.parse(url).host }.getOrNull().orEmpty() }
        return RadioCandidate(name, url, logo)
    }

    /** Reads an incoming intent. Never touches the network. Returns null when nothing valid is in it. */
    fun parse(intent: Intent?): RadioCandidate? {
        intent ?: return null
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return null
                fromDeepLink(data)?.let { return it }
                val url = cleanUrl(data.toString()) ?: return null
                return RadioCandidate(Uri.parse(url).host.orEmpty(), url, "")
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.take(MAX_TEXT) ?: return null
                deepRegex.find(text)?.let { m -> fromDeepLink(Uri.parse(m.value))?.let { return it } }
                val url = urlRegex.findAll(text).mapNotNull { cleanUrl(it.value) }.firstOrNull() ?: return null
                val title = cleanName(intent.getStringExtra(Intent.EXTRA_SUBJECT)).ifBlank {
                    cleanName(text.lineSequence().firstOrNull { it.isNotBlank() && !it.contains("://") })
                }
                return RadioCandidate(title.ifBlank { Uri.parse(url).host.orEmpty() }, url, "")
            }
        }
        return null
    }
}
