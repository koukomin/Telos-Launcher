package de.mm20.launcher2.wallpapers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.ui.WallpaperSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Plays a user-selected video file (see [WallpapersService.setVideoWallpaper]) in a loop as a
 * live wallpaper. Always muted.
 *
 * Power/performance contract: whenever the wallpaper doesn't need to render - engine invisible
 * (launcher in background, screen off), surface gone, battery saver active, or the device is
 * thermally throttling - the [MediaPlayer] is fully *released*, not just paused, so no decoder
 * or codec resources stay allocated. The surface keeps showing the last rendered frame, and
 * playback is recreated (resuming near the previous position) when all conditions clear.
 */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return VideoEngine()
    }

    inner class VideoEngine : Engine(), KoinComponent {

        private val settings: WallpaperSettings by inject()
        private val powerManager
            get() = this@VideoWallpaperService.getSystemService<PowerManager>()

        private var scope: CoroutineScope? = null

        private var player: MediaPlayer? = null
        private var lastPositionMs = 0

        private var isSurfaceReady = false
        private var isEngineVisible = false
        private var isPowerSaveActive = false
        private var thermalStatus = 0 // PowerManager.THERMAL_STATUS_NONE

        private var pauseOnBatterySaver = true
        private var pauseOnThermal = true

        // TODO(desktop-mode): when Desktop Mode lands, add its "active" state as another pause
        // condition in shouldPlay(), same pattern as battery saver / thermal below.

        private val powerSaveReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                isPowerSaveActive = powerManager?.isPowerSaveMode == true
                updatePlayback()
            }
        }

        private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            this.scope = scope
            scope.launch {
                settings.videoPauseOnBatterySaver.collect {
                    pauseOnBatterySaver = it
                    updatePlayback()
                }
            }
            scope.launch {
                settings.videoPauseOnThermalThrottling.collect {
                    pauseOnThermal = it
                    updatePlayback()
                }
            }
            scope.launch {
                videoChanged.collect {
                    releasePlayer()
                    lastPositionMs = 0
                    updatePlayback()
                }
            }

            isPowerSaveActive = powerManager?.isPowerSaveMode == true
            ContextCompat.registerReceiver(
                this@VideoWallpaperService,
                powerSaveReceiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )

            if (isAtLeastApiLevel(29)) {
                val listener = PowerManager.OnThermalStatusChangedListener { status ->
                    thermalStatus = status
                    updatePlayback()
                }
                thermalListener = listener
                powerManager?.addThermalStatusListener(listener)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            isSurfaceReady = true
            updatePlayback()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            isSurfaceReady = false
            updatePlayback()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isEngineVisible = visible
            updatePlayback()
        }

        override fun onDestroy() {
            super.onDestroy()
            try {
                this@VideoWallpaperService.unregisterReceiver(powerSaveReceiver)
            } catch (e: IllegalArgumentException) {
                // not registered
            }
            if (isAtLeastApiLevel(29)) {
                thermalListener?.let { powerManager?.removeThermalStatusListener(it) }
                thermalListener = null
            }
            releasePlayer()
            scope?.cancel()
            scope = null
        }

        private fun shouldPlay(): Boolean {
            if (!isSurfaceReady || !isEngineVisible) return false
            if (pauseOnBatterySaver && isPowerSaveActive) return false
            if (pauseOnThermal && isAtLeastApiLevel(29) &&
                thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
            ) return false
            return true
        }

        private fun updatePlayback() {
            if (shouldPlay()) {
                if (player == null) createPlayer()
            } else {
                releasePlayer()
            }
        }

        private fun createPlayer() {
            val file = WallpapersService.getVideoFile(this@VideoWallpaperService)
            if (!file.exists()) return
            val p = MediaPlayer()
            try {
                p.setDataSource(file.absolutePath)
                p.setSurface(surfaceHolder.surface)
                p.isLooping = true
                // Video wallpapers are always muted, unconditionally.
                p.setVolume(0f, 0f)
                p.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                p.setOnPreparedListener {
                    if (lastPositionMs > 0) it.seekTo(lastPositionMs)
                    it.start()
                }
                p.setOnErrorListener { _, _, _ ->
                    releasePlayer()
                    true
                }
                p.prepareAsync()
                player = p
            } catch (e: Exception) {
                CrashReporter.logException(e)
                p.release()
                player = null
            }
        }

        private fun releasePlayer() {
            val p = player ?: return
            player = null
            try {
                if (p.isPlaying) lastPositionMs = p.currentPosition
            } catch (e: IllegalStateException) {
                // player was not in a queryable state; keep the previous position
            }
            p.release()
        }
    }

    companion object {
        /**
         * Emitted by [WallpapersService] after the video file has been replaced, so running
         * engines drop their player and reload the new file immediately.
         */
        internal val videoChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    }
}
