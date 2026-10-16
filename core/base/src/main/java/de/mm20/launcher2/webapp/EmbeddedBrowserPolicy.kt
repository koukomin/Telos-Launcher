package de.mm20.launcher2.webapp

/** Pure selection policy; the hardened renderer is the preference for bundled builds. */
object EmbeddedBrowserPolicy {
    const val Ironewolf = "ironewolf"
    const val WebView = "webview"
    const val Default = Ironewolf

    fun select(preference: String, ironewolfBundled: Boolean): String =
        if (preference != WebView && ironewolfBundled) Ironewolf else WebView
}
