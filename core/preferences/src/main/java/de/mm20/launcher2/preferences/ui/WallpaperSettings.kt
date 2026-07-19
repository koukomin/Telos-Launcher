package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.VideoWallpaperScalingMode
import de.mm20.launcher2.preferences.VideoWallpaperStartBehavior
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Visual transforms applied by the video wallpaper's GL renderer. */
data class VideoWallpaperTransforms(
    val scalingMode: VideoWallpaperScalingMode,
    val zoom: Float,
    val positionX: Float,
    val positionY: Float,
    val brightness: Float,
    val parallax: Boolean,
    val parallaxStrength: Float,
)

class WallpaperSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val videoPauseOnBatterySaver
        get() = dataStore.data.map { it.videoWallpaperPauseOnBatterySaver }
            .distinctUntilChanged()

    fun setVideoPauseOnBatterySaver(pause: Boolean) {
        dataStore.update { it.copy(videoWallpaperPauseOnBatterySaver = pause) }
    }

    val videoPauseOnThermalThrottling
        get() = dataStore.data.map { it.videoWallpaperPauseOnThermalThrottling }
            .distinctUntilChanged()

    fun setVideoPauseOnThermalThrottling(pause: Boolean) {
        dataStore.update { it.copy(videoWallpaperPauseOnThermalThrottling = pause) }
    }

    val videoTransforms
        get() = dataStore.data.map {
            VideoWallpaperTransforms(
                scalingMode = it.videoWallpaperScalingMode,
                zoom = it.videoWallpaperZoom,
                positionX = it.videoWallpaperPositionX,
                positionY = it.videoWallpaperPositionY,
                brightness = it.videoWallpaperBrightness,
                parallax = it.videoWallpaperParallax,
                parallaxStrength = it.videoWallpaperParallaxStrength,
            )
        }.distinctUntilChanged()

    fun setVideoScalingMode(mode: VideoWallpaperScalingMode) {
        dataStore.update { it.copy(videoWallpaperScalingMode = mode) }
    }

    fun setVideoZoom(zoom: Float) {
        dataStore.update { it.copy(videoWallpaperZoom = zoom) }
    }

    fun setVideoPosition(x: Float, y: Float) {
        dataStore.update { it.copy(videoWallpaperPositionX = x, videoWallpaperPositionY = y) }
    }

    fun setVideoBrightness(brightness: Float) {
        dataStore.update { it.copy(videoWallpaperBrightness = brightness) }
    }

    fun setVideoParallax(enabled: Boolean) {
        dataStore.update { it.copy(videoWallpaperParallax = enabled) }
    }

    fun setVideoParallaxStrength(strength: Float) {
        dataStore.update { it.copy(videoWallpaperParallaxStrength = strength) }
    }

    val videoSpeed
        get() = dataStore.data.map { it.videoWallpaperSpeed }.distinctUntilChanged()

    fun setVideoSpeed(speed: Float) {
        dataStore.update { it.copy(videoWallpaperSpeed = speed) }
    }

    val videoStartBehavior
        get() = dataStore.data.map { it.videoWallpaperStartBehavior }.distinctUntilChanged()

    fun setVideoStartBehavior(behavior: VideoWallpaperStartBehavior) {
        dataStore.update { it.copy(videoWallpaperStartBehavior = behavior) }
    }

    val videoThemeColors
        get() = dataStore.data.map { it.videoWallpaperThemeColors }.distinctUntilChanged()

    fun setVideoThemeColors(enabled: Boolean) {
        dataStore.update { it.copy(videoWallpaperThemeColors = enabled) }
    }

    val videoPauseOnDesktopMode
        get() = dataStore.data.map { it.videoWallpaperPauseOnDesktopMode }.distinctUntilChanged()

    fun setVideoPauseOnDesktopMode(pause: Boolean) {
        dataStore.update { it.copy(videoWallpaperPauseOnDesktopMode = pause) }
    }

    val dimWallpaper
        get() = dataStore.data.map {
            it.wallpaperDim
        }.distinctUntilChanged()

    fun setDimWallpaper(dimWallpaper: Boolean) {
        dataStore.update {
            it.copy(wallpaperDim = dimWallpaper)
        }
    }

    val blurWallpaper
        get() = dataStore.data.map {
            it.wallpaperBlur
        }.distinctUntilChanged()

    fun setBlurWallpaper(blurWallpaper: Boolean) {
        dataStore.update {
            it.copy(wallpaperBlur = blurWallpaper)
        }
    }

    val wallpaperBlurRadius
        get() = dataStore.data.map {
            it.wallpaperBlurRadius
        }.distinctUntilChanged()

    fun setWallpaperBlurRadius(wallpaperBlurRadius: Int) {
        dataStore.update {
            it.copy(wallpaperBlurRadius = wallpaperBlurRadius)
        }
    }
}
