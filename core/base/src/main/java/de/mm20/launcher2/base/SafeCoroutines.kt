package de.mm20.launcher2.base

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * An exception that nobody catches in a coroutine ends the whole process, the launcher included.
 * Scopes of background features of Telos apps use this handler instead, so a failure in such a task
 * is logged and the launcher goes on.
 */
val containedExceptionHandler = CoroutineExceptionHandler { _, e ->
    Log.e("Telos", "A background task failed and was contained", e)
}

/** A scope for background work whose failures must not take the launcher down. */
fun containedScope(dispatcher: CoroutineDispatcher): CoroutineScope =
    CoroutineScope(SupervisorJob() + dispatcher + containedExceptionHandler)

/** Runs [block], logs any exception and returns null instead of letting it escape. */
inline fun <T> contained(what: String, block: () -> T): T? = try {
    block()
} catch (e: Exception) {
    Log.e("Telos", "$what failed and was contained", e)
    null
}
