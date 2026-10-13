package de.mm20.launcher2.comms.stremio

import java.util.Base64
import java.util.zip.InflaterInputStream

/** Account watch state, distinct from an unfinished playback position. */
data class StremioWatched(val movie: Boolean = false, val videos: String? = null) {
    /** Decodes the public Stremio watched-bitfield format against the metadata video order. */
    fun episodes(videoIds: List<String>): Set<String> = runCatching {
        val field = videos ?: return emptySet()
        val packed = field.substringAfterLast(':')
        val prefix = field.substringBeforeLast(':')
        val length = prefix.substringAfterLast(':').toInt()
        val anchor = videoIds.indexOf(prefix.substringBeforeLast(':'))
        if (anchor < 0 || length < 1 || length > 1_000_000) return emptySet()
        val bytes = InflaterInputStream(Base64.getDecoder().decode(packed).inputStream()).use {
            it.readBytesLimited(125_000)
        }
        val offset = length - 1 - anchor
        videoIds.filterIndexed { i, _ ->
            val bit = i + offset
            bit >= 0 && bit / 8 < bytes.size && (bytes[bit / 8].toInt() and (1 shl (bit % 8))) != 0
        }.toSet()
    }.getOrDefault(emptySet())
}

private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        check(out.size() + count <= limit)
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}
