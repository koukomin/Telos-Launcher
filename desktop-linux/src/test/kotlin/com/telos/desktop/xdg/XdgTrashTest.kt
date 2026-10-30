package com.telos.desktop.xdg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class XdgTrashTest {
    @Test
    fun moveAndRestore() {
        val home = Files.createTempDirectory("telos-trash-home").toFile()
        val doc = File(home, "Documents").apply { mkdirs() }
        val src = File(doc, "note.txt").apply { writeText("hi") }
        assertTrue(XdgTrash.moveToTrash(src, home))
        assertFalse(src.exists())
        val trashed = XdgTrash.filesDir(home).listFiles()!!.single()
        assertEquals("note.txt", trashed.name)
        assertTrue(XdgTrash.restore(trashed, home))
        assertTrue(src.exists())
        assertEquals("hi", src.readText())
    }
}
