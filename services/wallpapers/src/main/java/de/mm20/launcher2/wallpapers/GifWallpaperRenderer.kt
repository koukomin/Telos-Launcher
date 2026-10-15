package de.mm20.launcher2.wallpapers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceHolder
import de.mm20.launcher2.preferences.VideoWallpaperScalingMode
import de.mm20.launcher2.preferences.ui.VideoWallpaperTransforms
import java.io.File
import kotlin.math.min

/** One visible engine, one decoded animation; never cache every frame as a bitmap. */
internal class GifWallpaperRenderer(
    private val holder: SurfaceHolder,
    private val drawable: Drawable?,
    private val movie: Movie?,
    private val settings: () -> VideoWallpaperTransforms,
    private val offset: () -> Float,
    private val speed: () -> Float,
) : Drawable.Callback {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var started = 0L
    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            render()
            if (movie != null) handler.postDelayed(this, 33)
        }
    }

    fun start() {
        if (running) return
        running = true
        started = SystemClock.uptimeMillis()
        drawable?.callback = this
        if (Build.VERSION.SDK_INT >= 28) (drawable as? AnimatedImageDrawable)?.start()
        frame.run()
    }

    fun close() {
        running = false
        if (Build.VERSION.SDK_INT >= 28) (drawable as? AnimatedImageDrawable)?.stop()
        drawable?.callback = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun render() {
        if (!running || !holder.surface.isValid) return
        var canvas: Canvas? = null
        try {
            canvas = holder.lockCanvas() ?: return
            canvas.drawColor(Color.BLACK)
            val t = settings()
            val width = drawable?.intrinsicWidth ?: movie?.width() ?: return
            val height = drawable?.intrinsicHeight ?: movie?.height() ?: return
            if (width <= 0 || height <= 0) return
            val xScale = canvas.width.toFloat() / width
            val yScale = canvas.height.toFloat() / height
            val base = when (t.scalingMode) {
                VideoWallpaperScalingMode.Fit -> minOf(xScale, yScale)
                VideoWallpaperScalingMode.Fill -> maxOf(xScale, yScale)
                VideoWallpaperScalingMode.Stretch -> 1f
            }
            val sx = (if (t.scalingMode == VideoWallpaperScalingMode.Stretch) xScale else base) * t.zoom
            val sy = (if (t.scalingMode == VideoWallpaperScalingMode.Stretch) yScale else base) * t.zoom
            val parallax = if (t.parallax) (offset() - 0.5f) * t.parallaxStrength else 0f
            canvas.save()
            canvas.translate((canvas.width - width * sx) / 2 + (t.positionX + parallax) * canvas.width / 2,
                (canvas.height - height * sy) / 2 + t.positionY * canvas.height / 2)
            canvas.scale(sx, sy)
            if (drawable != null) { drawable.setBounds(0, 0, width, height); drawable.draw(canvas) }
            else movie?.let {
                val duration = it.duration().takeIf { d -> d > 0 } ?: 1000
                it.setTime((((SystemClock.uptimeMillis() - started) * speed()) % duration).toInt())
                it.draw(canvas, 0f, 0f)
            }
            canvas.restore()
            if (t.brightness < 1f) canvas.drawColor(Color.argb(((1f - t.brightness) * 255).toInt().coerceIn(0, 255), 0, 0, 0))
            else if (t.brightness > 1f) canvas.drawColor(Color.argb(((t.brightness - 1f) * 255).toInt().coerceIn(0, 255), 255, 255, 255))
        } catch (_: IllegalArgumentException) {
            // Surface can disappear between the validity check and lockCanvas.
        } finally { canvas?.let { runCatching { holder.unlockCanvasAndPost(it) } } }
    }

    override fun invalidateDrawable(who: Drawable) { if (running) render() }
    override fun scheduleDrawable(who: Drawable, what: Runnable, whenMillis: Long) { if (running) handler.postAtTime(what, whenMillis) }
    override fun unscheduleDrawable(who: Drawable, what: Runnable) { handler.removeCallbacks(what) }

    companion object {
        fun isGif(file: File): Boolean = file.inputStream().use { input ->
            val header = ByteArray(6)
            input.read(header) == 6 && String(header, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")
        }

        /** Header and file-size limits apply before invoking a native decoder. */
        fun create(file: File, holder: SurfaceHolder, settings: () -> VideoWallpaperTransforms,
            offset: () -> Float, speed: () -> Float): GifWallpaperRenderer {
            require(file.length() in 10..32L * 1024 * 1024) { "GIF exceeds the wallpaper size limit" }
            val header = file.inputStream().use { input -> ByteArray(10).also { require(input.read(it) == 10) } }
            val width = (header[6].toInt() and 255) or ((header[7].toInt() and 255) shl 8)
            val height = (header[8].toInt() and 255) or ((header[9].toInt() and 255) shl 8)
            require(width in 1..4096 && height in 1..4096 && width.toLong() * height <= 4_194_304)
            val drawable = if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val ratio = min(1f, 2048f / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize(maxOf(1, (info.size.width * ratio).toInt()), maxOf(1, (info.size.height * ratio).toInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
            }.also { (it as? AnimatedImageDrawable)?.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE } else null
            @Suppress("DEPRECATION")
            val movie = if (drawable == null) requireNotNull(Movie.decodeFile(file.path)) else null
            return GifWallpaperRenderer(holder, drawable, movie, settings, offset, speed)
        }
    }
}
