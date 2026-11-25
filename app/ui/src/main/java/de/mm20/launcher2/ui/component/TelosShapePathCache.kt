package de.mm20.launcher2.ui.component

import androidx.compose.ui.graphics.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Pre-computed Path Cache for Telos Custom Icon Shapes (Hexagon, Pentagon, Teardrop, Pebble, Squircle).
 * Caches Compose [Path] instances by size and shape type to eliminate draw-frame geometry allocations.
 */
object TelosShapePathCache {

    private val pathCache = ConcurrentHashMap<String, Path>()

    /**
     * Retrieves or creates a cached [Path] for a given shape key and dimensions.
     */
    fun getOrCreatePath(shapeKey: String, widthPx: Float, heightPx: Float, builder: (Path) -> Unit): Path {
        val cacheKey = "$shapeKey:$widthPx:$heightPx"
        return pathCache.getOrPut(cacheKey) {
            Path().apply { builder(this) }
        }
    }

    /**
     * Flushes the shape path cache (e.g. on memory trim or screen density changes).
     */
    fun clear() {
        pathCache.clear()
    }
}
