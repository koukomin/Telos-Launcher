package de.mm20.launcher2.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.SecureRandom

/**
 * Asynchronous SecureRandom Entropy Pool Warmup Helper for Telos Launcher.
 * Pre-seeds OS entropy pool on background threads during cold-start to eliminate 100ms
 * security challenge delays during AppLock or encryption tasks.
 */
object TelosEntropyWarmup {

    @Volatile
    private var isWarmedUp = false

    /**
     * Asynchronously seeds SecureRandom on [Dispatchers.Default].
     */
    fun warmupAsync(scope: CoroutineScope) {
        if (isWarmedUp) return

        scope.launch(Dispatchers.Default) {
            try {
                val random = SecureRandom()
                val seed = ByteArray(32)
                random.nextBytes(seed)
                isWarmedUp = true
            } catch (e: Exception) {
                // Ignore non-fatal entropy warmup errors
            }
        }
    }
}
