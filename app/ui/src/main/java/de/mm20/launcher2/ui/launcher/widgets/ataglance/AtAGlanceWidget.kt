package de.mm20.launcher2.ui.launcher.widgets.ataglance

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.LocalTime
import de.mm20.launcher2.ui.locals.LocalMeasurementSystem
import de.mm20.launcher2.ui.utils.formatPercent
import de.mm20.launcher2.ui.utils.formatTemperature
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.widgets.AtAGlanceWidget

@Composable
fun AtAGlanceWidget(widget: AtAGlanceWidget) {
    val viewModel: AtAGlanceWidgetVM = viewModel()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val measurementSystem = LocalMeasurementSystem.current
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (val c = content) {
            null -> {
                Text(
                    text = stringResource(R.string.at_a_glance_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is GlanceContent.Weather -> {
                Icon(
                    painter = painterResource(weatherIcon(c.forecast.icon)),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = formatTemperature(context, c.forecast.temperature.toFloat(), measurementSystem),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = c.forecast.condition,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            is GlanceContent.Calendar -> {
                Icon(
                    painter = painterResource(R.drawable.today_24px),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = c.event.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val start = c.event.startTime
                    val now = LocalTime.current
                    val subtitle = if (start != null && start > now) {
                        DateUtils.getRelativeTimeSpanString(
                            start, now, DateUtils.MINUTE_IN_MILLIS
                        ).toString()
                    } else {
                        stringResource(R.string.at_a_glance_event_now)
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            is GlanceContent.Battery -> {
                Icon(
                    painter = painterResource(batteryIcon(c.level, c.charging)),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = formatPercent(c.level.toFloat()),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = if (c.charging) {
                            c.fullIn?.let {
                                val minutes = (it / 60000).toInt()
                                pluralStringResource(
                                    R.plurals.battery_part_remaining_charge_time,
                                    minutes,
                                    minutes,
                                )
                            } ?: stringResource(R.string.at_a_glance_battery_charging)
                        } else {
                            stringResource(R.string.at_a_glance_battery_low)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun weatherIcon(id: Int): Int {
    return when (id) {
        Forecast.CLEAR -> R.drawable.light_mode_24px
        Forecast.LIGHT_RAIN, Forecast.RAIN, Forecast.HEAVY_RAIN, Forecast.SLEET, Forecast.HAIL ->
            R.drawable.rainy_20px
        Forecast.SNOW, Forecast.EXTREME_COLD -> R.drawable.ac_unit_24px
        else -> R.drawable.cloud_20px
    }
}

private fun batteryIcon(level: Int, charging: Boolean): Int {
    return if (charging) {
        when (level) {
            in 0..25 -> R.drawable.battery_charging_20_24px
            in 26..55 -> R.drawable.battery_charging_30_24px
            in 56..85 -> R.drawable.battery_charging_80_24px
            in 86..95 -> R.drawable.battery_charging_90_24px
            else -> R.drawable.battery_charging_full_24px
        }
    } else {
        when (level) {
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
}
