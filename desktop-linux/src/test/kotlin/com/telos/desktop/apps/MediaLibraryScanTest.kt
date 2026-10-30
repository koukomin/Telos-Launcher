package com.telos.desktop.apps

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MediaLibraryScanTest {
    @Test
    fun picksAudioAndSkipsOther() {
        val root = Files.createTempDirectory("telos-media").toFile()
        File(root, "a.mp3").writeText("x")
        File(root, "b.txt").writeText("x")
        File(root, "sub").mkdirs()
        File(root, "sub/c.flac").writeText("x")
        val hits = scanLibrary(root, setOf("mp3", "flac"))
        assertEquals(setOf("a.mp3", "c.flac"), hits.map { it.name }.toSet())
    }
}
