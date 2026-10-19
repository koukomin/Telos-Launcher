package de.mm20.launcher2.ui.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface

/** Bounded manual horizontal panorama: equal-height slices, 20% overlapping feathered seams. */
internal object CameraPanorama {
    fun stitch(context: Context, sources: List<Uri>): Uri {
        require(sources.size in 2..8)
        val images = mutableListOf<Bitmap>()
        var output: Bitmap? = null
        try {
            sources.forEach { uri ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, options) }
                require(options.outWidth > 0 && options.outHeight > 0) { "Invalid image dimensions" }
                var sample = 1
                while (maxOf(options.outWidth, options.outHeight) / sample > 1024) sample *= 2
                val bitmap = context.contentResolver.openInputStream(uri)!!.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: error("Image decode failed")
                try {
                    val exif = context.contentResolver.openInputStream(uri)!!.use { ExifInterface(it) }
                    val matrix = Matrix().apply { postRotate(exif.rotationDegrees.toFloat()); if (exif.isFlipped) postScale(-1f, 1f) }
                    val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    images.add(oriented)
                    if (oriented !== bitmap) bitmap.recycle()
                } catch (error: Throwable) { bitmap.recycle(); throw error }
            }
            var height = images.minOf { it.height }.coerceAtMost(1024)
            fun widthsAt(value: Int) = images.map { (it.width.toLong() * value / it.height).toInt().coerceAtLeast(1) }
            val initialWidths = widthsAt(height)
            val initialWidth = initialWidths.sum() - initialWidths.zipWithNext { left, right -> minOf(left, right) / 5 }.sum()
            val pixels = initialWidth.toLong() * height
            if (pixels > 8_000_000) height = (height * kotlin.math.sqrt(8_000_000.0 / pixels)).toInt().coerceAtLeast(1)
            val widths = widthsAt(height)
            val overlaps = widths.zipWithNext { left, right -> minOf(left, right) / 5 }
            val width = widths.sum() - overlaps.sum()
            require(width.toLong() * height <= 8_000_000)
            output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            var x = 0
            images.forEachIndexed { index, image ->
                val targetWidth = widths[index]
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                val overlap = overlaps.getOrNull(index - 1) ?: 0
                if (overlap == 0) canvas.drawBitmap(image, null, Rect(x, 0, x + targetWidth, height), paint)
                else {
                    for (column in 0 until overlap) {
                        paint.alpha = (255f * column / overlap).toInt()
                        val sourceX = column * image.width / targetWidth
                        canvas.drawBitmap(image, Rect(sourceX, 0, (sourceX + (image.width / targetWidth).coerceAtLeast(1)).coerceAtMost(image.width), image.height), Rect(x + column, 0, x + column + 1, height), paint)
                    }
                    paint.alpha = 255
                    canvas.drawBitmap(image, Rect(overlap * image.width / targetWidth, 0, image.width, image.height), Rect(x + overlap, 0, x + targetWidth, height), paint)
                }
                x += targetWidth - (overlaps.getOrNull(index) ?: 0)
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Telos_Panorama_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Telos"); put(MediaStore.Images.Media.IS_PENDING, 1) }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Media storage unavailable")
            try {
                context.contentResolver.openOutputStream(uri)!!.use { check(output!!.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
                if (Build.VERSION.SDK_INT >= 29) context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                return uri
            } catch (error: Exception) { context.contentResolver.delete(uri, null, null); throw error }
        } finally { images.forEach { it.recycle() }; output?.recycle() }
    }
}
