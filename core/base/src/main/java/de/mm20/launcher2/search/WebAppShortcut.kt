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

    /**
     * Optional CSS injected into every page loaded by this shortcut's embedded WebView (not
     * applied when opened via Custom Tabs).
     */
    val customCss: String?

    override val preferDetailsOverLaunch: Boolean
        get() = false

    val showInGrid: Boolean
    val showInPanel: Boolean

    val order: Int

    val iconSource: IconSource

    val notificationsEnabled: Boolean

    /**
     * Per web app override of the global ad blocker setting. Only applies to the embedded
     * WebView (Custom Tabs are controlled by the browser app).
     */
    val adBlockMode: AdBlockMode

    /**
     * Per web app cookie options (embedded WebView only). Both default to [CookieMode.Global]
     * so existing and restored web apps keep following the global settings.
     */
    val cookieOptions: CookieOptions

    enum class CookieMode {
        /** Follow the global setting. */
        Global,
        Accept,
        Block,
    }

    data class CookieOptions(
        val cookies: CookieMode = CookieMode.Global,
        val thirdPartyCookies: CookieMode = CookieMode.Global,
    )

    enum class AdBlockMode {
        /** Follow the global setting. */
        Global,
        On,
        Off,
    }

    enum class IconSource {
        Website,
        System,
        Custom
    }
}
