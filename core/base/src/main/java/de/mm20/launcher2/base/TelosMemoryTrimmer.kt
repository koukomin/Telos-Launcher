package de.mm20.launcher2.base

import android.content.ComponentCallbacks2
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Low-Memory Emergency Cache Trimmer for Telos Launcher.
 * Listens to ComponentCallbacks2.onTrimMemory system warnings and triggers immediate cache release callbacks
 * to prevent Low Memory Kills (LMK).
 */
object TelosMemoryTrimmer {

    private val trimCallbacks = CopyOnWriteArrayList<() -> Unit>()

    fun registerCacheCallback(callback: () -> Unit) {
        trimCallbacks.addIfAbsent(callback)
    }

    fun unregisterCacheCallback(callback: () -> Unit) {
        trimCallbacks.remove(callback)
    }

    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            trimCallbacks.forEach { callback ->
                try {
                    callback()
                } catch (e: Exception) {
                    // Ignore non-fatal cache trim errors
                }
            }
        }
    }
}
