package de.mm20.launcher2.comms.tv

/** Pure rules of how [TvPlayerController] reacts when a stream fails or stalls (offline-aware reconnecting) */
object TvReconnectDecision {
    enum class Action {
        /** Device is offline: no failover, nothing is marked bad, no catalog check; wait for the network and retry the same stream */
        WAIT_FOR_NETWORK,

        /** Online: reconnect to the same stream (with backoff) before treating it as dead */
        RETRY_SAME,

        /** Online and the stream is dead: next stream of the channel, then the catalog check, then the error */
        FAIL_OVER,
    }

    /** Online means a default network that has internet and is validated */
    fun isOnline(hasNetwork: Boolean, hasInternet: Boolean, validated: Boolean): Boolean =
        hasNetwork && hasInternet && validated

    /**
     * @param online current connectivity
     * @param hadSignal the stream played before, or it was interrupted by going offline (so it is not proven bad)
     * @param retries same-stream retries used since the stream last played stably
     */
    fun decide(online: Boolean, hadSignal: Boolean, retries: Int, maxRetries: Int): Action = when {
        !online -> Action.WAIT_FOR_NETWORK
        hadSignal && retries < maxRetries -> Action.RETRY_SAME
        else -> Action.FAIL_OVER
    }

    /** All streams failing is only an error while online; offline the controller keeps waiting */
    fun giveUpAsError(online: Boolean): Boolean = online
}
