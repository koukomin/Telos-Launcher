package de.mm20.launcher2.applock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs [IntruderPhotoManager.deleteExpired] once per process start, enforcing the retention
 * period even if the photo gallery screen is never opened. */
internal class IntruderPhotoCleanup(intruderPhotoManager: IntruderPhotoManager) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            intruderPhotoManager.deleteExpired()
        }
    }
}
