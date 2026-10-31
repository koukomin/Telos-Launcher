package com.telos.desktop.linux

import java.io.RandomAccessFile

/**
 * Hot-path `/proc` reader. Reuses one [ByteArray] and one [RandomAccessFile]
 * so CPU/RAM polling does not allocate a new buffer or file handle each tick.
 */
class ProcByteReader(private val path: String, capacity: Int = 16 * 1024) {
    private val buf = ByteArray(capacity)
    private var raf: RandomAccessFile? = null
    var length: Int = 0
        private set

    fun refresh(): Boolean {
        return try {
            val f = raf ?: RandomAccessFile(path, "r").also { raf = it }
            f.seek(0)
            length = f.read(buf, 0, buf.size).coerceAtLeast(0)
            true
        } catch (_: Exception) {
            length = 0
            false
        }
    }

    fun forEachLine(block: (start: Int, end: Int) -> Unit) {
        var lineStart = 0
        var i = 0
        while (i < length) {
            if (buf[i] == NL) {
                block(lineStart, i)
                lineStart = i + 1
            }
            i++
        }
        if (lineStart < length) block(lineStart, length)
    }

    fun startsWith(start: Int, ascii: String): Boolean {
        if (start + ascii.length > length) return false
        for (k in ascii.indices) {
            if (buf[start + k].toInt() != ascii[k].code) return false
        }
        return true
    }

    fun skipToken(start: Int, end: Int): Int {
        var i = start
        while (i < end && buf[i] != SP && buf[i] != NL) i++
        while (i < end && buf[i] == SP) i++
        return i
    }

    fun parseLong(start: Int, end: Int): Pair<Long, Int> {
        var n = 0L
        var i = start
        while (i < end && (buf[i] == SP || buf[i] == COLON)) i++
        while (i < end) {
            val c = buf[i].toInt()
            if (c !in 48..57) break
            n = n * 10 + (c - 48)
            i++
        }
        while (i < end && buf[i] == SP) i++
        return n to i
    }

    fun indexOf(start: Int, end: Int, ch: Char): Int {
        val b = ch.code.toByte()
        var i = start
        while (i < end) {
            if (buf[i] == b) return i
            i++
        }
        return -1
    }

    fun asciiEquals(start: Int, end: Int, ascii: String): Boolean {
        if (end - start != ascii.length) return false
        for (k in ascii.indices) {
            if (buf[start + k].toInt() != ascii[k].code) return false
        }
        return true
    }

    private companion object {
        val NL = '\n'.code.toByte()
        val SP = ' '.code.toByte()
        val COLON = ':'.code.toByte()
    }
}
