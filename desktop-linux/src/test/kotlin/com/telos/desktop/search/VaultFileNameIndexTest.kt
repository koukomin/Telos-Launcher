package com.telos.desktop.search

import com.telos.desktop.vault.PortableEnvironmentIsolator
import com.telos.desktop.vault.PortableTraceGuard
import com.telos.desktop.xdg.XdgUserDirs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VaultFileNameIndexTest {
    private lateinit var tmp: File
    private var previousPortable: String? = null
    private var previousBoot: String? = null
    private var previousHome: String? = null

    @Before
    fun setUp() {
        tmp = Files.createTempDirectory("telos-file-index-").toFile()
        previousPortable = System.getProperty("telos.portable.data")
        previousBoot = System.getProperty("telos.boot.mode")
        previousHome = System.getProperty("user.home")
        PortableTraceGuard.clearSelectedRoot()
        PortableEnvironmentIsolator.clear()
        VaultFileNameIndex.enabled = false
        VaultFileNameIndex.clearMemory()
        System.clearProperty("telos.boot.mode")
    }

    @After
    fun tearDown() {
        VaultFileNameIndex.enabled = false
        VaultFileNameIndex.clearMemory()
        PortableEnvironmentIsolator.clear()
        PortableTraceGuard.clearSelectedRoot()
        if (previousPortable == null) System.clearProperty("telos.portable.data")
        else System.setProperty("telos.portable.data", previousPortable!!)
        if (previousBoot == null) System.clearProperty("telos.boot.mode")
        else System.setProperty("telos.boot.mode", previousBoot!!)
        if (previousHome == null) System.clearProperty("user.home")
        else System.setProperty("user.home", previousHome!!)
        tmp.deleteRecursively()
    }

    @Test
    fun rootFilteringRejectsHostProfileUnlessHostHome() {
        val host = File(tmp, "host-home").apply { mkdirs() }
        val vault = File(tmp, "vault-home").apply { mkdirs() }
        val portable = File(tmp, "TelosPortableData").apply { mkdirs() }
        File(host, "Documents").mkdirs()
        File(vault, "Documents").mkdirs()
        File(portable, "Documents").mkdirs()

        System.setProperty("user.home", vault.absolutePath)
        System.setProperty("telos.portable.data", portable.absolutePath)
        PortableTraceGuard.selectDataRoot(portable)
        System.setProperty("telos.portable.active", "1")

        val xdg = XdgUserDirs(
            desktop = File(vault, "Desktop"),
            download = File(vault, "Downloads"),
            documents = File(vault, "Documents"),
            music = File(vault, "Music"),
            pictures = File(vault, "Pictures"),
            videos = File(vault, "Videos"),
            home = vault,
            trash = File(vault, ".local/share/Trash/files"),
        )
        File(host, "Documents").mkdirs()

        val withoutHost = VaultFileNameIndex.allowedRoots(
            sessionHome = vault,
            hostHome = host,
            portableRoot = portable,
            hostHomeMode = false,
            xdg = xdg,
        )
        assertTrue(withoutHost.any { it.canonicalFile == vault.canonicalFile || it.canonicalFile == File(vault, "Documents").canonicalFile })
        assertFalse(withoutHost.any { it.canonicalFile == host.canonicalFile })
        assertFalse(withoutHost.any { it.canonicalFile == File(host, "Documents").canonicalFile })

        val hostMode = VaultFileNameIndex.allowedBases(
            sessionHome = vault,
            hostHome = host,
            portableRoot = null,
            hostHomeMode = true,
        )
        assertTrue(hostMode.any { it.canonicalFile == host.canonicalFile })
    }

    @Test
    fun privacyFirstEmptyBasesWhenNoVaultPortableOrHostHome() {
        val host = File(tmp, "lonely-host").apply { mkdirs() }
        val bases = VaultFileNameIndex.allowedBases(
            sessionHome = host,
            hostHome = host,
            portableRoot = null,
            hostHomeMode = false,
        )
        assertTrue(bases.isEmpty())
    }

    @Test
    fun queryMatchesFileNameAndExtension() {
        val vault = File(tmp, "vault-q").apply { mkdirs() }
        val docs = File(vault, "Documents").apply { mkdirs() }
        val report = File(docs, "QuarterlyReport.pdf").apply { writeText("secret-body-not-indexed") }
        File(docs, "notes.txt").writeText("hi")
        File(docs, "photo.JPEG").writeText("x")

        val entries = VaultFileNameIndex.scanRoots(listOf(docs), maxEntries = 100, maxDepth = 4)
        assertTrue(entries.any { it.fileName == "QuarterlyReport.pdf" })

        val byName = VaultFileNameIndex.query("quarter", entries = entries)
        assertTrue(byName.any { it.absolutePath == report.absolutePath })

        val byExt = VaultFileNameIndex.query("pdf", entries = entries)
        assertTrue(byExt.any { it.extension.equals("pdf", ignoreCase = true) })
        assertFalse(byExt.any { it.fileName == "notes.txt" })

        val jpeg = VaultFileNameIndex.query(".jpeg", entries = entries)
        assertTrue(jpeg.any { it.fileName.equals("photo.JPEG", ignoreCase = true) })
    }

    @Test
    fun persistEncryptedUnderCacheWithOwnerPermsAndNoContent() {
        val vault = File(tmp, "vault-store").apply { mkdirs() }
        val docs = File(vault, "Documents").apply { mkdirs() }
        File(docs, "budget.xlsx").writeText("SHOULD_NOT_APPEAR_IN_BLOB")

        System.setProperty("user.home", vault.absolutePath)
        PortableEnvironmentIsolator.vaultHome = vault
        PortableEnvironmentIsolator.active = true

        VaultFileNameIndex.enabled = true
        val n = VaultFileNameIndex.maybeRescan(
            force = true,
            minIntervalMs = 0,
            sessionHome = vault,
            roots = listOf(docs),
        )
        assertTrue(n >= 1)

        val dir = VaultFileNameIndex.indexDir(vault)
        assertEquals(File(vault, ".cache/telos/file-index").canonicalFile, dir.canonicalFile)
        val blob = File(dir, VaultFileNameIndex.BLOB_NAME)
        assertTrue(blob.isFile)
        val raw = blob.readBytes()
        val asText = raw.toString(Charsets.ISO_8859_1)
        assertFalse(asText.contains("SHOULD_NOT_APPEAR_IN_BLOB"))
        assertFalse(String(raw).contains("budget.xlsx")) // ciphertext, not plaintext names

        runCatching {
            val perms = Files.getPosixFilePermissions(blob.toPath())
            assertFalse(perms.contains(java.nio.file.attribute.PosixFilePermission.GROUP_READ))
            assertFalse(perms.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_READ))
            assertTrue(perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_READ))
        }

        VaultFileNameIndex.clearMemory()
        val loaded = VaultFileNameIndex.loadFromDisk(vault)
        assertTrue(loaded.any { it.fileName == "budget.xlsx" })
    }

    @Test
    fun disabledByDefaultSkipsRescan() {
        assertFalse(VaultFileNameIndex.enabled)
        val vault = File(tmp, "vault-off").apply { mkdirs() }
        File(vault, "a.txt").writeText("x")
        val n = VaultFileNameIndex.maybeRescan(force = true, sessionHome = vault, roots = listOf(vault))
        assertEquals(0, n)
        assertTrue(VaultFileNameIndex.snapshot().isEmpty())
    }

    @Test
    fun rescanThrottledUnlessForced() {
        val vault = File(tmp, "vault-throttle").apply { mkdirs() }
        File(vault, "one.txt").writeText("1")
        PortableEnvironmentIsolator.vaultHome = vault
        PortableEnvironmentIsolator.active = true
        VaultFileNameIndex.enabled = true
        val first = VaultFileNameIndex.maybeRescan(
            force = true,
            minIntervalMs = 60_000,
            sessionHome = vault,
            roots = listOf(vault),
            nowMs = 1_000L,
        )
        assertTrue(first >= 1)
        File(vault, "two.txt").writeText("2")
        val throttled = VaultFileNameIndex.maybeRescan(
            force = false,
            minIntervalMs = 60_000,
            sessionHome = vault,
            roots = listOf(vault),
            nowMs = 1_500L,
        )
        assertEquals(first, throttled)
        assertFalse(VaultFileNameIndex.snapshot().any { it.fileName == "two.txt" })
        val forced = VaultFileNameIndex.maybeRescan(
            force = true,
            minIntervalMs = 60_000,
            sessionHome = vault,
            roots = listOf(vault),
            nowMs = 1_600L,
        )
        assertTrue(forced >= 2)
        assertTrue(VaultFileNameIndex.snapshot().any { it.fileName == "two.txt" })
    }

    @Test
    fun desktopSearchUsesIndexWhenEnabled() {
        val vault = File(tmp, "vault-search").apply { mkdirs() }
        val docs = File(vault, "Documents").apply { mkdirs() }
        File(docs, "deep").mkdirs()
        File(docs, "deep/secret-plan.md").writeText("body")
        PortableEnvironmentIsolator.vaultHome = vault
        PortableEnvironmentIsolator.active = true
        System.setProperty("user.home", vault.absolutePath)

        VaultFileNameIndex.enabled = true
        VaultFileNameIndex.maybeRescan(force = true, sessionHome = vault, roots = listOf(docs))

        val hits = DesktopSearch.search(
            "secret-plan",
            apps = emptyList(),
            filesRoot = listOf(docs),
            telos = emptyList(),
        )
        assertTrue(hits.any { it.kind == SearchKind.File && it.title == "secret-plan.md" })
    }
}
