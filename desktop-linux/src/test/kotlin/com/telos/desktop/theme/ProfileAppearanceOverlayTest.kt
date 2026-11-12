package com.telos.desktop.theme

import com.telos.desktop.network.DesktopNetworkController
import com.telos.desktop.settings.TelosDesktopSettings
import com.telos.desktop.vault.DesktopProfiles
import com.telos.desktop.vault.PortableEnvironmentIsolator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Properties

class ProfileAppearanceOverlayTest {
    private var previousHome: String? = null

    @Before
    fun isolateHome() {
        previousHome = System.getProperty("user.home")
        PortableEnvironmentIsolator.clear()
        DesktopNetworkController.clearConfigHomeCache()
    }

    @After
    fun restoreHome() {
        PortableEnvironmentIsolator.clear()
        DesktopNetworkController.clearConfigHomeCache()
        if (previousHome != null) System.setProperty("user.home", previousHome) else System.clearProperty("user.home")
    }

    @Test
    fun overlayWinsOverGlobalForAppearanceKeysOnly() {
        val global = Properties().apply {
            setProperty("theme.intensity", "quiet")
            setProperty("theme.accent", "magenta")
            setProperty("icons.theme", "hicolor")
            setProperty("cursor.theme", "Breeze")
            setProperty("showDesktopIcons", "true")
            setProperty("nightLight.enabled", "true")
        }
        val overlay = Properties().apply {
            setProperty("theme.intensity", "neon")
            setProperty("theme.accent", "cyan")
            setProperty("icons.theme", "Papirus-Dark")
            setProperty("cursor.theme", "Bibata-Modern-Ice")
            // Non-appearance keys in overlay must not leak into merge for those keys:
            setProperty("nightLight.enabled", "false")
            setProperty("showDesktopIcons", "false")
        }
        val merged = ProfileAppearanceOverlay.merge(global, overlay)
        assertEquals("neon", merged.getProperty("theme.intensity"))
        assertEquals("cyan", merged.getProperty("theme.accent"))
        assertEquals("Papirus-Dark", merged.getProperty("icons.theme"))
        assertEquals("Papirus-Dark", merged.getProperty("iconTheme"))
        assertEquals("Bibata-Modern-Ice", merged.getProperty("cursor.theme"))
        assertEquals("Bibata-Modern-Ice", merged.getProperty("cursorTheme"))
        // Non-overlay keys keep global values.
        assertEquals("true", merged.getProperty("showDesktopIcons"))
        assertEquals("true", merged.getProperty("nightLight.enabled"))
    }

    @Test
    fun missingOverlayKeysFallBackToGlobal() {
        val global = Properties().apply {
            setProperty("theme.intensity", "neon")
            setProperty("theme.accent", "orange")
            setProperty("icons.theme", "Tela")
        }
        val overlay = Properties().apply {
            setProperty("theme.accent", "green")
        }
        val merged = ProfileAppearanceOverlay.merge(global, overlay)
        assertEquals("neon", merged.getProperty("theme.intensity"))
        assertEquals("green", merged.getProperty("theme.accent"))
        assertEquals("Tela", merged.getProperty("icons.theme"))
    }

