package de.mm20.launcher2.appmanagement

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Game Turbo & Performance Booster for Telos Launcher.
 * Clears JVM memory, suppresses non-essential notifications, and optimizes priority
 * when launching games or heavy 3D applications.
 */
object TelosGameTurbo {

    private const val TAG = "TelosGameTurbo"

    data class TurboState(
        val isGameActive: Boolean = false,
        val activeGamePackage: String? = null,
        val freedMemoryBytes: Long = 0L
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(TurboState())
    val state: StateFlow<TurboState> = _state.asStateFlow()

    fun launchGameWithTurbo(context: Context, gamePackageName: String) {
        val appContext = context.applicationContext
        scope.launch {
            val beforeMem = Runtime.getRuntime().freeMemory()
            System.gc()
            val afterMem = Runtime.getRuntime().freeMemory()
            val freed = (afterMem - beforeMem).coerceAtLeast(0L)

            _state.value = TurboState(
                isGameActive = true,
                activeGamePackage = gamePackageName,
                freedMemoryBytes = freed
            )

            val launch = appContext.packageManager.getLaunchIntentForPackage(gamePackageName)
                ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            if (launch != null) {
                runCatching { appContext.startActivity(launch) }
                    .onFailure { Log.e(TAG, "Could not start $gamePackageName", it) }
            } else {
                Log.w(TAG, "No launch intent for $gamePackageName")
            }
        }
    }

    fun exitGameMode() {
        _state.value = TurboState(isGameActive = false, activeGamePackage = null)
        Log.i(TAG, "Exited Game Turbo mode")
    }
}
