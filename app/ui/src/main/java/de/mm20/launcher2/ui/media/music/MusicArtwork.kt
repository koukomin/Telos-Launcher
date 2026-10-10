package de.mm20.launcher2.ui.media.music

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.ColorUtils
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import quantize.QuantizerCelebi
import score.Score

/** The dominant colour of artwork, cached per artwork URL (colour extraction by material-color-utilities) */
object ArtworkColors {
    private val cache = LruCache<String, Int>(64)

    suspend fun dominant(context: android.content.Context, url: String): Int? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.Default) {
            runCatching {
                val request = ImageRequest.Builder(context).data(url).size(96).allowHardware(false).build()
                val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable ?: return@runCatching null
                val bmp: Bitmap = drawable.toBitmap(64, 64, Bitmap.Config.ARGB_8888)
                val pixels = IntArray(bmp.width * bmp.height)
                bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                val best = Score.score(QuantizerCelebi.quantize(pixels, 8), 1, 0xFF3A3A4A.toInt()).first()
                cache.put(url, best)
                best
            }.getOrNull()
        }
    }
}

/**
 * A colour taken from the artwork at [url], animated when it changes (instantly when [reduceAnimations]).
 * [fallback] is used while loading or when there is no artwork.
 */
@Composable
fun rememberArtworkTint(url: String?, fallback: Color, reduceAnimations: Boolean): State<Color> {
    val context = LocalContext.current
    val target = remember(url) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url) {
        target.value = if (url == null) null else ArtworkColors.dominant(context, url)?.let { Color(it) }
    }
    return animateColorAsState(
        target.value ?: fallback,
        animationSpec = if (reduceAnimations) snap() else tween(600),
        label = "artworkTint",
    )
}

/** A tint made dark enough for white text */
fun Color.darkened(amount: Float): Color =
    Color(ColorUtils.blendARGB(toArgb(), android.graphics.Color.BLACK, amount.coerceIn(0f, 1f)))

/** The mini player in the colours of the artwork; reusable by the media hub. */
@Composable
fun ArtTintedMiniPlayer(
    title: String,
    artist: String,
    artUrl: String?,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reduceAnimations: Boolean = false,
) {
    val tint by rememberArtworkTint(artUrl, MaterialTheme.colorScheme.surfaceVariant, reduceAnimations)
    val bg = tint.darkened(0.45f).copy(alpha = 0.92f)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        contentColor = Color.White,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtOrNote(artUrl, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onToggle) {
                Icon(painterResource(if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px), contentDescription = stringResource(if (isPlaying) R.string.au_music_pause else R.string.hc_play))
            }
            IconButton(onClick = onNext) {
                Icon(painterResource(R.drawable.skip_next_24px), contentDescription = stringResource(R.string.hc_next))
            }
        }
    }
}
