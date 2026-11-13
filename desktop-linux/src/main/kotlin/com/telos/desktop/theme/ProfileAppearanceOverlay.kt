package com.telos.desktop.theme

import com.telos.desktop.vault.DesktopProfiles
import java.io.File
import java.util.Properties

/**
 * VOS-1 profile-scoped look: `profiles/<name>/desktop.properties` may override
 * [AppearanceKeys.PROFILE_OVERLAY_KEYS] without writing host HOME in Portable Data
 * (paths go through [DesktopProfiles.profilesRoot]).
 *
 * Precedence: global vault `desktop.properties`, then active profile overlay
 * (overlay wins for present keys). Missing overlay keys fall back to global.
 */
object ProfileAppearanceOverlay {
    const val OVERLAY_FILE = "desktop.properties"

    fun overlayFile(profileName: String, profilesRoot: File = DesktopProfiles.profilesRoot()): File {
        val safe = DesktopProfiles.sanitizeProfileName(profileName)
            ?: throw IllegalArgumentException("Invalid profile name: $profileName")
        return File(File(profilesRoot, safe), OVERLAY_FILE)
    }

    fun readOverlayProperties(
        profileName: String?,
        profilesRoot: File = DesktopProfiles.profilesRoot(),
    ): Properties {
        val out = Properties()
        if (profileName.isNullOrBlank()) return out
        val safe = DesktopProfiles.sanitizeProfileName(profileName) ?: return out
        val file = File(File(profilesRoot, safe), OVERLAY_FILE)
        if (!file.isFile) return out
        file.inputStream().use { out.load(it) }
        // Never retain secrets in an appearance overlay read.
        SECRET_PROPERTY_KEYS.forEach { out.remove(it) }
        return out
    }

    /**
     * Merge [global] with [overlay]: for each [AppearanceKeys.PROFILE_OVERLAY_KEYS]
     * entry present in overlay, replace the global value.
     */
    fun merge(global: Properties, overlay: Properties): Properties {
        val merged = Properties()
        merged.putAll(global)
        AppearanceKeys.PROFILE_OVERLAY_KEYS.forEach { key ->
            val value = overlay.getProperty(key)?.trim()
            if (!value.isNullOrEmpty()) {
                merged.setProperty(key, value)
                // Keep legacy alias aligned when icons.theme is overridden.
                if (key == AppearanceKeys.ICONS_THEME) {
                    merged.setProperty("iconTheme", value)
                }
                if (key == AppearanceKeys.CURSOR_THEME) {
                    merged.setProperty("cursorTheme", value)
                }
            }
        }
        return merged
    }

    /**
     * Load vault global props, then apply the active profile overlay (if any).
     */
    fun loadMerged(
        vaultConfigDir: File,
        activeProfile: String? = DesktopProfiles.activeProfile(vaultConfigDir),
        profilesRoot: File = DesktopProfiles.profilesRoot(),
    ): Properties {
        val global = Properties()
        val globalFile = File(vaultConfigDir, OVERLAY_FILE)
        if (globalFile.isFile) {
            globalFile.inputStream().use { global.load(it) }
        }
        val overlay = readOverlayProperties(activeProfile, profilesRoot)
        return merge(global, overlay)
    }

    /**
     * Write only appearance overlay keys into `profiles/<name>/desktop.properties`.
     * Does not touch host HOME; uses [profilesRoot] (portable-aware).
     * Leaves unrelated overlay keys intact; strips secret keys.
     */
    fun writeOverlayKeys(
        profileName: String,
        keys: Map<String, String>,
        profilesRoot: File = DesktopProfiles.profilesRoot(),
    ): Boolean {
        val safe = DesktopProfiles.sanitizeProfileName(profileName) ?: return false
        val dir = File(profilesRoot, safe).apply { mkdirs() }
        val file = File(dir, OVERLAY_FILE)
        val p = Properties()
        if (file.isFile) {
            file.inputStream().use { p.load(it) }
        }
        SECRET_PROPERTY_KEYS.forEach { p.remove(it) }
        keys.forEach { (key, value) ->
            if (key in AppearanceKeys.PROFILE_OVERLAY_KEYS) {
                val trimmed = value.trim()
                if (trimmed.isEmpty()) p.remove(key) else p.setProperty(key, trimmed)
            }
        }
        file.outputStream().use { p.store(it, "Telos profile appearance overlay") }
        tightenOwnerOnly(file)
        return true
    }

    /**
     * Apply appearance fields from a merged [Properties] into a settings holder via callbacks.
     * Keeps this module free of a hard dependency on mutable settings internals for tests.
     */
    fun applyAppearanceFields(
        props: Properties,
        setIntensity: (String) -> Unit,
        setAccent: (String) -> Unit,
        setIconTheme: (String) -> Unit,
        setCursorTheme: (String) -> Unit,
    ) {
        props.getProperty(AppearanceKeys.INTENSITY)?.let {
            setIntensity(ThemeIntensity.parse(it).propertyValue)
        }
        props.getProperty(AppearanceKeys.ACCENT)?.let {
            setAccent(ThemeAccent.parse(it).propertyValue)
        }
        val icons = props.getProperty(AppearanceKeys.ICONS_THEME)
            ?: props.getProperty("iconTheme")
        if (!icons.isNullOrBlank()) setIconTheme(icons.trim())
        val cursor = props.getProperty(AppearanceKeys.CURSOR_THEME)
            ?: props.getProperty("cursorTheme")
        if (cursor != null) setCursorTheme(cursor.trim())
    }

    private fun tightenOwnerOnly(file: File) {
        runCatching {
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
        }
    }

    private val SECRET_PROPERTY_KEYS = setOf(
        "password",
        "passwd",
        "secret",
        "token",
        "apiKey",
        "api_key",
        "wireguard.privateKey",
        "wg.privateKey",
        "sync.passphrase",
        "keepass.password",
    )
}
