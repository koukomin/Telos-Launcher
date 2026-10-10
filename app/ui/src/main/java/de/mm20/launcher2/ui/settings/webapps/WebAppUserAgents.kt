package de.mm20.launcher2.ui.settings.webapps

object WebAppUserAgents {
    const val MODE_DEFAULT = "default"
    const val MODE_DESKTOP = "desktop"
    const val MODE_CUSTOM = "custom"

    private const val MAX_LENGTH = 512

    private const val DESKTOP =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"

    /** Strips control characters (header injection) and caps the length. */
    fun sanitize(value: String): String =
        value.filter { it.code in 0x20..0x7E }.trim().take(MAX_LENGTH)

    /** The user agent to apply, or null to keep the WebView's default one. */
    fun resolve(mode: String, custom: String): String? = when (mode) {
        MODE_DESKTOP -> DESKTOP
        MODE_CUSTOM -> sanitize(custom).takeIf { it.isNotEmpty() }
        else -> null
    }
}
