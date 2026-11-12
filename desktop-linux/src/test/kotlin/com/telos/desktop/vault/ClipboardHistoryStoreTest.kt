package com.telos.desktop.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.atomic.AtomicLong

class ClipboardHistoryStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = AtomicLong(1_000_000L)
    private val guard = TelosOpSecGuard()

    private fun store(max: Int = 20, ttlMs: Long = 5 * 60 * 1000L) = ClipboardHistoryStore(
        maxItems = max,
        defaultTtlMs = ttlMs,
        looksSensitive = { guard.looksSensitive(it) },
        clockMs = { now.get() },
        vaultOnlyPersist = true,
    )

    @Test
    fun scrubRejectsIbanPrivateKeyAndWgKey() {
        val s = store()
        assertFalse(s.offer("-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----"))
        assertFalse(s.offer("PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEE="))
        assertFalse(s.offer("DE89370400440532013000"))
        assertTrue(s.offer("hello clipboard"))
        assertEquals(1, s.size())
        assertEquals("hello clipboard", s.snapshot().single().text)
    }

    @Test
    fun ttlEvictsUnpinnedItems() {
        val s = store(ttlMs = 5 * 60 * 1000L)
        assertTrue(s.offer("one"))
        assertTrue(s.offer("two"))
        assertEquals(2, s.size())
        now.addAndGet(5 * 60 * 1000L + 1)
        assertEquals(2, s.expire())
        assertEquals(0, s.size())
    }

    @Test
    fun pinUntilLockSurvivesTtlThenClearsOnLock() {
        val s = store(ttlMs = 60_000L)
        assertTrue(s.offer("keep-me"))
        val id = s.snapshot().single().id
        assertTrue(s.setPinned(id, true))
        now.addAndGet(120_000L)
        assertEquals(0, s.expire())
        assertEquals(1, s.size())
        assertTrue(s.snapshot().single().pinned)
        s.onLock()
        assertEquals(0, s.size())
    }

    @Test
    fun boundKeepsAtMostMaxItems() {
        val s = store(max = 20, ttlMs = 0L)
        repeat(30) { i ->
            now.addAndGet(1)
            assertTrue(s.offer("item-$i"))
        }
        assertEquals(20, s.size())
        assertEquals("item-29", s.snapshot().first().text)
        assertEquals("item-10", s.snapshot().last().text)
    }

    @Test
    fun vaultPersistIsOwnerOnlyAndSkipsSensitive() {
        val home = tmp.newFolder("vault-home")
        PortableEnvironmentIsolator.apply(home, fullSessionHome = false)
        try {
            val s = store(ttlMs = 0L)
            assertTrue(s.offer("safe-note"))
            assertFalse(s.offer("DE89370400440532013000"))
            val file = s.historyFile()
            assertTrue(file.isFile)
            val body = file.readText()
            assertTrue(body.contains("safe-note"))
            assertFalse(body.contains("DE89"))
            val perms = runCatching { Files.getPosixFilePermissions(file.toPath()) }.getOrNull()
            if (perms != null) {
                assertEquals(
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    perms,
                )
            }
            s.onLock()
            assertFalse(file.exists())
        } finally {
            PortableEnvironmentIsolator.clear()
        }
    }

    @Test
    fun noPersistOutsideVaultIsolator() {
        PortableEnvironmentIsolator.clear()
        val s = store()
        assertTrue(s.offer("ephemeral-only"))
        // Without isolator, history stays session memory — do not create a host-home file via offer.
        // auditDir may still resolve under user.home; assert the store refused persist by leaving no file
        // when canPersist is false (isolator inactive).
        val file = s.historyFile()
        // If a leftover file exists from other tests under real home, skip; under isolator-clear we
        // only assert that offer did not require the file to exist.
        assertEquals(1, s.size())
        if (file.exists()) {
            // Must not contain our ephemeral text written by this store instance when isolator is off.
            assertFalse(file.readText().contains("ephemeral-only"))
        }
    }
}
