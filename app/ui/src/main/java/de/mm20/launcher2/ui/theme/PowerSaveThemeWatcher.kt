package de.mm20.launcher2.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.AmoledPresetId
import de.mm20.launcher2.themes.CyberpunkPresetId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * While Battery Saver is on, switch colors to the AMOLED preset. When it turns off, restore the
 * previous theme. If the current theme is already AMOLED or Cyberpunk, nothing changes.
 */
class PowerSaveThemeWatcher(
    private val context: Context,
    private val uiSettings: UiSettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action != PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) return
            scope.launch { apply() }
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        scope.launch { apply() }
    }

    private suspend fun apply() {
        val ui = uiSettings.snapshotUi.first()
        if (!ui.uiAmoledOnPowerSave) {
            if (ui.uiAmoledPowerSaveEngaged) restore(ui.uiColorsIdBeforePowerSave)
            return
        }
        val saver = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        val current = ui.uiColorsId
        val alreadyDark = current == AmoledPresetId || current == CyberpunkPresetId
        if (saver) {
            if (ui.uiAmoledPowerSaveEngaged || alreadyDark) return
            uiSettings.replacePowerSaveTheme(true, current, AmoledPresetId)
        } else if (ui.uiAmoledPowerSaveEngaged) {
            restore(ui.uiColorsIdBeforePowerSave)
        }
    }

    private fun restore(saved: UUID) {
        val back = if (saved != UUID(0L, 0L)) saved else null
        uiSettings.replacePowerSaveTheme(false, UUID(0L, 0L), back)
    }
}
