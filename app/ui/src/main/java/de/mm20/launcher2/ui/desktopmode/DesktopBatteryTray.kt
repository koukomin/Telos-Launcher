package de.mm20.launcher2.ui.desktopmode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private data class DesktopBatteryState(
    val percent: Int = 0,
    val charging: Boolean = false,
)

@Composable
internal fun DesktopBatteryTrayIcon() {
    val context = LocalContext.current
    val state by remember { batteryStateFlow(context) }.collectAsState(DesktopBatteryState())

    DesktopTrayIconButton(
        icon = getBatteryIcon(state),
        contentDescription = stringResource(R.string.desktop_mode_tray_battery),
        onClick = {},
    )
    Text(
        text = "${state.percent}%",
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(end = 8.dp),
    )
}

private fun getBatteryIcon(state: DesktopBatteryState): Int {
    if (state.charging) {
        return when (state.percent) {
            in 0..25 -> R.drawable.battery_charging_20_24px
            in 26..55 -> R.drawable.battery_charging_30_24px
            in 56..85 -> R.drawable.battery_charging_80_24px
            in 86..95 -> R.drawable.battery_charging_90_24px
            else -> R.drawable.battery_charging_full_24px
        }
    }
    return when (state.percent) {
        in 0..12 -> R.drawable.battery_0_bar_24px
        in 13..25 -> R.drawable.battery_1_bar_24px
        in 26..37 -> R.drawable.battery_2_bar_24px
        in 38..50 -> R.drawable.battery_3_bar_24px
        in 51..63 -> R.drawable.battery_4_bar_24px
        in 64..75 -> R.drawable.battery_5_bar_24px
        in 76..88 -> R.drawable.battery_6_bar_24px
        else -> R.drawable.battery_full_24px
    }
}

private fun batteryStateFlow(context: Context): Flow<DesktopBatteryState> = callbackFlow {
    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    fun stateFrom(intent: Intent?): DesktopBatteryState {
        intent ?: return DesktopBatteryState()
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return DesktopBatteryState(percent = percent, charging = charging)
    }

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            trySendBlocking(stateFrom(intent))
        }
    }
    val sticky = ContextCompat.registerReceiver(
        context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
    )
    trySendBlocking(stateFrom(sticky))

    awaitClose { context.unregisterReceiver(receiver) }
}
