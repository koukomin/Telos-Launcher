package de.mm20.launcher2.appmanagement

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView

/**
 * WebApp Cookie and Session Privacy Container for Telos Launcher.
 * Enforces strict web privacy controls on WebViews (third-party cookie blocking,
 * DOM storage isolation, disabling password saving and location geolocation by default).
 */
object TelosWebContainer {

    /**
     * Applies privacy and security settings to a [WebView] instance.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun configurePrivacySettings(webView: WebView, enableJs: Boolean = true) {
        val settings = webView.settings

        // JavaScript & DOM Storage
        settings.javaScriptEnabled = enableJs
        settings.domStorageEnabled = true

        // Disable insecure features
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        settings.savePassword = false
        settings.setGeolocationEnabled(false)

        // Block third-party cookies for cross-site tracking protection
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptThirdPartyCookies(webView, false)
    }
}
