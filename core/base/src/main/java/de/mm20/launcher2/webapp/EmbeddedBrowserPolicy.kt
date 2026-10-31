package de.mm20.launcher2.webapp

object EmbeddedBrowserPolicy {
    const val WebView = "webview"
    const val Default = WebView

    fun select(preference: String): String = WebView
}
