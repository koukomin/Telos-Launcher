package de.mm20.launcher2.store.transfer

import java.io.Reader
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObtainiumExportReadTest {
    @Test fun retainsUnicodeAndWhitespaceInAnOrdinaryExport() {
        val export = "{\"apps\":[{\"name\":\"Ελληνική εφαρμογή\"}]}\n"
        assertEquals(export, ObtainiumTransfer.readExport(StringReader(export)))
    }

    @Test fun stopsReadingAnOversizedProviderBeforeBufferingItsEntireFile() {
        val limit = 4 * 1024 * 1024
        val sourceSize = 166_283_744
        var consumed = 0
        val reader = object : Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                if (consumed >= sourceSize) return -1
                val count = minOf(length, sourceSize - consumed)
                buffer.fill('x', offset, offset + count)
                consumed += count
                return count
            }
            override fun close() = Unit
        }
        val failure = runCatching { ObtainiumTransfer.readExport(reader) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue("Read past the limit into the large source", consumed <= limit + 1)
    }
}
