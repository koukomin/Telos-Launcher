package de.mm20.launcher2.network.impl

import android.net.TrafficStats
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real-Time Network Speedometer & Traffic Monitor for Telos Launcher.
 * Calculates live download and upload throughput (KB/s or MB/s)
 * off the main thread with low overhead.
 */
object TelosNetworkSpeedometer {

    private const val TAG = "TelosNetworkSpeedometer"

    data class SpeedState(
        val downloadSpeedBytesPerSec: Long = 0L,
        val uploadSpeedBytesPerSec: Long = 0L,
        val formattedDownload: String = "0 KB/s",
        val formattedUpload: String = "0 KB/s"
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isMonitoring = AtomicBoolean(false)
    private var monitorJob: Job? = null

    private val _speedState = MutableStateFlow(SpeedState())
    val speedState: StateFlow<SpeedState> = _speedState.asStateFlow()

    fun startMonitoring() {
        if (!isMonitoring.compareAndSet(false, true)) return

        monitorJob = scope.launch {
            var lastRxBytes = TrafficStats.getTotalRxBytes()
            var lastTxBytes = TrafficStats.getTotalTxBytes()
            var lastTimeMs = System.currentTimeMillis()

            while (isActive) {
                delay(1_000L)
                val nowMs = System.currentTimeMillis()
                val currentRxBytes = TrafficStats.getTotalRxBytes()
                val currentTxBytes = TrafficStats.getTotalTxBytes()

                val durationSec = ((nowMs - lastTimeMs) / 1000.0).coerceAtLeast(1.0)

                val rxDiff = (currentRxBytes - lastRxBytes).coerceAtLeast(0L)
                val txDiff = (currentTxBytes - lastTxBytes).coerceAtLeast(0L)

                val rxRate = (rxDiff / durationSec).toLong()
                val txRate = (txDiff / durationSec).toLong()

                lastRxBytes = currentRxBytes
                lastTxBytes = currentTxBytes
                lastTimeMs = nowMs

                _speedState.value = SpeedState(
                    downloadSpeedBytesPerSec = rxRate,
                    uploadSpeedBytesPerSec = txRate,
                    formattedDownload = formatSpeed(rxRate),
                    formattedUpload = formatSpeed(txRate)
                )
            }
        }
        Log.i(TAG, "Network Speedometer monitoring started")
    }

    fun stopMonitoring() {
        if (!isMonitoring.compareAndSet(true, false)) return
        monitorJob?.cancel()
        monitorJob = null
        _speedState.value = SpeedState()
        Log.i(TAG, "Network Speedometer monitoring stopped")
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> "${bytesPerSec / 1024} KB/s"
            else -> "$bytesPerSec B/s"
        }
    }
}
