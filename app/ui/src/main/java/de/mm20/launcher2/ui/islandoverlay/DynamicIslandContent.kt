package de.mm20.launcher2.ui.islandoverlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import de.mm20.launcher2.music.MusicService
import de.mm20.launcher2.music.PlaybackState
import de.mm20.launcher2.preferences.ui.DynamicIslandSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

sealed class IslandContent {
    data object Call : IslandContent()
    data class Timer(val remainingMs: Long) : IslandContent()
    data class Media(val title: String?, val artist: String?, val playing: Boolean) : IslandContent()
    data class Charging(val level: Int) : IslandContent()
}

/**
 * Resolves which single [IslandContent] the Dynamic Island pill should currently show, using the
 * same "highest score wins, null if nothing qualifies" mechanic as the clock widget's
 * PartProviders and the At a Glance widget - unlike At a Glance, there's no fallback baseline
 * here: the pill only appears at all while something is actually live.
 */
class DynamicIslandContentProvider(
    private val context: Context,
    private val settings: DynamicIslandSettings,
    private val callStateProvider: CallStateProvider,
    private val timerManager: TimerManager,
    private val musicService: MusicService,
) {
    private val callFlow: Flow<Boolean> = settings.showCalls.flatMapLatest { show ->
        if (show) callStateProvider.isInCall else flowOf(false)
    }

    private val mediaFlow: Flow<IslandContent.Media?> = combine(
        musicService.playbackState,
        musicService.title,
        musicService.artist,
    ) { state, title, artist ->
        if (state == PlaybackState.Playing) IslandContent.Media(title, artist, playing = true) else null
    }

    private val chargingFlow: Flow<IslandContent.Charging?> = callbackFlow {
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        if (batteryManager == null) {
            trySendBlocking(null)
            awaitClose {}
            return@callbackFlow
        }

        fun currentInfo(intent: Intent?): IslandContent.Charging? {
            val charging = intent?.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )?.let { it == BatteryManager.BATTERY_STATUS_CHARGING } ?: batteryManager.isCharging
            if (!charging) return null
            return IslandContent.Charging(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
        }

        trySendBlocking(currentInfo(null))
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySendBlocking(currentInfo(intent))
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        awaitClose { context.unregisterReceiver(receiver) }
    }

    val content: Flow<IslandContent?> = combine(
        callFlow,
        timerManager.state,
        mediaFlow,
        chargingFlow,
    ) { call, timer, media, charging ->
        val candidates = listOfNotNull(
            if (call) (IslandContent.Call as IslandContent) to CALL_SCORE else null,
            timer?.let { (IslandContent.Timer(it.remainingMs) as IslandContent) to TIMER_SCORE },
            media?.let { (it as IslandContent) to MEDIA_SCORE },
            charging?.let { (it as IslandContent) to CHARGING_SCORE },
        )
        candidates.maxByOrNull { it.second }?.first
    }

    companion object {
        private const val CALL_SCORE = 100
        private const val TIMER_SCORE = 80
        private const val MEDIA_SCORE = 50
        private const val CHARGING_SCORE = 20
    }
}
