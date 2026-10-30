package de.mm20.launcher2.wallpapers

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

data class VideoGlassRequest(val radius: Int, val widthDp: Int, val heightDp: Int)

/** Shared bounded demand; neither clients nor published frames retain an Activity. */
class VideoGlass internal constructor() {
    private val clients = LinkedHashMap<Any, List<VideoGlassRequest>>()
    private val mutableRequests = MutableStateFlow<List<VideoGlassRequest>>(emptyList())
    internal val requests = mutableRequests.asStateFlow()
    private val mutableFrames = MutableStateFlow<Map<VideoGlassRequest, Bitmap>>(emptyMap())
    val frames = mutableFrames.asStateFlow()
    private var owner: Any? = null

    @Synchronized fun register(token: Any, requests: List<VideoGlassRequest>) {
        clients[token] = requests.filter { it.radius > 0 && it.widthDp > 0 && it.heightDp > 0 }.take(2)
        updateRequests()
    }
    @Synchronized fun unregister(token: Any) { clients.remove(token); updateRequests() }
    private fun updateRequests() {
        mutableRequests.value = clients.values.toList().asReversed().flatten().distinct().take(4)
        mutableFrames.value = mutableFrames.value.filterKeys { it in mutableRequests.value }
    }
    @Synchronized internal fun claim(token: Any) { owner = token; mutableFrames.value = emptyMap() }
    @Synchronized internal fun clear(token: Any) {
        if (owner === token) { owner = null; mutableFrames.value = emptyMap() }
    }
    @Synchronized internal fun publish(token: Any, frames: Map<VideoGlassRequest, Bitmap>) {
        if (owner === token) mutableFrames.value = frames.filterKeys { it in mutableRequests.value }
    }
}

internal fun videoGlassSize(width: Int, height: Int): Pair<Int, Int> {
    require(width > 0 && height > 0)
    val scale = minOf(1f, 192f / maxOf(width, height))
    return (width * scale).roundToInt().coerceIn(1, 192) to
        (height * scale).roundToInt().coerceIn(1, 192)
}

private val videoCopyInFlight = AtomicBoolean(false)

/** One asynchronous readback at a time, from our own wallpaper surface only. */
internal suspend fun copyVideoGlass(holder: SurfaceHolder): Bitmap? {
    val rect = holder.surfaceFrame
    if (!holder.surface.isValid || rect.width() <= 0 || rect.height() <= 0) return null
    val (width, height) = videoGlassSize(rect.width(), rect.height())
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    if (!videoCopyInFlight.compareAndSet(false, true)) { bitmap.recycle(); return null }
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(holder.surface, bitmap, { result ->
                videoCopyInFlight.set(false)
                // Native copying cannot be cancelled: recycle only after its callback.
                if (!continuation.isActive || result != PixelCopy.SUCCESS) {
                    bitmap.recycle()
                    if (continuation.isActive) continuation.resume(null)
                } else continuation.resume(bitmap) { _, abandoned, _ -> abandoned.recycle() }
            }, Handler(Looper.getMainLooper()))
        } catch (_: IllegalArgumentException) {
            videoCopyInFlight.set(false)
            bitmap.recycle()
            if (continuation.isActive) continuation.resume(null)
        }
    }
}

internal suspend fun blurVideoGlass(bitmap: Bitmap, requests: List<VideoGlassRequest>): Map<VideoGlassRequest, Bitmap> =
    try {
        withContext(Dispatchers.Default) {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val radii = HashMap<Int, Bitmap>()
            requests.associateWith { request ->
                val radius = GlassPixels.sourceRadius(request.radius, width, height, request.widthDp, request.heightDp)
                radii.getOrPut(radius) {
                    Bitmap.createBitmap(GlassPixels.blur(pixels, width, height, radius), width, height, Bitmap.Config.ARGB_8888)
                }
            }
        }
    } finally { bitmap.recycle() }
