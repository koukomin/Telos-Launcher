package com.telos.desktop.icons

import com.telos.desktop.vault.PortableTraceGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TelosIconPackTest {
    @Test
    fun catalogHasGplCompatiblePacks() {
        assertTrue(TelosIconPackCatalog.entries.size >= 3)
        TelosIconPackCatalog.entries.forEach { e ->
            assertTrue(e.license.startsWith("GPL"))
            assertTrue(e.archiveUrl.startsWith("https://"))
            assertTrue(e.homepageUrl.startsWith("https://"))
        }
        assertEquals("papirus", TelosIconPackCatalog.byId("papirus")?.id)
    }

    @Test
    fun portableIconsRootStaysUnderPortableData() {
        val previous = System.getProperty("telos.portable.data")
        val root = File.createTempFile("telos-icons-portable", "").also {
            it.delete()
            it.mkdirs()
        }
        try {
            System.setProperty("telos.portable.data", root.absolutePath)
            PortableTraceGuard.selectDataRoot(root)
            System.setProperty("telos.portable.active", "1")
            val expected = root.canonicalFile
            val icons = TelosIconPackPaths.iconsRoot().canonicalFile
            // Windows may rewrite 8.3 vs long paths; compare canonical forms.
            assertEquals(File(expected, "icons").canonicalFile, icons)
            assertEquals("icons", icons.name)
            PortableTraceGuard.assertNoHostWrites(icons.parentFile ?: icons)
        } finally {
            if (previous == null) System.clearProperty("telos.portable.data")
            else System.setProperty("telos.portable.data", previous)
            System.clearProperty("telos.portable.active")
            PortableTraceGuard.clearSelectedRoot()
            root.deleteRecursively()
        }
    }

    @Test
    fun installerFindsIconThemeAndSkipsCursorOnly() {
        val staging = File.createTempFile("telos-icon-stage", "").also {
            it.delete()
            it.mkdirs()
        }
        try {
            val theme = File(staging, "FakePapirus").apply { mkdirs() }
            File(theme, "index.theme").writeText(
                """
                [Icon Theme]
                Name=FakePapirus
                Comment=Test
                Inherits=hicolor
                Directories=48x48/apps
                [48x48/apps]
                Size=48
                Context=Applications
                Type=Fixed
                """.trimIndent(),
            )
            File(theme, "48x48/apps").mkdirs()
            File(theme, "48x48/apps/test-app.svg").writeText("<svg/>")

            val cursor = File(staging, "Sweet-cursors").apply { mkdirs() }
            File(cursor, "index.theme").writeText(
                """
                [Cursor Theme]
                Name=Sweet
                """.trimIndent(),
            )

            val found = TelosIconPackInstaller.findIconThemeDirs(staging)
            assertEquals(listOf("FakePapirus"), found.map { it.name })
        } finally {
            staging.deleteRecursively()
        }
    }

    @Test
    fun installFromZipIntoManagedRoot() {
        val previous = System.getProperty("telos.portable.data")
        val root = File.createTempFile("telos-icons-install", "").also {
            it.delete()
            it.mkdirs()
        }
        try {
            System.setProperty("telos.portable.data", root.absolutePath)
            PortableTraceGuard.selectDataRoot(root)
            System.setProperty("telos.portable.active", "1")

            val zip = File(root, "pack.zip")
            ZipOutputStream(zip.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("DemoTheme/index.theme"))
                zos.write(
                    """
                    [Icon Theme]
                    Name=DemoTheme
                    Inherits=hicolor
                    Directories=scalable/apps
                    [scalable/apps]
                    Size=48
                    Type=Scalable
                    MinSize=1
                    MaxSize=256
                    """.trimIndent().toByteArray(),
                )
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("DemoTheme/scalable/apps/folder.svg"))
                zos.write("<svg xmlns='http://www.w3.org/2000/svg'/>".toByteArray())
                zos.closeEntry()
            }

            val staging = File(TelosIconPackPaths.stagingDir(), "demo").also { it.mkdirs() }
            assertTrue(com.telos.desktop.files.ZipArchives.extractAll(zip, staging))
            val themes = TelosIconPackInstaller.findIconThemeDirs(staging)
            assertEquals(1, themes.size)
            val dest = File(TelosIconPackPaths.iconsRoot(), themes[0].name)
            themes[0].copyRecursively(dest, overwrite = true)

            val resolver = LinuxIconThemeResolver.withManagedRoots("DemoTheme")
            resolver.invalidate()
            val listed = resolver.listInstalledThemes()
            assertTrue(listed.any { it.name == "DemoTheme" && it.source == IconThemeSource.TelosDownloaded })
            assertTrue(listed.any { it.name == TelosIconPackPaths.BUILTIN_CANDY && it.source == IconThemeSource.BuiltIn })
            assertTrue(resolver.resolve("folder", 48)?.isFile == true)
        } finally {
            if (previous == null) System.clearProperty("telos.portable.data")
            else System.setProperty("telos.portable.data", previous)
            System.clearProperty("telos.portable.active")
            PortableTraceGuard.clearSelectedRoot()
            root.deleteRecursively()
        }
    }

    @Test
    fun resolverSkipsCursorThemeIndexes() {
        val root = File.createTempFile("telos-icon-scan", "").also {
            it.delete()
            it.mkdirs()
        }
        try {
            val cursor = File(root, "OnlyCursor").apply { mkdirs() }
            File(cursor, "index.theme").writeText("[Cursor Theme]\nName=OnlyCursor\n")
            val icon = File(root, "RealIcons").apply { mkdirs() }
            File(icon, "index.theme").writeText(
                "[Icon Theme]\nName=RealIcons\nInherits=hicolor\nDirectories=\n",
            )
            val resolver = LinuxIconThemeResolver(preferredTheme = "RealIcons", extraRoots = listOf(root))
            val names = resolver.listInstalledThemes().map { it.name }
            assertTrue("RealIcons" in names)
            assertFalse("OnlyCursor" in names)
            assertTrue(TelosIconPackPaths.BUILTIN_CANDY in names)
        } finally {
            root.deleteRecursively()
        }
    }
}
