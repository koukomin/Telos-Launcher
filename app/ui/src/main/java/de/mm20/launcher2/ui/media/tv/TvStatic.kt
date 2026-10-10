package de.mm20.launcher2.ui.media.tv

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Fills [pixels] with grayscale noise (xorshift, no allocation) */
private fun fillNoise(pixels: IntArray, seed: Int) {
    var x = if (seed == 0) 0x2545F491 else seed
    for (i in pixels.indices) {
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        // slight contrast: stretch the middle of the range
        var g = (x ushr 24) and 0xFF
        g = ((g - 128) * 9 / 8 + 128).coerceIn(0, 255)
        pixels[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }
}

private const val NOISE_W = 160
private const val NOISE_H = 90
private const val MINI_W = 40
private const val MINI_H = 24

/** One frozen low-res frame shared by all small tiles (created once, on first use) */
private val miniNoise: ImageBitmap by lazy {
    val px = IntArray(MINI_W * MINI_H)
    fillNoise(px, Random.nextInt())
    Bitmap.createBitmap(MINI_W, MINI_H, Bitmap.Config.ARGB_8888).also {
        it.setPixels(px, 0, MINI_W, 0, 0, MINI_W, MINI_H)
    }.asImageBitmap()
}

/** Small, cheap, frozen static for the logo tile of a channel that does not work */
@Composable
fun TvStaticMini(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawImage(
            miniNoise,
            srcOffset = IntOffset.Zero, srcSize = IntSize(MINI_W, MINI_H),
            dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.None,
        )
    }
}

/**
 * Animated analog TV noise: a small noise bitmap regenerated about 20 times per second on a background
 * dispatcher (two bitmaps, reused, no per-frame allocation), scaled up without smoothing, with scanlines,
 * a slow rolling band, a random horizontal tear, a vignette and a gentle flicker. The loop only runs while the
 * lifecycle is at least started and ends when the composable leaves the composition. With [reduceAnimations]
 * a single frozen frame is shown.
 */
@Composable
fun TvStatic(modifier: Modifier = Modifier, reduceAnimations: Boolean = false) {
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val bitmaps = remember {
        Array(2) { Bitmap.createBitmap(NOISE_W, NOISE_H, Bitmap.Config.ARGB_8888) }
    }
    val images = remember { Array(2) { bitmaps[it].asImageBitmap() } }
    val pixels = remember { IntArray(NOISE_W * NOISE_H) }
    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(reduceAnimations) {
        if (reduceAnimations) {
            withContext(Dispatchers.Default) {
                fillNoise(pixels, Random.nextInt())
                bitmaps[0].setPixels(pixels, 0, NOISE_W, 0, 0, NOISE_W, NOISE_H)
            }
            frame = 0
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val back = (frame + 1) and 1
                withContext(Dispatchers.Default) {
                    fillNoise(pixels, Random.nextInt())
                    bitmaps[back].setPixels(pixels, 0, NOISE_W, 0, 0, NOISE_W, NOISE_H)
                }
                frame += 1
                delay(50)
            }
        }
    }

    val scanStep = with(density) { 3.dp.toPx() }
    Canvas(modifier) {
        val f = frame
        val w = size.width.toInt().coerceAtLeast(1)
        val h = size.height.toInt().coerceAtLeast(1)
        val img = images[if (reduceAnimations) 0 else f and 1]
        drawImage(
            img,
            srcOffset = IntOffset.Zero, srcSize = IntSize(NOISE_W, NOISE_H),
            dstOffset = IntOffset.Zero, dstSize = IntSize(w, h),
            filterQuality = FilterQuality.None,
        )
        if (!reduceAnimations) {
            // horizontal tear: a few rows shifted sideways on some frames
            if (f % 5 == 0) {
                val row = (f * 37) % (NOISE_H - 6)
                val rows = 2 + f % 4
                val dx = (((f * 53) % 17) - 8) * (w / 160)
                drawImage(
                    img,
                    srcOffset = IntOffset(0, row), srcSize = IntSize(NOISE_W, rows),
                    dstOffset = IntOffset(dx, row * h / NOISE_H), dstSize = IntSize(w, rows * h / NOISE_H + 1),
                    filterQuality = FilterQuality.None,
                )
            }
            // slow rolling brighter band
            val bandH = size.height * 0.18f
            val y = ((f % 160) / 160f) * (size.height + bandH) - bandH
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.14f), Color.Transparent),
                    startY = y, endY = y + bandH,
                ),
                topLeft = Offset(0f, y), size = Size(size.width, bandH),
            )
        }
        // scanlines
        var sy = 0f
        while (sy < size.height) {
            drawRect(Color.Black.copy(alpha = 0.2f), topLeft = Offset(0f, sy), size = Size(size.width, scanStep / 3f))
            sy += scanStep
        }
        // vignette
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                center = center, radius = maxOf(size.width, size.height) * 0.75f,
            )
        )
        // flicker
        if (!reduceAnimations) {
            val flick = ((f * 31) % 11) / 11f * 0.09f
            drawRect(Color.Black.copy(alpha = flick))
        }
    }
}
