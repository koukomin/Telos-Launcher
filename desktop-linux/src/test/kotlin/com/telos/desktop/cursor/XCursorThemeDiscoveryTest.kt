package com.telos.desktop.cursor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class XCursorThemeDiscoveryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun discoversCursorThemeWithIndexAndCursorsDir() {
        val theme = tmp.newFolder("Bibata-Modern-Classic")
        File(theme, "cursors").mkdirs()
        File(theme, "cursors/left_ptr").writeBytes(byteArrayOf(1, 2, 3))
        File(theme, "index.theme").writeText(
            """
            [Icon Theme]
            Name=Bibata Modern Classic
            Comment=Test
            Inherits=breeze_cursors

            [Cursor Theme]
            Name=Bibata Modern Classic
            """.trimIndent(),
        )
        val info = XCursorThemeDiscovery.parseTheme(theme)
        assertNotNull(info)
        assertEquals("Bibata-Modern-Classic", info!!.name)
        assertEquals("Bibata Modern Classic", info.displayName)
        assertTrue(info.hasCursorsDir)
        assertEquals(listOf("breeze_cursors"), info.inherits)
    }

    @Test
    fun ignoresIconOnlyThemeWithoutCursors() {
        val theme = tmp.newFolder("Papirus")
        File(theme, "48x48/apps").mkdirs()
        File(theme, "index.theme").writeText(
            """
            [Icon Theme]
            Name=Papirus
            Directories=48x48/apps
            """.trimIndent(),
        )
        assertNull(XCursorThemeDiscovery.parseTheme(theme))
    }

    @Test
    fun nearestKdeSizes() {
        assertEquals(16, KdeCursorConfig.nearestKdeSize(14))
        assertEquals(24, KdeCursorConfig.nearestKdeSize(24))
        assertEquals(32, KdeCursorConfig.nearestKdeSize(30))
        assertEquals(48, KdeCursorConfig.nearestKdeSize(64))
    }

    @Test
    fun readsXcursorFrame() {
        val file = tmp.newFile("left_ptr")
        writeMinimalXcursor(file, size = 24)
        val frame = XCursorReader.loadBestFrame(file, 24)
        assertNotNull(frame)
        assertEquals(24, frame!!.width)
        assertEquals(24, frame.height)
        assertEquals(4, frame.xHot)
        assertEquals(4, frame.yHot)
    }

    @Test
    fun settingsRoundTripCursorKeys() {
        val dir = tmp.newFolder("config")
        val settings = com.telos.desktop.settings.TelosDesktopSettings(dir)
        settings.cursorTheme = "BreezeX-Dark"
        settings.cursorSize = 32
        settings.save()
        val loaded = com.telos.desktop.settings.TelosDesktopSettings(dir)
        loaded.load()
        assertEquals("BreezeX-Dark", loaded.cursorTheme)
        assertEquals(32, loaded.cursorSize)
        val props = File(dir, "desktop.properties").readText()
        assertTrue(props.contains("cursor.theme=BreezeX-Dark"))
        assertTrue(props.contains("cursor.size=32"))
    }

    @Test
    fun applierInjectsXcursorEnv() {
        CursorThemeApplier.apply(
            themeName = "Bibata-Modern-Classic",
            size = 24,
            followSystemTheme = false,
        )
        val pb = ProcessBuilder("true")
        CursorThemeApplier.inject(pb)
        assertEquals("Bibata-Modern-Classic", pb.environment()["XCURSOR_THEME"])
        assertEquals("24", pb.environment()["XCURSOR_SIZE"])
    }

    private fun writeMinimalXcursor(file: File, size: Int) {
        // header 16 + one toc 12 + image chunk header 36 + pixels
        val pixels = size * size
        val imagePos = 16 + 12
        val buf = ByteBuffer.allocate(imagePos + 36 + pixels * 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0x72756358) // magic
        buf.putInt(16) // header bytes
        buf.putInt(0x10000) // version
        buf.putInt(1) // ntoc
        buf.putInt(0xfffd0002.toInt()) // type IMAGE
        buf.putInt(size) // subtype = size
        buf.putInt(imagePos)
        buf.putInt(0xfffd0002.toInt())
        buf.putInt(36) // chunk size field
        buf.putInt(1) // version
        buf.putInt(size)
        buf.putInt(size)
        buf.putInt(4) // xhot
        buf.putInt(4) // yhot
        buf.putInt(50) // delay
        repeat(pixels) { buf.putInt(0xFF00F0FF.toInt()) }
        file.writeBytes(buf.array())
    }
}
