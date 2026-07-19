package de.mm20.launcher2.search

interface WebAppShortcut : SavableSearchable {

    val url: String

    /**
     * URI of a locally stored custom icon (file/content URI), takes priority over [faviconUrl].
     */
    val iconUri: String?

    /**
     * Remote favicon/manifest icon URL, auto-detected at creation time. Used when no custom
     * icon has been picked, or as a fallback if the custom icon fails to load.
     */
    val faviconUrl: String?

    val color: Int?

    /**
     * Package name of a specific installed browser to open this web app with, via Custom Tabs.
     * `null` means the embedded in-app WebView (the default). If the package is no longer
     * installed, or no longer supports Custom Tabs, launching silently falls back to the
     * embedded WebView.
     */
    val rendererPackage: String?

    override val preferDetailsOverLaunch: Boolean
        get() = false
}
