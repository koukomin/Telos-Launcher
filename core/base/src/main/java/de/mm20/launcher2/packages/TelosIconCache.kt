package de.mm20.launcher2.packages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Single shared, downsampled application icon cache across Virtual Apps (ART Optimizer,
 * Smart Freeze Manager, Telos Store, Telos System).
 *
 * - Capped at 1/8th of the JVM max heap size (`LruCache<String, ImageBitmap>`).
 * - Downsamples AdaptiveIcons and oversized 512x512 Drawables strictly to [TARGET_ICON_PX] (`144x144` px)
 *   before converting to [ImageBitmap], preventing bitmap heap bloat.
 * - Backed by fast disk caching under `context.cacheDir/telos_icons_144`.
 * - Supports pinning Home Screen & Dock packages during memory trims ([trimHalf] / [evictUnpinned]).
 */
object TelosIconCache {

    const val TARGET_ICON_PX = 144
    private const val DISK_CACHE_FOLDER = "telos_icons_144"

    private val maxCacheBytes: Int = (Runtime.getRuntime().maxMemory() / 8L)
        .coerceIn(4L * 1024L * 1024L, 64L * 1024L * 1024L)
        .toInt()

    private val lock = Any()
    private val decodeGate = Semaphore(permits = 4)

    @Volatile
    private var pinnedPackages: Set<String> = emptySet()

