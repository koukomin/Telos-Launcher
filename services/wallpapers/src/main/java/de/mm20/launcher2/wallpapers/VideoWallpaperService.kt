package de.mm20.launcher2.wallpapers

import android.app.WallpaperColors
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.ExoPlayer
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.desktopmode.DesktopModeManager
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.VideoWallpaperStartBehavior
import de.mm20.launcher2.preferences.ui.WallpaperSettings
import de.mm20.launcher2.preferences.ui.VideoWallpaperTransforms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Advanced video wallpaper service using Media3 (ExoPlayer).
 * Supports looping, speed control, zoom, brightness, parallax, and color extraction.
 * Always muted, unconditionally.
 */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return VideoEngine()
    }

    inner class VideoEngine : Engine(), KoinComponent {

        private val settings: WallpaperSettings by inject()
        private val desktopModeManager: DesktopModeManager by inject()
        private val powerManager
            get() = this@VideoWallpaperService.getSystemService<PowerManager>()

        private var scope: CoroutineScope? = null

        private var player: ExoPlayer? = null
        private var lastPositionMs = 0L

        private var isSurfaceReady = false
        private var isEngineVisible = false
        private var isPowerSaveActive = false
        private var thermalStatus = 0

        private var pauseOnBatterySaver = true
        private var pauseOnThermal = true
        private var pauseOnDesktopMode = false
        private var isDesktopModeActive = false
        private var transforms = VideoWallpaperTransforms(
            de.mm20.launcher2.preferences.VideoWallpaperScalingMode.Fill,
            1f, 0f, 0f, 1f, false, 0.2f
        )
        private var speed = 1f
        private var startBehavior = VideoWallpaperStartBehavior.Resume

        private var xOffset = 0.5f
        private var cachedColors: WallpaperColors? = null

        private val powerSaveReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                isPowerSaveActive = powerManager?.isPowerSaveMode == true
                updatePlayback()
            }
        }

        private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

        @OptIn(UnstableApi::class)
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
                settings.videoPauseOnDesktopMode.collect {
                    pauseOnDesktopMode = it
                    updatePlayback()
                }
            }
            scope.launch {
                desktopModeManager.shouldShowDesktopShell.collect {
                    isDesktopModeActive = it
                    updatePlayback()
                }
            }
            scope.launch {
                settings.videoTransforms.collect {
                    val changed = transforms != it
                    transforms = it
                    // Effects can only be installed before prepare(), so transform changes
                    // rebuild the player. Parallax scroll offsets don't go through here.
                    if (changed) recreatePlayer()
                }
            }
            scope.launch {
                settings.videoSpeed.collect {
                    speed = it
                    player?.setPlaybackSpeed(speed)
                }
            }
            scope.launch {
                settings.videoStartBehavior.collect {
                    startBehavior = it
                }
            }
            scope.launch {
                videoChanged.collect {
                    releasePlayer()
                    lastPositionMs = 0
                    updatePlayback()
                    extractColors()
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

            setTouchEventsEnabled(true)
            extractColors()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            isSurfaceReady = true
            updatePlayback()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            // The Presentation effect is sized to the surface, so a resize (e.g. rotation)
            // needs a rebuilt pipeline. With default transforms MediaCodec handles scaling.
            if (!hasDefaultTransforms()) recreatePlayer()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            isSurfaceReady = false
            updatePlayback()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isEngineVisible = visible
            if (visible && startBehavior == VideoWallpaperStartBehavior.Restart) {
                lastPositionMs = 0
                player?.seekTo(0)
            } else if (visible && startBehavior == VideoWallpaperStartBehavior.Random) {
                player?.let {
                    val duration = it.duration
                    if (duration > 0 && duration != C.TIME_UNSET) {
                        lastPositionMs = (Math.random() * duration).toLong()
                        it.seekTo(lastPositionMs)
                    }
                }
            }
            updatePlayback()
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixels: Int,
            yPixels: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixels, yPixels)
            // The parallax MatrixTransformation reads this on every frame; no pipeline rebuild.
            this.xOffset = xOffset
        }

        // Note: deliberately no double-tap-to-pause via COMMAND_TAP here. The launcher forwards
        // every home screen tap as a wallpaper command, so a double tap already triggers the
        // user's configured double-tap gesture (screen lock by default) - reacting here too
        // would fire both actions at once.

        override fun onDestroy() {
            super.onDestroy()
            try {
                this@VideoWallpaperService.unregisterReceiver(powerSaveReceiver)
            } catch (e: IllegalArgumentException) {
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
            if (pauseOnDesktopMode && isDesktopModeActive) return false
            return true
        }

        /**
         * Power contract: any reason not to play - engine invisible, surface gone, battery
         * saver, thermal throttling - fully *releases* the player so no decoder or codec
         * resources stay allocated. The surface keeps its last frame; playback recreates and
         * resumes near the previous position when conditions clear.
         */
        private fun updatePlayback() {
            if (shouldPlay()) {
                if (player == null) createPlayer()
                else player?.play()
            } else {
                releasePlayer()
            }
        }

        /** Called when visual transform settings change; effects must be set before prepare. */
        private fun recreatePlayer() {
            releasePlayer()
            updatePlayback()
        }

        @OptIn(UnstableApi::class)
        private fun createPlayer() {
            val dir = WallpapersService.getVideoDir(this@VideoWallpaperService)
            val files = dir.listFiles()?.filter { !it.name.endsWith(".tmp") }?.sortedBy { it.name }

            val legacyFile = WallpapersService.getVideoFile(this@VideoWallpaperService)
            val allFiles = if (files.isNullOrEmpty() && legacyFile.exists()) listOf(legacyFile) else files ?: emptyList()

            if (allFiles.isEmpty()) return

            val p = ExoPlayer.Builder(this@VideoWallpaperService).build()
            try {
                p.setVideoSurface(surfaceHolder.surface)
                p.repeatMode = Player.REPEAT_MODE_ALL

                val mediaItems = allFiles.map { MediaItem.fromUri(it.absolutePath) }
                p.setMediaItems(mediaItems)

                p.setPlaybackSpeed(speed)
                // Video wallpapers are always muted, unconditionally.
                p.volume = 0f

                // Effects must be installed before prepare(). With default transforms we skip
                // the GL effects pipeline entirely and let MediaCodec crop-fill the surface,
                // so the common case has zero extra GPU cost.
                val effects = buildEffects()
                if (effects.isNotEmpty()) {
                    p.setVideoEffects(effects)
                } else {
                    p.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                }

                p.prepare()
                if (lastPositionMs > 0) p.seekTo(lastPositionMs)

                player = p
                p.playWhenReady = true
            } catch (e: Exception) {
                CrashReporter.logException(e)
                p.release()
                player = null
            }
        }

        private fun hasDefaultTransforms(): Boolean {
            return transforms.scalingMode == de.mm20.launcher2.preferences.VideoWallpaperScalingMode.Fill &&
                    transforms.zoom == 1f &&
                    transforms.positionX == 0f &&
                    transforms.positionY == 0f &&
                    transforms.brightness == 1f &&
                    !transforms.parallax
        }

        @OptIn(UnstableApi::class)
        private fun buildEffects(): List<Effect> {
            if (hasDefaultTransforms()) return emptyList()

            val effects = mutableListOf<Effect>()

            val scalingMode = when (transforms.scalingMode) {
                de.mm20.launcher2.preferences.VideoWallpaperScalingMode.Fit -> Presentation.LAYOUT_SCALE_TO_FIT
                de.mm20.launcher2.preferences.VideoWallpaperScalingMode.Fill -> Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                de.mm20.launcher2.preferences.VideoWallpaperScalingMode.Stretch -> Presentation.LAYOUT_STRETCH_TO_FIT
            }

            val width = surfaceHolder.surfaceFrame.width()
            val height = surfaceHolder.surfaceFrame.height()
            if (width > 0 && height > 0) {
                effects.add(Presentation.createForWidthAndHeight(width, height, scalingMode))
            }

            if (transforms.brightness != 1f) {
                effects.add(Brightness(transforms.brightness - 1f))
            }

            // getMatrix runs per frame and reads live state, so scroll parallax updates without
            // reinstalling the effects pipeline.
            effects.add(MatrixTransformation { _ ->
                val matrix = Matrix()
                val parallaxOffset = if (transforms.parallax) {
                    (xOffset - 0.5f) * transforms.parallaxStrength
                } else 0f
                matrix.postTranslate(transforms.positionX + parallaxOffset, transforms.positionY)
                matrix.postScale(transforms.zoom, transforms.zoom, 0.5f, 0.5f)
                matrix
            })

            return effects
        }

        private fun releasePlayer() {
            val p = player ?: return
            player = null
            try {
                lastPositionMs = p.currentPosition
            } catch (e: Exception) {
            }
            p.release()
        }

        private fun extractColors() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
            scope?.launch(Dispatchers.IO) {
                val dir = WallpapersService.getVideoDir(this@VideoWallpaperService)
                val file = dir.listFiles()?.filter { !it.name.endsWith(".tmp") }?.firstOrNull() 
                    ?: WallpapersService.getVideoFile(this@VideoWallpaperService).takeIf { it.exists() }
                    ?: return@launch
                
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    val bitmap = retriever.getFrameAtTime(0)
                    if (bitmap != null) {
                        cachedColors = WallpaperColors.fromBitmap(bitmap)
                        withContext(Dispatchers.Main) {
                            notifyColorsChanged()
                        }
                    }
                } catch (e: Exception) {
                    CrashReporter.logException(e)
                } finally {
                    retriever.release()
                }
            }
        }

        override fun onComputeColors(): WallpaperColors? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                cachedColors ?: super.onComputeColors()
            } else {
                super.onComputeColors()
            }
        }
    }

    companion object {
        internal val videoChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    }
}
