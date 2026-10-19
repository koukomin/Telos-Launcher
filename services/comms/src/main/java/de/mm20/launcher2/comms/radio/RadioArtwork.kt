package de.mm20.launcher2.comms.radio

import de.mm20.launcher2.comms.media.video.Http
import java.io.ByteArrayOutputStream

/** Bounded artwork download API; HTTP redirect internals stay inside the comms module. */
object RadioArtwork {
    fun download(address: String): ByteArray? {
        if (!Http.isPublicWeb(address)) return null
        val first = Http.open(address, "Telos Radio", "image/*")
        first.connectTimeout = 6000
        first.readTimeout = 8000
        val connection = try { Http.resolve(first) } catch (e: Exception) { first.disconnect(); throw e }
        try {
            if (connection.responseCode !in 200..299 || connection.contentLengthLong > RadioArchives.MAX_IMAGE_BYTES) return null
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= RadioArchives.MAX_IMAGE_BYTES)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }
}
