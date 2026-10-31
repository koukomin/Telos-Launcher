package de.mm20.launcher2.ui.launcher.widgets.battery
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.utils.formatPercent
import de.mm20.launcher2.widgets.BatteryWidget
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Composable
fun BatteryWidget(widget: BatteryWidget) {
    val context = LocalContext.current
    val batteryInfo by remember(context) { batteryInfoFlow(context) }.collectAsState(null)
    val info = batteryInfo ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(getBatteryIcon(info)),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Column(
            modifier = Modifier.padding(start = 16.dp),
        ) {
            Text(
                text = formatPercent(info.level.toFloat()),
                style = MaterialTheme.typography.titleLarge,
            )
            if (info.charging) {
                Text(
                    text = info.fullIn?.let {
                        val minutes = (it / 60000).toInt()
                        pluralStringResource(
                            I18nR.plurals.battery_part_remaining_charge_time,
                            minutes,
                            minutes,
                        )
                    } ?: stringResource(I18nR.string.battery_part_charging),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = stringResource(I18nR.string.battery_widget_not_charging),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun getBatteryIcon(batteryInfo: BatteryWidgetInfo): Int {
    return if (batteryInfo.charging) {
        when (batteryInfo.level) {
            in 0..25 -> BaseR.drawable.battery_charging_20_24px
            in 26..55 -> BaseR.drawable.battery_charging_30_24px
            in 56..85 -> BaseR.drawable.battery_charging_80_24px
            in 86..95 -> BaseR.drawable.battery_charging_90_24px
            else -> BaseR.drawable.battery_charging_full_24px
        }
    } else {
        when (batteryInfo.level) {
            in 0..12 -> BaseR.drawable.battery_0_bar_24px
            in 13..25 -> BaseR.drawable.battery_1_bar_24px
            in 26..37 -> BaseR.drawable.battery_2_bar_24px
            in 38..50 -> BaseR.drawable.battery_3_bar_24px
            in 51..63 -> BaseR.drawable.battery_4_bar_24px
            in 64..75 -> BaseR.drawable.battery_5_bar_24px
            in 76..88 -> BaseR.drawable.battery_6_bar_24px
            else -> BaseR.drawable.battery_full_24px
        }
    }
}

private data class BatteryWidgetInfo(
    val level: Int,
    val charging: Boolean,
    val fullIn: Long?,
)

private fun batteryInfoFlow(context: Context): Flow<BatteryWidgetInfo> = callbackFlow {
    val batteryManager: BatteryManager = context.getSystemService() ?: run {
        close()
        return@callbackFlow
    }

    fun currentInfo(intent: Intent?) = BatteryWidgetInfo(
        level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
        charging = intent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        )?.let { it == BatteryManager.BATTERY_STATUS_CHARGING } ?: batteryManager.isCharging,
        fullIn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            batteryManager.computeChargeTimeRemaining().takeIf { it > 0 }
        } else null,
    )

    trySendBlocking(currentInfo(null))

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            trySendBlocking(currentInfo(intent))
        }
    }
    context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    awaitClose {
        context.unregisterReceiver(receiver)
    }
}
