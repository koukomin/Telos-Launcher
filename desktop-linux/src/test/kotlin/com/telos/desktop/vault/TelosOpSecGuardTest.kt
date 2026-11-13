package com.telos.desktop.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class TelosOpSecGuardTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun looksSensitiveDetectsPrivateKeyAndIban() {
        val guard = TelosOpSecGuard()
        assertTrue(guard.looksSensitive("-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----"))
        assertTrue(guard.looksSensitive("PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEE="))
        assertTrue(guard.looksSensitive("DE89370400440532013000"))
        assertFalse(guard.looksSensitive("hello world"))
        assertFalse(guard.looksSensitive("1234"))
    }

    @Test
    fun hostIntegritySnapshotIncludesPortableAndPlatform() {
        PortableTraceGuard.clearSelectedRoot()
        System.clearProperty("telos.portable.active")
        // Inject empty TPM tools so CI hosts without tpm2-tools stay deterministic.
        val noTpm = object : TpmAttestation.CommandRunner {
            override fun which(name: String): String? = null
            override fun run(vararg args: String, timeoutSec: Long) =
                com.telos.desktop.linux.Sys.Result(1, "unused")
        }
        val snap = TelosOpSecGuard(tpmRunner = noTpm).hostIntegritySnapshot()
        assertTrue(snap.containsKey("os"))
        assertTrue(snap.containsKey("compositor"))
        assertTrue(snap.containsKey("portable"))
        assertTrue(snap.containsKey("platform"))
        assertEquals(false.toString(), snap["portable"])
        assertEquals("unavailable", snap["tpm"])
    }

    @Test
    fun auditLogAppendsOwnerOnlyAndReadTail() {
        val home = tmp.newFolder("home")
        PortableEnvironmentIsolator.apply(home, fullSessionHome = false)
        try {
            TelosSessionAuditLog.append("unlock", "test")
            TelosSessionAuditLog.append("network_policy", "firewall_applied")
            val lines = TelosSessionAuditLog.readTail(10)
            assertTrue(lines.any { it.contains("unlock") })
            assertTrue(lines.any { it.contains("network_policy") })
            val file = TelosSessionAuditLog.auditFile()
            assertTrue(file.isFile)
            val perms = runCatching { Files.getPosixFilePermissions(file.toPath()) }.getOrNull()
            if (perms != null) {
                assertEquals(
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    perms,
                )
                // Windows File.canExecute() stays true for normal files; only assert on POSIX.
                assertFalse(file.canExecute())
            }
        } finally {
            PortableEnvironmentIsolator.clear()
        }
    }

    @Test
    fun profileSwitchWritesAuditEvent() {
        val home = tmp.newFolder("profile-home")
        PortableEnvironmentIsolator.apply(home, fullSessionHome = false)
        try {
            File(DesktopProfiles.profilesRoot(), "work").mkdirs()
            // Pass an explicit vault config dir so leftover PhantomVault state from other
            // tests cannot shadow the marker written under the portable isolator.
            val configDir = File(home, ".config/telos").apply { mkdirs() }
            assertTrue(DesktopProfiles.setActiveProfile("work", vaultConfigDir = configDir))
            assertEquals("work", DesktopProfiles.activeProfile(vaultConfigDir = configDir))
            assertTrue(TelosSessionAuditLog.readTail(5).any { it.contains("profile_switch") && it.contains("work") })
        } finally {
            PortableEnvironmentIsolator.clear()
        }
    }
}
