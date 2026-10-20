package de.mm20.launcher2.ui.media.stremio

import android.content.Context
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.TreeSet

/** Process-local handle; each player process has a separate disk directory/lock. Obtain only on IO. */
internal object StremioStreamCache {
    private var cache: SimpleCache? = null
    private val evictor = BudgetEvictor()
    @Synchronized fun obtain(context: Context, bytes: Long): SimpleCache {
        evictor.maximum = bytes
        val instance = cache ?: SimpleCache(
            File(context.applicationContext.cacheDir, if (de.mm20.launcher2.base.ProcessInfo.isolatedPlayer)
                "stremio-http-isolated" else "stremio-http-main"),
            evictor, StandaloneDatabaseProvider(context.applicationContext),
        ).also { cache = it }
        evictor.trim(instance)
        return instance
    }

    /** Cache invokes listeners under its lock. Updating only the volatile budget avoids lock inversion. */
    private class BudgetEvictor : CacheEvictor {
        @Volatile var maximum = 5L * 1024 * 1024 * 1024
        private val spans = TreeSet(compareBy<CacheSpan>({ it.lastTouchTimestamp }, { it.key }, { it.position }))
        private var size = 0L
        override fun requiresCacheSpanTouches() = true
        override fun onCacheInitialized() = Unit
        override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
            if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
        }
        override fun onSpanAdded(cache: Cache, span: CacheSpan) {
            if (spans.add(span)) size += span.length
            evict(cache, 0)
        }
        override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
            if (spans.remove(span)) size -= span.length
        }
        override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
            onSpanRemoved(cache, oldSpan)
            onSpanAdded(cache, newSpan)
        }
        fun trim(cache: Cache) { synchronized(cache) { evict(cache, 0) } }
        private fun evict(cache: Cache, incoming: Long) {
            while (size + incoming > maximum && spans.isNotEmpty()) cache.removeSpan(spans.first())
        }
    }
}
