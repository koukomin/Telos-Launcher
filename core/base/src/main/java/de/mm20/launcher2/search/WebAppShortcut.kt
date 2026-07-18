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

    override val preferDetailsOverLaunch: Boolean
        get() = false
}