    @Test
    fun loadMergedUsesActiveProfileOverlay() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "telos-appear-overlay-${System.nanoTime()}")
        val vaultConfig = File(tmp, "vault-config").apply { mkdirs() }
        System.setProperty("user.home", tmp.absolutePath)
        DesktopNetworkController.clearConfigHomeCache()
        try {
            File(vaultConfig, "desktop.properties").writeText(
                """
                theme.intensity=quiet
                theme.accent=magenta
                icons.theme=hicolor
                cursor.theme=Breeze
                showDesktopIcons=true
                """.trimIndent() + "\n",
            )
            val work = DesktopProfiles.createProfile("WorkLook").getOrThrow()
            assertTrue(DesktopProfiles.setActiveProfile("WorkLook", vaultConfig))
            ProfileAppearanceOverlay.writeOverlayKeys(
                profileName = work.name,
                keys = mapOf(
                    AppearanceKeys.INTENSITY to "neon",
                    AppearanceKeys.ACCENT to "cyan",
                    AppearanceKeys.ICONS_THEME to "Papirus",
                    AppearanceKeys.CURSOR_THEME to "Bibata-Modern-Classic",
                ),
                profilesRoot = DesktopProfiles.profilesRoot(),
            )
            val merged = ProfileAppearanceOverlay.loadMerged(vaultConfig)
            assertEquals("neon", merged.getProperty("theme.intensity"))
            assertEquals("cyan", merged.getProperty("theme.accent"))
            assertEquals("Papirus", merged.getProperty("icons.theme"))
            assertEquals("Bibata-Modern-Classic", merged.getProperty("cursor.theme"))
            assertEquals("true", merged.getProperty("showDesktopIcons"))

            val settings = TelosDesktopSettings(vaultConfig)
            settings.load()
            assertEquals("neon", settings.themeIntensity)
            assertEquals("neon-cyan", settings.themeAccent)
            assertEquals("Papirus", settings.iconTheme)
            assertEquals("Bibata-Modern-Classic", settings.cursorTheme)

            // Switch profile without overlay → fall back to global.
            DesktopProfiles.switchToProfile("GhostLook", vaultConfig).getOrThrow()
            settings.reloadAppearanceFromProfile()
            assertEquals("quiet", settings.themeIntensity)
            assertEquals("neon-magenta", settings.themeAccent)
            assertEquals("hicolor", settings.iconTheme)
            assertEquals("Breeze", settings.cursorTheme)
        } finally {
            DesktopNetworkController.clearConfigHomeCache()
            tmp.deleteRecursively()
        }
    }

    @Test
    fun overlayWriteStaysUnderProfilesRootNotHostHome() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "telos-appear-portable-${System.nanoTime()}")
        // Capture the real JVM home *before* redirecting user.home — on Windows,
        // preferPortableConfig() routes profiles under PortableTraceGuard.dataRoot(),
        // not under the redirected user.home, which is the OpSec guarantee under test.
        val realHostHome = previousHome?.let(::File) ?: File(System.getProperty("user.home"))
        System.setProperty("user.home", tmp.absolutePath)
        DesktopNetworkController.clearConfigHomeCache()
        try {
            DesktopProfiles.createProfile("MuteGhost").getOrThrow()
            assertTrue(
                ProfileAppearanceOverlay.writeOverlayKeys(
                    "MuteGhost",
                    mapOf(AppearanceKeys.INTENSITY to "quiet", AppearanceKeys.ACCENT to "green"),
                ),
            )
            val overlay = ProfileAppearanceOverlay.overlayFile("MuteGhost")
            assertTrue(overlay.isFile)
            assertTrue(overlay.absolutePath.contains("${File.separator}profiles${File.separator}MuteGhost"))

            val overlayPath = overlay.canonicalFile.toPath().toAbsolutePath().normalize()
            val profilesRootPath =
                DesktopProfiles.profilesRoot().canonicalFile.toPath().toAbsolutePath().normalize()
            assertTrue(
                "overlay must stay under profilesRoot (portable session on Windows): " +
                    "overlay=$overlayPath profilesRoot=$profilesRootPath",
                overlayPath.startsWith(profilesRootPath),
            )

            val hostPath = realHostHome.canonicalFile.toPath().toAbsolutePath().normalize()
            val tmpPath = tmp.canonicalFile.toPath().toAbsolutePath().normalize()
            val portablePath = runCatching {
                com.telos.desktop.vault.PortableTraceGuard.dataRoot()
                    .canonicalFile.toPath().toAbsolutePath().normalize()
            }.getOrNull()
            val underFakeHome = overlayPath.startsWith(tmpPath)
            val underPortable = portablePath != null && overlayPath.startsWith(portablePath)
            val underRealHost = overlayPath.startsWith(hostPath)
            // Allowed: redirected test home (Linux) or portable data root (Windows).
            // Forbidden: spilling into the real host profile home outside those roots.
            assertTrue(
                "overlay must land under fake home or portable data root, not raw host home: " +
                    "overlay=$overlayPath host=$hostPath tmp=$tmpPath portable=$portablePath",
                underFakeHome || underPortable || !underRealHost,
            )
            if (underRealHost && !underFakeHome) {
                assertTrue(
                    "when overlay is under host home it must still be inside portable data",
                    underPortable,
                )
            }

            val p = Properties()
            overlay.inputStream().use { p.load(it) }
            assertEquals("quiet", p.getProperty("theme.intensity"))
            assertEquals("green", p.getProperty("theme.accent"))
            assertFalse(p.stringPropertyNames().any { it.contains("pass", ignoreCase = true) })
        } finally {
            DesktopNetworkController.clearConfigHomeCache()
            tmp.deleteRecursively()
        }
    }
}
