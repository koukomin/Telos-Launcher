package com.telos.desktop.theme

import com.telos.desktop.network.DesktopNetworkController
import com.telos.desktop.settings.TelosDesktopSettings
import com.telos.desktop.vault.PortableEnvironmentIsolator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Properties

class PanicMuteControllerTest {
    private var previousHome: String? = null

    @Before
    fun resetSession() {
        previousHome = System.getProperty("user.home")
        PortableEnvironmentIsolator.clear()
        DesktopNetworkController.clearConfigHomeCache()
        PanicMuteController.setMuted(false, settings = null, persist = false)
    }

    @After
    fun restore() {
        PanicMuteController.setMuted(false, settings = null, persist = false)
        PortableEnvironmentIsolator.clear()
        DesktopNetworkController.clearConfigHomeCache()
        if (previousHome != null) System.setProperty("user.home", previousHome) else System.clearProperty("user.home")
    }

    @Test
    fun sessionFlagForcesQuietAndHidesNeonChrome() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "telos-panic-mute-${System.nanoTime()}")
        System.setProperty("user.home", tmp.absolutePath)
        DesktopNetworkController.clearConfigHomeCache()
        val config = File(tmp, "config").apply { mkdirs() }
        try {
            val settings = TelosDesktopSettings(config)
            settings.themeIntensity = ThemeIntensity.VaultNeon.propertyValue
            settings.themeAccent = ThemeAccent.NeonMagenta.propertyValue
            settings.panicMute = false
            assertEquals(ThemeIntensity.VaultNeon, PanicMuteController.effectiveIntensity(settings))
            assertTrue(PanicMuteController.showTechBorders(settings))
            assertTrue(PanicMuteController.showGauges(settings))

            PanicMuteController.setMuted(true, settings, persist = true)
            assertTrue(PanicMuteController.isMuted(settings))
            assertEquals(ThemeIntensity.QuietOps, PanicMuteController.effectiveIntensity(settings))
            assertFalse(PanicMuteController.showTechBorders(settings))
            assertFalse(PanicMuteController.showGauges(settings))

            val props = Properties()
            File(config, "desktop.properties").inputStream().use { props.load(it) }
            assertEquals("true", props.getProperty(AppearanceKeys.PANIC_MUTE))
            // Mute must not invent network / firewall keys.
            assertFalse(props.stringPropertyNames().any { it.contains("nft", ignoreCase = true) })
            assertFalse(props.stringPropertyNames().any { it.contains("kill", ignoreCase = true) })
            assertFalse(props.stringPropertyNames().any { it.contains("firewall", ignoreCase = true) })
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun persistedMuteRestoresOnLoadWithoutTouchingIntensityProperty() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "telos-panic-persist-${System.nanoTime()}")
        System.setProperty("user.home", tmp.absolutePath)
        DesktopNetworkController.clearConfigHomeCache()
        val config = File(tmp, "config").apply { mkdirs() }
        try {
            File(config, "desktop.properties").writeText(
                """
                theme.intensity=neon
                theme.accent=orange
                theme.panicMute=true
                """.trimIndent() + "\n",
            )
            val settings = TelosDesktopSettings(config)
            settings.load()
            assertTrue(settings.panicMute)
            assertTrue(PanicMuteController.isMuted(settings))
            assertEquals("neon", settings.themeIntensity) // stored intensity unchanged
            assertEquals(ThemeIntensity.QuietOps, PanicMuteController.effectiveIntensity(settings))
            assertFalse(PanicMuteController.showGauges(settings))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun toggleRoundTrip() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "telos-panic-toggle-${System.nanoTime()}")
        System.setProperty("user.home", tmp.absolutePath)
        DesktopNetworkController.clearConfigHomeCache()
        val config = File(tmp, "config").apply { mkdirs() }
        try {
            val settings = TelosDesktopSettings(config)
            assertFalse(PanicMuteController.isMuted(settings))
            assertTrue(PanicMuteController.toggle(settings, persist = true))
            assertTrue(PanicMuteController.isMuted(settings))
            assertFalse(PanicMuteController.toggle(settings, persist = true))
            assertFalse(PanicMuteController.isMuted(settings))
            val props = Properties()
            File(config, "desktop.properties").inputStream().use { props.load(it) }
            assertEquals("false", props.getProperty(AppearanceKeys.PANIC_MUTE))
        } finally {
            tmp.deleteRecursively()
        }
    }
}
