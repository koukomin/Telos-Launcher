package de.mm20.launcher2.comms.radio

import de.mm20.launcher2.comms.model.RadioStation
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RadioArchivesTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        } }
    }.toByteArray()

    @Test fun transistorCollectionRestoresSelectedStreamAndAbsoluteImagePath() {
        val image = byteArrayOf(1, 2, 3, 4)
        val json = """{"stations":[{"uuid":"a","name":"ΣΚΑΪ","streamUris":["https://radio.example/one","https://radio.example/two"],"stream":1,"image":"/data/user/0/org.y20k.transistor/files/station-images/a.png"}]}"""
        val imported = RadioArchives.read(ByteArrayInputStream(zip(
            "/collection.json" to json.toByteArray(), "/station-images/a.png" to image,
        ))).single()
        assertEquals("a", imported.station.id)
        assertEquals("ΣΚΑΪ", imported.station.name)
        assertEquals("https://radio.example/two", imported.station.streamUrl)
        assertEquals(listOf("https://radio.example/one"), imported.station.alternateStreams)
        assertArrayEquals(image, imported.image)
    }

    @Test fun backupRoundTripPreservesStationMetadataAndImage() {
        val station = RadioStation("../id", "Radio", "https://radio.example/live", "https://radio.example/image.png",
            homepage = "https://radio.example", streamContent = "audio/aac", nameManuallySet = true,
            alternateStreams = listOf("https://radio.example/backup"), lastPlayedAt = 42L)
        val image = byteArrayOf(8, 7, 6)
        val output = ByteArrayOutputStream()
        RadioArchives.write(listOf(station), output) { image }
        val result = RadioArchives.read(ByteArrayInputStream(output.toByteArray())).single()
        assertEquals(station, result.station)
        assertArrayEquals(image, result.image)
    }

    @Test fun oldTransistorM3uAndMatchingImageAreImported() {
        val bytes = zip("/Favorites/Station.m3u" to "#EXTM3U\n#EXTINF:-1,Station\nhttps://radio.example/live\n".toByteArray(),
            "/Favorites/Station.png" to byteArrayOf(1))
        val result = RadioArchives.read(ByteArrayInputStream(bytes)).single()
        assertEquals("Station", result.station.name)
        assertArrayEquals(byteArrayOf(1), result.image)
    }

    @Test fun traversalEntriesAreRejected() {
        listOf("../collection.json", "images/../../image.png", "images\\..\\image.png").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                RadioArchives.read(ByteArrayInputStream(zip(name to "{}".toByteArray())))
            }
        }
    }

    @Test fun oversizedImagesAndIgnoredEntriesAreRejectedBeforeExtraction() {
        listOf("image.png", "ignored.bin").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                RadioArchives.read(ByteArrayInputStream(zip(name to ByteArray(RadioArchives.MAX_IMAGE_BYTES + 1))))
            }
        }
    }

    @Test fun invalidStreamsDoNotBecomeStations() {
        val json = """{"stations":[{"streamUris":["file:///etc/passwd","https://user:pass@example.com/live"]}]}"""
        assertTrue(RadioArchives.parseJson(json).isEmpty())
    }
}
