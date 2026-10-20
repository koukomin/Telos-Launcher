package de.mm20.launcher2.ui.theme

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.mm20.launcher2.preferences.SystemBarColors
import de.mm20.launcher2.preferences.ui.WallpaperSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.math.abs

/**
 * Android 13+ may return the default image instead of the real wallpaper. Never sample it.
 * Older systems are sampled only with an existing permission and an unambiguous viewport.
 * Wide/scrolling crops, live wallpapers, dimming and blur deliberately use a safe scrim.
 */
@Suppress("DEPRECATION")
private fun readWallpaperSamples(context: Context, aspect: Float): WallpaperSamples? {
    if (Build.VERSION.SDK_INT >= 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return null
    return try {
        val manager = WallpaperManager.getInstance(context)
        if (manager.wallpaperInfo != null) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use {
            BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            abs(bounds.outWidth.toFloat() / bounds.outHeight - aspect) > 0.01f) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 256) inSampleSize *= 2
        }
        val bitmap = manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use {
            BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, options)
        } ?: return null
        try {
            val w = minOf(16, bitmap.width)
            val h = minOf(32, bitmap.height)
            val minima = DoubleArray(w * h) { 1.0 }
            val maxima = DoubleArray(w * h)
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val index = (y * h / bitmap.height) * w + x * w / bitmap.width
                val value = relativeLuminance(bitmap.getPixel(x, y))
                minima[index] = minOf(minima[index], value)
                maxima[index] = maxOf(maxima[index], value)
            }
            WallpaperSamples(w, h, minima, maxima, aspect)
        } finally { bitmap.recycle() }
    } catch (_: SecurityException) { null }
      catch (_: RuntimeException) { null }
      catch (_: java.io.IOException) { null }
}

internal val LocalWallpaperSamples = staticCompositionLocalOf<WallpaperSamples?> { null }
internal val LocalWallpaperTextMode = staticCompositionLocalOf { SystemBarColors.Auto }
internal val LocalOnWallpaper = staticCompositionLocalOf { false }

@Composable
internal fun wallpaperSamplesAsState(colors: WallpaperColors, enabled: Boolean): State<WallpaperSamples?> {
    val context = LocalContext.current.applicationContext
    val config = LocalConfiguration.current
    val view = androidx.compose.ui.platform.LocalView.current
    val screenWidth = view.rootView.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
    val screenHeight = view.rootView.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
    val lifecycleOwner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    // Revalidate after returning from the system picker, including API 26 (no colors callback).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val cache = remember { WallpaperSampleCache() }
    val state = produceState<WallpaperSamples?>(null, colors, enabled, config, revision, screenWidth, screenHeight) {
        if (!enabled) { value = null; return@produceState }
        val aspect = screenWidth.toFloat() / screenHeight
        val key = withContext(Dispatchers.IO) {
            try {
                Triple(WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM),
                    aspect, ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE))
            } catch (_: RuntimeException) { null }
        }
        if (key == null) { value = null; return@produceState }
        if (cache.key == key) { value = cache.samples; return@produceState }
        value = null // Changed wallpaper: protect text until the new snapshot is available.
        delay(250) // Coalesce color notifications; no per-frame analysis.
        val result = withContext(Dispatchers.IO) {
            val snapshot = readWallpaperSamples(context, aspect)
            val currentId = try { WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM) }
                catch (_: RuntimeException) { -1 }
            if (currentId == key.first) snapshot else null
        }
        cache.key = key
        cache.samples = result
        value = result
    }
    // Disabled analysis must stop being used immediately, including theme/blur transitions.
    return if (enabled) state else rememberUpdatedState(null)
}

private class WallpaperSampleCache {
    var key: Triple<Int, Float, Int>? = null
    var samples: WallpaperSamples? = null
}

internal data class WallpaperContentStyle(
    val darkText: Boolean,
    val modifier: Modifier,
    val scrimAlpha: Float,
) {
    val color: Color get() = if (darkText) Color.Black else Color.White
}

/** Geometry changes only select cached cells; decoding and luminance analysis run on IO. */
@Composable
internal fun rememberWallpaperContentStyle(forcedDark: Boolean? = null): WallpaperContentStyle {
    val samples = LocalWallpaperSamples.current
    val mode = LocalWallpaperTextMode.current
    val rootView = androidx.compose.ui.platform.LocalView.current.rootView
    val viewport = Rect(0f, 0f, rootView.width.toFloat(), rootView.height.toFloat())
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var settledBounds by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(bounds) {
        delay(150)
        settledBounds = bounds
    }
    val forced = forcedDark ?: when (mode) {
        SystemBarColors.Dark -> true
        SystemBarColors.Light -> false
        else -> null
    }
    val settledChoice = remember(samples, settledBounds, viewport, forced) {
        // Until layout is known, use a scrim rather than guessing a local region.
        if (settledBounds == null) chooseContrast(forcedDark = forced)
        else samples?.choice(viewport.width, viewport.height, forced,
            settledBounds!!.left / viewport.width, settledBounds!!.top / viewport.height,
            settledBounds!!.right / viewport.width, settledBounds!!.bottom / viewport.height)
            ?: chooseContrast(forcedDark = forced)
    }
    // During scrolling/dragging, keep the last text polarity with a safe backing. Change
    // polarity at most once after geometry settles instead of following each frame's pixels.
    val choice = if (bounds != settledBounds)
        chooseContrast(forcedDark = forced ?: settledChoice.darkText) else settledChoice
    val backing = if (choice.darkText) Color.White else Color.Black
    return WallpaperContentStyle(choice.darkText, Modifier
        .onGloballyPositioned { bounds = it.boundsInWindow() }
        .background(backing.copy(alpha = choice.scrimAlpha)), choice.scrimAlpha)
}
