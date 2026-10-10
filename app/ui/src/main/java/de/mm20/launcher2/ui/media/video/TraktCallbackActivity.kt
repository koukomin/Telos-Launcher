package de.mm20.launcher2.ui.media.video

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import de.mm20.launcher2.comms.media.video.trakt.Trakt
import de.mm20.launcher2.ui.R
import kotlin.concurrent.thread

/**
 * Receives the redirect (telos-trakt://callback) of the Trakt sign in in the browser. It only
 * forwards the answer to [Trakt.finishWebLogin], which checks the state and exchanges the code.
 */
class TraktCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        val app = applicationContext
        if (data == null || data.scheme != "telos-trakt") {
            finish()
            return
        }
        val code = data.getQueryParameter("code")
        val state = data.getQueryParameter("state")
        val error = data.getQueryParameter("error")
        thread {
            val result = runCatching { Trakt.finishWebLogin(app, code, state, error) }.getOrDefault(Trakt.WebResult.Failed)
            val msg = when (result) {
                Trakt.WebResult.Connected -> R.string.au_video_trakt_connected
                Trakt.WebResult.Denied -> R.string.au8_traktweb_denied
                Trakt.WebResult.StateMismatch -> R.string.au8_traktweb_state_mismatch
                Trakt.WebResult.Expired -> R.string.au8_traktweb_expired
                Trakt.WebResult.Failed -> R.string.au_video_trakt_signin_failed
            }
            runOnUiThread {
                Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
