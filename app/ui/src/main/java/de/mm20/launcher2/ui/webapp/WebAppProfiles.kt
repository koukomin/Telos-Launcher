package de.mm20.launcher2.ui.webapp

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import de.mm20.launcher2.search.WebAppShortcut

/**
 * Isolated browser storage per web app, built on the androidx.webkit multi profile API. Each web
 * app uses its own WebView profile, so cookies, localStorage etc. are never shared between web
 * apps. Where the installed WebView does not support profiles, every function degrades to the
 * previous global behaviour ([isSupported] is false). All functions must be called on the main
 * thread.
 */
object WebAppProfiles {

    fun isSupported(): Boolean = try {
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    } catch (e: Throwable) {
        false
    }

    /** Profile names only allow letters, digits and underscores. */
    private fun profileName(shortcutKey: String): String =
        "wa_" + shortcutKey.substringAfter("://").replace(Regex("[^A-Za-z0-9_]"), "_")

    /**
     * Assigns the profile of the web app [shortcutKey] to [webView]. Must be called before the
     * WebView loads anything. Returns the profile's [CookieManager], or null if profiles are
     * unavailable (the WebView then uses the shared default profile).
     */
    fun attach(webView: WebView, shortcutKey: String?): CookieManager? {
        if (shortcutKey == null || !isSupported()) return null
        return try {
            val name = profileName(shortcutKey)
            val profile = ProfileStore.getInstance().getOrCreateProfile(name)
            WebViewCompat.setProfile(webView, name)
            profile.cookieManager
        } catch (e: Throwable) {
            android.util.Log.w("WebAppProfiles", "Could not use a profile", e)
            null
        }
    }

    /** Removes cookies and web storage of a single web app. */
    fun clear(shortcutKey: String, onDone: () -> Unit = {}) {
        if (!isSupported()) {
            clearDefault(onDone)
            return
        }
        try {
            val profile = ProfileStore.getInstance().getOrCreateProfile(profileName(shortcutKey))
            clearProfile(profile.cookieManager, profile.webStorage, onDone)
        } catch (e: Throwable) {
            android.util.Log.w("WebAppProfiles", "Clearing failed", e)
            onDone()
        }
    }

    /** Removes cookies and web storage of every web app and of the shared default profile. */
    fun clearAll(onDone: () -> Unit = {}) {
        if (isSupported()) {
            try {
                val store = ProfileStore.getInstance()
                for (name in store.allProfileNames) {
                    val profile = store.getProfile(name) ?: continue
                    clearProfile(profile.cookieManager, profile.webStorage) {}
                }
            } catch (e: Throwable) {
                android.util.Log.w("WebAppProfiles", "Clearing profiles failed", e)
            }
        }
        clearDefault(onDone)
    }

    /** Deletes the profile (and with it all stored data) of a deleted web app. */
    fun delete(shortcut: WebAppShortcut) {
        if (!isSupported()) return
        val name = profileName(shortcut.key)
        try {
            if (!ProfileStore.getInstance().allProfileNames.contains(name)) return
            ProfileStore.getInstance().deleteProfile(name)
        } catch (e: Throwable) {
            // Still in use by an open WebView: wipe the data instead, the empty profile is harmless.
            try {
                val profile = ProfileStore.getInstance().getOrCreateProfile(name)
                clearProfile(profile.cookieManager, profile.webStorage) {}
            } catch (_: Throwable) {
            }
        }
    }

    private fun clearDefault(onDone: () -> Unit) {
        clearProfile(CookieManager.getInstance(), WebStorage.getInstance(), onDone)
    }

    private fun clearProfile(cookies: CookieManager, storage: WebStorage, onDone: () -> Unit) {
        cookies.removeAllCookies { _ -> cookies.flush() }
        storage.deleteAllData()
        onDone()
    }
}
