package de.mm20.launcher2.base

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

/**
 * High-Performance Compact ID Generator for Telos Launcher.
 * Generates compact 64-bit Base62 IDs for downloads, widgets, and items
 * with zero string allocation overhead compared to standard 36-character UUIDs.
 */
object TelosIdGenerator {

    private val counter = AtomicLong(System.currentTimeMillis())
    private val random = SecureRandom()
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    /**
     * Generates a compact unique Base62 String ID (e.g. "k9Xm2L8p").
     */
    fun nextCompactId(): String {
        var value = (counter.incrementAndGet() shl 16) or (random.nextInt(0xFFFF).toLong() and 0xFFFFL)
        if (value < 0) value = -value

        val sb = StringBuilder(11)
        while (value > 0) {
            sb.append(ALPHABET[(value % 62).toInt()])
            value /= 62
        }
        return sb.reverse().toString()
    }

    /**
     * Generates a unique 64-bit Long ID.
     */
    fun nextNumericId(): Long {
        return counter.incrementAndGet()
    }
}
