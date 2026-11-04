package de.mm20.launcher2.comms.tv

/** Pure rules of "Keep playing in the background" (see [TvPlayerController] and [TvPlaybackService]) */
object TvBackgroundPolicy {
    /** The service stops itself when nothing played for this long */
    const val IDLE_STOP_MS = 5 * 60 * 1000L

    /** Upgrade the old pause-on-leave default once; subsequent explicit opt-outs survive. */
    fun backgroundAfterUpgrade(saved: Boolean, version: Int): Boolean = version < 1 || saved

    /** Leaving the app pauses playback unless background playback is on. Screen off never pauses a live channel. */
    fun pauseWhenLeaving(keepInBackground: Boolean, screenOff: Boolean = false): Boolean = !keepInBackground && !screenOff

    /**
     * Video is switched off only while the app is in the background with background playback on and the
     * screen is still on. Turning the screen off keeps the video track so a live HLS/DASH stream is not torn down.
     */
    fun videoDisabled(keepInBackground: Boolean, inBackground: Boolean, screenOff: Boolean = false): Boolean =
        keepInBackground && inBackground && !screenOff

    /**
     * True when the service should stop and release TV: nothing is playing or loading and it has been
     * that way since [idleSince] (elapsed millis, or -1 when it is playing / loading now).
     */
    fun shouldStopAfterIdle(
        playing: Boolean,
        buffering: Boolean,
        idleSince: Long,
        now: Long,
        reconnecting: Boolean = false,
    ): Boolean =
        !playing && !buffering && !reconnecting && idleSince >= 0 && now - idleSince >= IDLE_STOP_MS

    /** Android 13 and newer need the notification permission to show the playback notification */
    fun needsNotificationPermission(sdk: Int, granted: Boolean): Boolean = sdk >= 33 && !granted
}
