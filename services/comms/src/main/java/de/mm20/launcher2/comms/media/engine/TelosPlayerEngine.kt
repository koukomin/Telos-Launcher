package de.mm20.launcher2.comms.media.engine

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.io.File

/**
 * Lazy ExoPlayer factory: no player, cache, or decoder is allocated until a Media
 * virtual app actually opens. Call [releaseCache] when the last player is released.
 * - Hardware video decoding only (no software rendering lag)
 * - DSP Audio Offload (Media3 AudioOffloadPreferences) for screen-off YT Music battery savings.
 * - Aggressive buffering strategy (instant start, limited RAM max).
 * - 250MB SimpleCache for zero-network replays.
 */
@UnstableApi
object TelosPlayerEngine {

    private var simpleCache: SimpleCache? = null
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null

    fun getCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        return cacheDataSourceFactory ?: synchronized(this) {
            cacheDataSourceFactory ?: run {
                val cacheDir = File(context.applicationContext.cacheDir, "telos_media_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(250L * 1024 * 1024) // 250 MB
                val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)

                val cache = SimpleCache(cacheDir, evictor, databaseProvider).also {
                    simpleCache = it
                }

                val upstreamFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setAllowCrossProtocolRedirects(true)
                
                val defaultDataSourceFactory = DefaultDataSource.Factory(context.applicationContext, upstreamFactory)

                CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(defaultDataSourceFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .also { cacheDataSourceFactory = it }
            }
        }
    }

    /**
     * Builds an ultra-optimized ExoPlayer instance.
     */
    fun buildExoPlayer(
        context: Context,
        trackSelector: TelosTrackSelector
    ): ExoPlayer {
        val appContext = context.applicationContext

        // 1. Hardware Codecs Only - Disable Extension (Software) Renderers
        val renderersFactory = DefaultRenderersFactory(appContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        // 2. Custom Load Control for RAM & Instant Start
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,  // minBufferMs
                45_000,  // maxBufferMs
                1_000,   // bufferForPlaybackMs (1-second playback start!)
                2_500    // bufferForPlaybackAfterRebufferMs
            )
            .setTargetBufferBytes(35 * 1024 * 1024) // Cap ExoPlayer RAM to ~35MB
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 3. Audio Attributes for Background Playback
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(appContext, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {

                // 4. DSP Audio Offload (Massive Battery Saver for Screen-Off Audio)
                val audioOffloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                    .setIsGaplessSupportRequired(true)
                    .setIsSpeedChangeSupportRequired(false)
                    .build()

                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setAudioOffloadPreferences(audioOffloadPreferences)
                    .build()
            }
    }

    fun releaseCache() {
        simpleCache?.release()
        simpleCache = null
        cacheDataSourceFactory = null
    }
}
