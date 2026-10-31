package de.mm20.launcher2.coroutines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Bounded dispatchers so icon baking and disk/database scans cannot occupy every IO/Default
 * thread and starve Compose or Binder replies on the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object TelosDispatchers {
    val Icon = Dispatchers.Default.limitedParallelism(4)
    val Disk = Dispatchers.IO.limitedParallelism(4)
}
