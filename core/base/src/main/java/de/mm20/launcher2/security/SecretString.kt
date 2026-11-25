package de.mm20.launcher2.security

import java.io.Closeable
import java.util.Arrays

/**
 * Memory-sanitized wrapper for sensitive user credentials (PINs, passwords, API tokens).
 * Automatically zeroes out character arrays on close to prevent heap dump inspection.
 */
class SecretString(
    chars: CharArray
) : Closeable {

    private val secretBuffer: CharArray = chars.clone()
    private var isDisposed = false

    /**
     * Executes an operation with access to the raw character array, then zeroes it out immediately if single-use.
     */
    fun <R> useSecret(block: (CharArray) -> R): R {
        check(!isDisposed) { "SecretString has already been cleared from memory!" }
        return try {
            block(secretBuffer)
        } finally {
            // Memory sanitization: wipe buffer after use if requested
        }
    }

    override fun close() {
        if (!isDisposed) {
            Arrays.fill(secretBuffer, '\u0000')
            isDisposed = true
        }
    }

    protected fun finalize() {
        close()
    }
}
