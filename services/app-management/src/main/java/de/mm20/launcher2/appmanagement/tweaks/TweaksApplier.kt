package de.mm20.launcher2.appmanagement.tweaks

import android.content.Context
import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell
import de.mm20.launcher2.preferences.TweaksGroup
import de.mm20.launcher2.preferences.tweaks.TweaksSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object TweaksApplier : KoinComponent {
    private const val TAG = "TweaksApplier"
    private val settings: TweaksSettings by inject()

    fun commands(group: TweaksGroup): List<String> = buildList {
        if (group.clockSeconds) add("settings put secure clock_seconds 1")
        if (group.peakRefresh120) add("settings put system peak_refresh_rate 120.0")
        if (group.disableSafeVolume) add("settings put global audio_safe_volume_state 2")
        if (group.callVibrateConnect) add("settings put system vibrate_on_call_connected 1")
        if (group.mobileDataAlwaysOn) add("settings put global mobile_data_always_on 1")
        if (group.alwaysOnDisplay) add("settings put secure doze_always_on 1")
    }

    suspend fun applySavedOnBoot(context: Context) = withContext(Dispatchers.IO) {
        val group = settings.data.first()
        if (!group.persistOnBoot) return@withContext
        if (!PrivilegedShell.available()) {
            delay(8_000)
        }
        if (!PrivilegedShell.available()) {
            Log.w(TAG, "no root/shizuku for boot tweaks")
            return@withContext
        }
        for (cmd in commands(group)) {
            val r = PrivilegedShell.run(cmd)
            if (r == null || r.exitCode != 0) Log.w(TAG, "boot tweak failed: $cmd")
        }
    }
}
