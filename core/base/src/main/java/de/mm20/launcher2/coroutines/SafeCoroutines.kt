package de.mm20.launcher2.coroutines

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Exception handler that catches non-fatal exceptions in background coroutine scopes,
 * preventing app crashes while preserving background service execution.
 */
val nonFatalExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    // Non-fatal crash containment guard
    android.util.Log.e("TelosCrashGuard", "Caught non-fatal background coroutine exception", throwable)
}

/**
 * Safely launches a coroutine on the receiver [CoroutineScope] with automatic
 * exception containment.
 */
fun CoroutineScope.launchSafe(
    block: suspend CoroutineScope.() -> Unit
): Job {
    return launch(nonFatalExceptionHandler) {
        try {
            block()
        } catch (e: Throwable) {
            android.util.Log.e("TelosCrashGuard", "Handled non-fatal coroutine failure", e)
        }
    }
}
