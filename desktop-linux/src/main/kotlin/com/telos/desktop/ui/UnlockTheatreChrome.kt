package com.telos.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.i18n.TelosI18n.t
import com.telos.desktop.icons.CandyCyberIcon
import com.telos.desktop.icons.CandyIcon
import com.telos.desktop.theme.QuietOpsTokens
import com.telos.desktop.theme.ThemeIntensity
import com.telos.desktop.theme.UnlockTheatre
import com.telos.desktop.theme.VaultNeonTokens
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/**
 * Quiet Ops locked-boot chrome: charcoal + vault glyph only (no user wallpaper).
 */
@Composable
fun UnlockTheatreLockedHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CandyCyberIcon(CandyIcon.TelosLogo, size = 72.dp)
        Text(
            t("desktop_unlock_theatre_title"),
            color = QuietOpsTokens.accent,
            fontSize = 22.sp,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            t("desktop_unlock_theatre_quiet_ops"),
            color = QuietOpsTokens.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * After unlock: crossfade Quiet Ops scrim away and reveal vault wallpaper + intensity.
 * No particle effects.
 */
@Composable
fun UnlockTheatreRevealLayer(
    unlocked: Boolean,
    vaultRoot: File?,
    wallpaperPath: String?,
    intensity: ThemeIntensity,
    playReveal: Boolean,
    onRevealFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(if (playReveal) 0f else 1f) }
    var wallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(unlocked, vaultRoot?.absolutePath, wallpaperPath) {
        wallpaper = null
        if (!unlocked) return@LaunchedEffect
        val file = UnlockTheatre.resolveWallpaperFile(unlocked, vaultRoot, wallpaperPath)
            ?: return@LaunchedEffect
        wallpaper = withContext(Dispatchers.IO) { decodeWallpaper(file) }
    }
    LaunchedEffect(playReveal, unlocked) {
        if (!unlocked) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        if (playReveal) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(UnlockTheatre.DEFAULT_REVEAL_MS))
            onRevealFinished()
        } else {
            progress.snapTo(1f)
        }
    }
    val alphas = UnlockTheatre.revealAlphas(progress.value)
    Box(modifier.fillMaxSize()) {
        wallpaper?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(alphas.wallpaper),
            )
        }
        if (alphas.quietOpsScrim > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(alphas.quietOpsScrim)
                    .background(QuietOpsTokens.background),
            )
        }
        // Subtle intensity cue along the top edge — no particles.
        val stripAlpha = alphas.intensityMix * intensity.techBorderAlpha * 0.55f
        if (stripAlpha > 0.02f) {
            val accent = when (intensity) {
                ThemeIntensity.QuietOps -> QuietOpsTokens.accent
                ThemeIntensity.VaultNeon -> VaultNeonTokens.accentMagenta
                ThemeIntensity.PaperOps -> QuietOpsTokens.accent
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(accent.copy(alpha = stripAlpha)),
            )
        }
    }
}

private fun decodeWallpaper(file: File): ImageBitmap? = runCatching {
    val bytes = file.readBytes()
    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()
