package de.mm20.launcher2.comms.media

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

/**
 * Media3 renderers with official NextLib (Maven: io.github.anilbeesetti:nextlib-media3ext).
 * FFmpeg software decoders take over when the phone has no decoder. Playback stays in Telos.
 */
@UnstableApi
object MediaDecoderPack {
    fun renderersFactory(context: Context): RenderersFactory {
        return NextRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    }
}
