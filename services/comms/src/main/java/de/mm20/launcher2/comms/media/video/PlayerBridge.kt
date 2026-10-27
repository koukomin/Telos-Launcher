package de.mm20.launcher2.comms.media.video

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import de.mm20.launcher2.base.containedScope
import de.mm20.launcher2.comms.media.video.trakt.Trakt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * What the player process hands back to the main process, which owns the stored data (watch
 * positions, Trakt login): the player sends a broadcast, [PlayerBridgeReceiver] does the work.
 */
object PlayerBridge {
    const val ACTION_STREMIO_PROGRESS = "de.mm20.launcher2.video.BRIDGE_STREMIO_PROGRESS"
    const val ACTION_RESUME = "de.mm20.launcher2.video.BRIDGE_RESUME"
    const val ACTION_VIDEO = "de.mm20.launcher2.video.BRIDGE_VIDEO"
    const val ACTION_SCROBBLE = "de.mm20.launcher2.video.BRIDGE_SCROBBLE"

    fun send(context: Context, action: String, extras: Bundle.() -> Unit) {
        runCatching {
            val intent = Intent(action)
                .setClassName(context.packageName, PlayerBridgeReceiver::class.java.name)
                .putExtras(Bundle().apply(extras))
            context.sendBroadcast(intent)
        }
    }
}

class PlayerBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PlayerBridge.ACTION_STREMIO_PROGRESS -> {
                val pending = goAsync()
                containedScope(Dispatchers.IO).launch {
                    var recorded: Pair<de.mm20.launcher2.comms.stremio.WatchProgress, String>? = null
                    try {
                        kotlinx.coroutines.withTimeout(8_000) {
                            de.mm20.launcher2.comms.stremio.StremioPlaybackBridge.decode(intent)?.let { pair ->
                                if (de.mm20.launcher2.comms.stremio.StremioPlaybackBridge.record(pair.first, pair.second)) recorded = pair
                            }
                        }
                    } catch (error: Exception) {
                        android.util.Log.w("StremioSync", "Could not persist playback: ${error.javaClass.simpleName}")
                    } finally { pending.finish() }
                    recorded?.let { (progress, session) ->
                        de.mm20.launcher2.comms.stremio.StremioPlaybackBridge.upload(progress, session, intent.getBooleanExtra("force", false))
                    }
                }
            }
            PlayerBridge.ACTION_RESUME -> {
                val uri = intent.getStringExtra("uri") ?: return
                ResumeStore.save(context, Uri.parse(uri), intent.getLongExtra("pos", 0L), intent.getLongExtra("dur", 0L))
            }
            PlayerBridge.ACTION_VIDEO -> {
                val app = context.applicationContext
                when (intent.getStringExtra("state")) {
                    "started" -> de.mm20.launcher2.comms.media.PlaybackCoordinator.onVideoStarted(app)
                    "stopped" -> de.mm20.launcher2.comms.media.PlaybackCoordinator.onVideoStopped(app)
                }
            }
            PlayerBridge.ACTION_SCROBBLE -> {
                if (intent.hasExtra("id")) {
                    val app = context.applicationContext
                    val pending = goAsync()
                    containedScope(Dispatchers.IO).launch {
                        try {
                            de.mm20.launcher2.comms.media.video.trakt.TraktScrobbleWorker.enqueue(app, intent).result.get(5, java.util.concurrent.TimeUnit.SECONDS)
                        } catch (error: Exception) { android.util.Log.w("TraktSync", "Could not queue playback: ${error.javaClass.simpleName}") }
                        finally { pending.finish() }
                    }
                    return
                }
                val action = intent.getStringExtra("action") ?: return
                val name = intent.getStringExtra("name") ?: return
                val progress = intent.getFloatExtra("progress", 0f)
                val appContext = context.applicationContext
                val pending = goAsync()
                containedScope(Dispatchers.IO).launch {
                    try {
                        Trakt.scrobble(appContext, action, EpisodeParser.parse(name), progress)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
