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
        assertNull(DesktopProfiles.sanitizeProfileName("foo/bar"))
        assertNull(DesktopProfiles.sanitizeProfileName("foo\\bar"))
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
            val meta = Properties()
            File(created.directory, "profile.meta").inputStream().use { meta.load(it) }
            assertNotNull(meta.getProperty("host.id"))
            assertNotNull(meta.getProperty("host.label"))
            assertNull(meta.getProperty("password"))
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

    @Test
    fun lockGatedSwitchRefusesWithoutAuthWhenUnlocked() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "telos-profiles-gate-${System.nanoTime()}")
        val vaultConfig = File(tmp, "vault-config").apply { mkdirs() }
        val previous = System.getProperty("user.home")
        System.setProperty("user.home", tmp.absolutePath)
        PortableEnvironmentIsolator.clear()
        var locked = false
        try {
            DesktopProfiles.createProfile("Alpha").getOrThrow()
            DesktopProfiles.setActiveProfile("Alpha", vaultConfig)
            val denied = DesktopProfiles.switchToProfileLockGated(
                name = "Beta",
                vaultUnlocked = true,
                passwordAuth = null,
                lockVault = { locked = true },
                vaultConfigDir = vaultConfig,
            )
            assertTrue(denied is DesktopProfiles.SwitchOutcome.Failed)
            assertTrue((denied as DesktopProfiles.SwitchOutcome.Failed).error is DesktopProfiles.ProfileError.AuthRequired)
            assertFalse(locked)
            assertEquals("Alpha", DesktopProfiles.activeProfile(vaultConfig))

            val failedAuth = DesktopProfiles.switchToProfileLockGated(
                name = "Beta",
                vaultUnlocked = true,
                passwordAuth = { false },
                lockVault = { locked = true },
                vaultConfigDir = vaultConfig,
            )
            assertTrue(failedAuth is DesktopProfiles.SwitchOutcome.Failed)
            assertTrue((failedAuth as DesktopProfiles.SwitchOutcome.Failed).error is DesktopProfiles.ProfileError.AuthFailed)
            assertFalse(locked)
            assertEquals("Alpha", DesktopProfiles.activeProfile(vaultConfig))
        } finally {
            PortableEnvironmentIsolator.clear()
            if (previous != null) System.setProperty("user.home", previous) else System.clearProperty("user.home")
            tmp.deleteRecursively()
        }
    }

    @Test
    fun lockGatedSwitchLocksThenSwitchesWithoutRemountClaim() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "telos-profiles-gate-ok-${System.nanoTime()}")
        val vaultConfig = File(tmp, "vault-config").apply { mkdirs() }
        val previous = System.getProperty("user.home")
        System.setProperty("user.home", tmp.absolutePath)
        PortableEnvironmentIsolator.clear()
        var locked = false
        try {
            DesktopProfiles.createProfile("Alpha").getOrThrow()
            DesktopProfiles.setActiveProfile("Alpha", vaultConfig)
            val ok = DesktopProfiles.switchToProfileLockGated(
                name = "Beta",
                vaultUnlocked = true,
                passwordAuth = { true },
                lockVault = { locked = true },
                vaultConfigDir = vaultConfig,
            )
            assertTrue(ok is DesktopProfiles.SwitchOutcome.Switched)
            val switched = ok as DesktopProfiles.SwitchOutcome.Switched
            assertTrue(switched.lockedFirst)
            assertTrue(locked)
            assertEquals("Beta", switched.folder?.name)
            assertEquals("Beta", DesktopProfiles.activeProfile(vaultConfig))
        } finally {
            PortableEnvironmentIsolator.clear()
            if (previous != null) System.setProperty("user.home", previous) else System.clearProperty("user.home")
            tmp.deleteRecursively()
        }
    }

    @Test
    fun exportImportRoundtripExcludesSecretsAndRequiresConfirm() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "telos-profiles-export-${System.nanoTime()}")
        val vaultConfig = File(tmp, "vault-config").apply { mkdirs() }
        val previous = System.getProperty("user.home")
        System.setProperty("user.home", tmp.absolutePath)
        PortableEnvironmentIsolator.clear()
        try {
            val created = DesktopProfiles.createProfile("Travel").getOrThrow()
            File(created.directory, "notes.txt").writeText("hello portable")
            File(created.directory, "wireguard.privateKey").writeText("SECRET_KEY_MATERIAL")
            File(created.directory, "ok.properties").writeText("theme=dark\npassword=nope\n")
            DesktopProfiles.setActiveProfile("Travel", vaultConfig)

            val dest = File(tmp, "Travel.telos.profile.zip")
            val pass = "test-export-pass".toCharArray()
            val exported = PortableProfileExport.exportProfile("Travel", dest, pass, vaultConfig)
            assertTrue(exported.isSuccess)
            assertTrue(dest.isFile)

            val unconfirmed = PortableProfileExport.importProfile(dest, pass, confirmed = false)
            assertTrue(unconfirmed.isFailure)
            assertTrue(unconfirmed.exceptionOrNull() is PortableProfileExport.ExportError.ConfirmationRequired)

            // Remove original so import recreates from bundle.
            created.directory.deleteRecursively()
            assertFalse(DesktopProfiles.listProfileFolders().any { it.name == "Travel" })

            val imported = PortableProfileExport.importProfile(
                dest,
                pass,
                confirmed = true,
                vaultConfigDir = vaultConfig,
                activate = true,
            ).getOrThrow()
            assertEquals("Travel", imported.name)
            assertTrue(File(imported.directory, "notes.txt").isFile)
            assertEquals("hello portable", File(imported.directory, "notes.txt").readText())
            assertFalse(File(imported.directory, "wireguard.privateKey").exists())
            val props = Properties()
            File(imported.directory, "ok.properties").inputStream().use { props.load(it) }
            assertEquals("dark", props.getProperty("theme"))
            assertNull(props.getProperty("password"))
            assertEquals("Travel", DesktopProfiles.activeProfile(vaultConfig))
            assertTrue(PortableProfileExport.isExcludedPath("wg/privatekey"))
            assertTrue(PortableProfileExport.isExcludedPath("wireguard.privateKey"))
            assertFalse(PortableProfileExport.isExcludedPath("notes.txt"))
            pass.fill('\u0000')
        } finally {
            PortableEnvironmentIsolator.clear()
            if (previous != null) System.setProperty("user.home", previous) else System.clearProperty("user.home")
            tmp.deleteRecursively()
        }
    }

    @Test
    fun hostFingerprintIsStableAndSecretFree() {
        val a = DesktopProfiles.currentHostFingerprint()
        val b = DesktopProfiles.currentHostFingerprint()
        assertEquals(a.id, b.id)
        assertEquals(a.label, b.label)
        assertTrue(a.id.matches(Regex("^[a-f0-9]{32}$")))
        assertFalse(a.label.contains("password", ignoreCase = true))
        assertFalse(a.id.contains(" "))
    }
}
