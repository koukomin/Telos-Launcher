package de.mm20.launcher2.comms.media

import androidx.media3.common.PlaybackException

/**
 * Pure decision logic of [StreamReconnector]: which errors are worth reconnecting for and how long to
 * wait between attempts. Kept free of Android classes (the clock is injectable) so it can be unit tested.
 */
class ReconnectPolicy(
    private val maxOtherRetries: Int = 3,
    private val stableAfterMs: Long = 30_000L,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    enum class Kind { NETWORK, OTHER }

    /** Failures in a row (network and other) since the stream last played stably */
    var attempts = 0
        private set

    /** Retries spent on errors that do not look like a lost connection */
    var otherRetries = 0
        private set

    private var readyAt = -1L

    /** The stream plays. If it keeps playing for [stableAfterMs] the next failure starts at 1 s again. */
    fun onReady() {
        if (readyAt < 0) readyAt = clock()
    }

    /**
     * Registers a failure. Returns false when the error must not be retried any more
     * (more than [maxOtherRetries] non-network errors), true when another attempt should be made.
     */
    fun onError(kind: Kind): Boolean {
        if (readyAt >= 0 && clock() - readyAt >= stableAfterMs) {
            attempts = 0
            otherRetries = 0
        }
        readyAt = -1
        if (kind == Kind.OTHER) {
            if (otherRetries >= maxOtherRetries) return false
            otherRetries++
        }
        attempts++
        return true
    }

    /**
     * Wait before the next attempt: 1, 2, 4, 8, then 15 s. Null while offline: nothing is tried then,
     * the connection callback starts the next attempt.
     */
    fun delayMs(online: Boolean): Long? = if (online) backoffMs(attempts) else null

    fun reset() {
        attempts = 0
        otherRetries = 0
        readyAt = -1
    }

    companion object {
        private val BACKOFF_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000)

        /** [attempt] counts from 1 */
        fun backoffMs(attempt: Int): Long = BACKOFF_MS[(attempt - 1).coerceIn(0, BACKOFF_MS.size - 1)]

        /**
         * @param httpStatus status of an [PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS], if known
         * @param hasIoCause true when an IOException is in the cause chain
         */
        fun classify(errorCode: Int, httpStatus: Int?, hasIoCause: Boolean): Kind = when (errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
            PlaybackException.ERROR_CODE_TIMEOUT -> Kind.NETWORK
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                if (httpStatus != null && (httpStatus in 500..599 || httpStatus == 408 || httpStatus == 429)) Kind.NETWORK
                else Kind.OTHER
            PlaybackException.ERROR_CODE_UNSPECIFIED -> if (hasIoCause) Kind.NETWORK else Kind.OTHER
            else -> Kind.OTHER
        }
    }
}
