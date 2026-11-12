package com.telos.desktop.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class StegoCarrierManifestTest {
    @Test
    fun roundTripsJsonManifest() {
        val dir = File(System.getProperty("java.io.tmpdir"), "telos-manifest-${System.nanoTime()}")
        dir.mkdirs()
        try {
            val original = StegoCarrierManifest(
                spanName = "telos_stego_span",
                shards = listOf(
                    StegoCarrierShardEntry("a.mp3", 1000, 4096, 1_048_576),
                    StegoCarrierShardEntry("photos/b.jpg", 2000, 8192, 2_097_152),
                ),
            )
            val file = File(dir, StegoCarrierManifest.FILE_NAME)
            original.write(file)
            val parsed = StegoCarrierManifest.read(file)
            assertNotNull(parsed)
            assertEquals(original.version, parsed!!.version)
            assertEquals(original.spanName, parsed.spanName)
            assertEquals(original.shards, parsed.shards)
        } finally {
            dir.deleteRecursively()
        }
    }
}
