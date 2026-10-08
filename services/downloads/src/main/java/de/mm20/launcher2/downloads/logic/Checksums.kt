package de.mm20.launcher2.downloads.logic

import java.io.InputStream
import java.security.MessageDigest

data class ExpectedChecksum(val algorithm: String, val hex: String)

object Checksums {
    /** "sha256:ab12..", "md5=..", or plain hex where the length tells the algorithm; null if it is none of these */
    fun parse(input: String?): ExpectedChecksum? {
        val t = input?.trim().orEmpty()
        if (t.isEmpty()) return null
        val sep = t.indexOfFirst { it == ':' || it == '=' }
        val (algoRaw, hexRaw) = if (sep > 0) t.substring(0, sep) to t.substring(sep + 1) else "" to t
        val hex = hexRaw.trim().lowercase()
        if (hex.isEmpty() || !hex.all { it in '0'..'9' || it in 'a'..'f' }) return null
        val byLength = when (hex.length) { 32 -> "MD5"; 40 -> "SHA-1"; 64 -> "SHA-256"; else -> null }
        val algo = when (algoRaw.lowercase().replace("-", "")) {
            "md5" -> "MD5"
            "sha1" -> "SHA-1"
            "sha256" -> "SHA-256"
            "" -> byLength
            else -> return null
        } ?: return null
        val expectedLength = when (algo) { "MD5" -> 32; "SHA-1" -> 40; else -> 64 }
        if (hex.length != expectedLength) return null
        return ExpectedChecksum(algo, hex)
    }

    fun compute(algorithm: String, input: InputStream, onProgress: (Long) -> Unit = {}): String {
        val md = MessageDigest.getInstance(algorithm)
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
            total += n
            onProgress(total)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
