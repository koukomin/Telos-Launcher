package de.mm20.launcher2.store.installer

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class InstallEvent(val packageName: String, val success: Boolean, val details: String)

/** Session callbacks are asynchronous; failures must also release the Store's pending row. */
object InstallEvents {
    private val mutable = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 64)
    val events = mutable.asSharedFlow()
    fun report(event: InstallEvent) { mutable.tryEmit(event) }
}
