package com.telos.desktop.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PortableTraceGuardTest {
    @Test
    fun assertNoHostWritesRejectsHostHome() {
        val host = PortableTraceGuard.hostProfileHome()
        try {
            PortableTraceGuard.assertNoHostWrites(host)
            assertFalse("Expected rejection for host profile root", true)
        } catch (_: IllegalStateException) {
            assertTrue(true)
        }
    }

    @Test
    fun assertNoHostWritesRejectsHostConfig() {
        val config = File(PortableTraceGuard.hostProfileHome(), ".config")
        try {
            PortableTraceGuard.assertNoHostWrites(config)
            assertFalse("Expected rejection for .config", true)
        } catch (_: IllegalStateException) {
            assertTrue(true)
        }
    }

    @Test
    fun assertNoHostWritesAllowsExternalRoot() {
        val external = File(System.getProperty("java.io.tmpdir"), "telos-external-portable-${System.nanoTime()}")
        external.mkdirs()
        try {
            PortableTraceGuard.assertNoHostWrites(external)
        } finally {
            external.deleteRecursively()
        }
    }
}
