package de.mm20.launcher2.ui.media.music

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import de.mm20.launcher2.comms.scrobble.LastFm
import de.mm20.launcher2.ui.R
import kotlin.concurrent.thread

/**
 * Receives the redirect (telos-lastfm://callback?token=...) of the Last.fm sign in in the browser.
 * It only forwards the token to [LastFm.finishWebLogin], which ignores it unless a sign in was
 * started in the app in the last 10 minutes.
 */
class LastFmCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        val app = applicationContext
        if (data == null || data.scheme != "telos-lastfm") {
            finish()
            return
        }
        val token = data.getQueryParameter("token")
        thread {
            val result = runCatching { LastFm.finishWebLogin(app, token) }.getOrDefault(LastFm.Companion.WebResult.Failed)
            val msg = when (result) {
                LastFm.Companion.WebResult.Connected -> R.string.au_music_lastfm_connected
                LastFm.Companion.WebResult.NotPending -> R.string.au12_lastfmweb_not_pending
                LastFm.Companion.WebResult.Expired -> R.string.au12_lastfmweb_expired
                LastFm.Companion.WebResult.Failed -> R.string.au12_lastfmweb_failed
            }
            runOnUiThread {
                Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
