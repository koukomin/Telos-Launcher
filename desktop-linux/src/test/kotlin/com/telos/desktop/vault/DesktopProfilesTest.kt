package com.telos.desktop.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

class DesktopProfilesTest {
    @Test
    fun sanitizeRejectsBlankAndOddNames() {
        assertNull(DesktopProfiles.sanitizeProfileName("   "))
        assertNull(DesktopProfiles.sanitizeProfileName("../etc"))
        assertNull(DesktopProfiles.sanitizeProfileName(""))
        assertEquals("Work", DesktopProfiles.sanitizeProfileName("Work"))
        assertEquals("Personal-1", DesktopProfiles.sanitizeProfileName("Personal 1"))
    }

    @Test
    fun createAndSwitchWritesNameOnly() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "telos-profiles-test-${System.nanoTime()}")
        val vaultConfig = File(tmp, "vault-config").apply { mkdirs() }
        val previous = System.getProperty("user.home")
        System.setProperty("user.home", tmp.absolutePath)
        PortableEnvironmentIsolator.clear()
        try {
            val created = DesktopProfiles.createProfile("Work").getOrThrow()
            assertTrue(created.directory.isDirectory)
            assertTrue(File(created.directory, "profile.meta").isFile)
            assertTrue(DesktopProfiles.setActiveProfile("Work", vaultConfig))
            assertEquals("Work", DesktopProfiles.activeProfile(vaultConfig))
            val props = Properties()
            File(vaultConfig, "desktop.properties").inputStream().use { props.load(it) }
            assertEquals("Work", props.getProperty("profile.active"))
            assertNull(props.getProperty("password"))
            assertFalse(props.stringPropertyNames().any { it.contains("pass", ignoreCase = true) })
            val switched = DesktopProfiles.switchToProfile("Personal", vaultConfig).getOrThrow()
            assertEquals("Personal", switched.name)
            assertEquals("Personal", DesktopProfiles.activeProfile(vaultConfig))
            assertTrue(DesktopProfiles.listProfileFolders().any { it.name == "Work" })
            assertTrue(DesktopProfiles.listProfileFolders().any { it.name == "Personal" })
        } finally {
            PortableEnvironmentIsolator.clear()
            if (previous != null) System.setProperty("user.home", previous) else System.clearProperty("user.home")
            tmp.deleteRecursively()
        }
    }

    @Test
    fun setActiveMergesDesktopPropertiesWithoutSecrets() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "telos-profiles-merge-${System.nanoTime()}")
        val vaultConfig = File(tmp, "config").apply { mkdirs() }
        val propsFile = File(vaultConfig, "desktop.properties")
        propsFile.writeText("showDesktopIcons=true\npassword=should-be-stripped\n")
        val previous = System.getProperty("user.home")
        System.setProperty("user.home", tmp.absolutePath)
        PortableEnvironmentIsolator.clear()
        try {
            assertTrue(DesktopProfiles.setActiveProfile("Ghost", vaultConfig))
            val p = Properties()
            propsFile.inputStream().use { p.load(it) }
            assertEquals("Ghost", p.getProperty("profile.active"))
            assertEquals("true", p.getProperty("showDesktopIcons"))
            assertNull(p.getProperty("password"))
        } finally {
            PortableEnvironmentIsolator.clear()
            if (previous != null) System.setProperty("user.home", previous) else System.clearProperty("user.home")
            tmp.deleteRecursively()
        }
    }

    @Test
    fun portableRootRegistryRejectsHostHome() {
        val host = PortableTraceGuard.hostProfileHome()
        assertFalse(DesktopProfiles.registerPortableVaultRoot(host))
        val external = File(System.getProperty("java.io.tmpdir"), "telos-portable-reg-${System.nanoTime()}")
        try {
            assertTrue(DesktopProfiles.registerPortableVaultRoot(external))
            assertNotNull(
                DesktopProfiles.listPortableVaultRoots().find {
                    it.absolutePath == external.canonicalFile.absolutePath || it.absolutePath == external.absolutePath
                },
            )
        } finally {
            external.deleteRecursively()
        }
    }
}
