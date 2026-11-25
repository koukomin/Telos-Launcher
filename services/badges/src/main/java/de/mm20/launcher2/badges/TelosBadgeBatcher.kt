package de.mm20.launcher2.badges

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn

/**
 * Notification Badge Update Batcher for Telos Launcher.
 * Batches rapid badge updates (missed calls, unread SMS, store update counts)
 * into a 50ms window to eliminate Compose UI recomposition thrashing.
 */
object TelosBadgeBatcher {

    /**
     * Debounces and batches badge update emissions within a [timeoutMs] window.
     */
    @OptIn(FlowPreview::class)
    fun <T> batchBadgeUpdates(
        flow: Flow<T>,
        scope: CoroutineScope,
        initialValue: T,
        timeoutMs: Long = 50L
    ) = flow.debounce(timeoutMs)
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = initialValue
        )
}
