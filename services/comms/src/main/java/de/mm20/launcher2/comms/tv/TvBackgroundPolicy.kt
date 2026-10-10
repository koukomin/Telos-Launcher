package de.mm20.launcher2.comms.tv

/** Pure rules of "Keep playing in the background" (see [TvPlayerController] and [TvPlaybackService]) */
object TvBackgroundPolicy {
    /** The service stops itself when nothing played for this long */
    const val IDLE_STOP_MS = 5 * 60 * 1000L

    /** Leaving the app or switching the screen off pauses playback unless the user allowed background playback */
    fun pauseWhenLeaving(keepInBackground: Boolean): Boolean = !keepInBackground

    /** In the background only the sound is needed: the video track is switched off to save data */
    fun videoDisabled(keepInBackground: Boolean, inBackground: Boolean): Boolean = keepInBackground && inBackground

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
