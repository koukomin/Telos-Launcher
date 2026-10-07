package de.mm20.launcher2.ui.media.video

import android.content.Context

/** Whether videos opened from Telos play in their own process (experimental, off by default). */
internal object PlayerChoice {
    private const val PREFS = "video_player_options"
    private const val ISOLATED = "isolated"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isolated(context: Context): Boolean = prefs(context).getBoolean(ISOLATED, false)

    fun setIsolated(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(ISOLATED, value).apply()
    }

    fun playerClass(context: Context): Class<*> =
        if (isolated(context)) IsolatedVideoPlayerActivity::class.java else VideoPlayerActivity::class.java
}