    private val memoryCache = object : LruCache<String, ImageBitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.width * value.height * 4).coerceAtLeast(1_024)
        }
    }

    fun setPinnedPackages(packages: Set<String>) {
        pinnedPackages = packages.toSet()
    }

    fun getPinnedPackages(): Set<String> = pinnedPackages

    fun getCached(packageName: String, targetPx: Int = TARGET_ICON_PX): ImageBitmap? {
        val cacheKey = memoryKey(packageName, targetPx)
        return synchronized(lock) {
            memoryCache.get(cacheKey)
        }
    }

    suspend fun getOrLoad(
        context: Context,
        packageName: String,
        versionCode: Long = 0L,
        targetPx: Int = TARGET_ICON_PX,
    ): ImageBitmap? {
        val safePx = targetPx.coerceIn(32, 256)
        val memKey = memoryKey(packageName, safePx)
        synchronized(lock) {
            memoryCache.get(memKey)
        }?.let { return it }

        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            decodeGate.withPermit {
                synchronized(lock) {
                    memoryCache.get(memKey)
                }?.let { return@withPermit it }

                val diskFile = diskCacheFile(appContext, packageName, versionCode, safePx)
                val fromDisk = readBitmapFromDisk(diskFile)
                if (fromDisk != null) {
                    val imageBitmap = fromDisk.asImageBitmap()
                    synchronized(lock) {
                        memoryCache.put(memKey, imageBitmap)
                    }
                    return@withPermit imageBitmap
                }

                val drawable = runCatching {
                    appContext.packageManager.getApplicationIcon(packageName)
                }.getOrNull() ?: return@withPermit null

                val rendered = renderDownsampledBitmap(drawable, safePx)
                writeBitmapToDisk(appContext, packageName, diskFile, rendered)

                val imageBitmap = rendered.asImageBitmap()
                synchronized(lock) {
                    memoryCache.put(memKey, imageBitmap)
                }
                imageBitmap
            }
        }
    }

    /**
     * Trims the in-memory `LruCache` by 50% while preserving Home Screen & Dock [keepPinned] icons.
     */
    fun trimHalf(keepPinned: Set<String> = pinnedPackages) {
        synchronized(lock) {
            val currentSize = memoryCache.size()
            if (currentSize <= 0) return
            val targetBytes = currentSize / 2
            val snapshotKeys = memoryCache.snapshot().keys.toList()
            for (key in snapshotKeys) {
                if (memoryCache.size() <= targetBytes) break
                val pkg = key.substringBefore('|')
                if (pkg !in keepPinned) {
                    memoryCache.remove(key)
                }
            }
        }
    }

    /**
     * Evicts all unpinned icons from RAM under critical memory pressure, keeping only Home & Dock icons.
     */
    fun evictUnpinned(keepPinned: Set<String> = pinnedPackages) {
        synchronized(lock) {
            val snapshotKeys = memoryCache.snapshot().keys.toList()
            for (key in snapshotKeys) {
                val pkg = key.substringBefore('|')
                if (pkg !in keepPinned) {
                    memoryCache.remove(key)
                }
            }
        }
    }

    fun invalidate(packageName: String, context: Context? = null) {
        synchronized(lock) {
            val matching = memoryCache.snapshot().keys.filter { it.startsWith("$packageName|") }
            for (key in matching) {
                memoryCache.remove(key)
            }
        }
        if (context != null) {
            val dir = File(context.applicationContext.cacheDir, DISK_CACHE_FOLDER)
            val prefix = sanitizeFileToken(packageName) + "_"
            runCatching {
                dir.listFiles()?.forEach { file ->
                    if (file.name.startsWith(prefix)) {
                        file.delete()
                    }
                }
            }
        }
    }

    private fun renderDownsampledBitmap(drawable: Drawable, targetPx: Int): Bitmap {
        if (drawable is BitmapDrawable) {
            val src = drawable.bitmap
            if (src != null && !src.isRecycled) {
                if (src.width == targetPx && src.height == targetPx && src.config == Bitmap.Config.ARGB_8888) {
                    return src.copy(Bitmap.Config.ARGB_8888, false)
                }
                val scaled = Bitmap.createScaledBitmap(src, targetPx, targetPx, true)
                return if (scaled.config == Bitmap.Config.ARGB_8888) {
                    scaled
                } else {
                    val converted = scaled.copy(Bitmap.Config.ARGB_8888, false)
                    if (scaled !== src) scaled.recycle()
                    converted
                }
            }
        }

        val out = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val oldBounds = drawable.copyBounds()
        drawable.setBounds(0, 0, targetPx, targetPx)
        drawable.draw(canvas)
        drawable.bounds = oldBounds
        return out
    }

    private fun readBitmapFromDisk(file: File): Bitmap? {
        if (!file.exists() || file.length() <= 0L) return null
        return runCatching {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            FileInputStream(file).use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        }.getOrNull()
    }

    private fun writeBitmapToDisk(
        appContext: Context,
        packageName: String,
        targetFile: File,
        bitmap: Bitmap,
    ) {
        runCatching {
            val dir = targetFile.parentFile ?: File(appContext.cacheDir, DISK_CACHE_FOLDER)
            if (!dir.exists()) dir.mkdirs()

            // Remove stale cached versions for this package & target resolution
            val prefix = sanitizeFileToken(packageName) + "_"
            val suffix = "_${bitmap.width}.cache"
            dir.listFiles()?.forEach { existing ->
                if (existing.name.startsWith(prefix) && existing.name.endsWith(suffix) && existing != targetFile) {
                    existing.delete()
                }
            }

            val tmpFile = File(dir, "${targetFile.name}.tmp")
            FileOutputStream(tmpFile).use { out ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    Bitmap.CompressFormat.PNG
                }
                bitmap.compress(format, 100, out)
                out.flush()
            }
            if (!tmpFile.renameTo(targetFile)) {
                tmpFile.delete()
            }
        }
    }

    private fun diskCacheFile(
        appContext: Context,
        packageName: String,
        versionCode: Long,
        targetPx: Int,
    ): File {
        val dir = File(appContext.cacheDir, DISK_CACHE_FOLDER)
        val safePkg = sanitizeFileToken(packageName)
        return File(dir, "${safePkg}_${versionCode}_${targetPx}.cache")
    }

    private fun memoryKey(packageName: String, targetPx: Int): String = "$packageName|$targetPx"

    private fun sanitizeFileToken(raw: String): String {
        return buildString(raw.length) {
            for (ch in raw) {
                if (ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-') {
                    append(ch)
                } else {
                    append('_')
                }
            }
        }
    }
}
