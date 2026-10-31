package de.mm20.launcher2.ui.launcher.widgets.clock.parts
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.weather.AnimatedWeatherIcon
import de.mm20.launcher2.ui.component.weather.WeatherIconDefaults
import de.mm20.launcher2.ui.launcher.widgets.weather.weatherIconById
import de.mm20.launcher2.ui.locals.LocalMeasurementSystem
import de.mm20.launcher2.ui.utils.formatTemperature
import de.mm20.launcher2.weather.WeatherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Shows the current temperature and condition icon alongside the clock, using the same
 * forecast data and formatting as the standalone Weather widget. Falls back to a "grant
 * location" prompt when the part is enabled but has no data yet, instead of disappearing. */
class WeatherPartProvider : PartProvider, KoinComponent {
    private val weatherRepository: WeatherRepository by inject()
    private val permissionsManager: PermissionsManager by inject()

    // Constant and higher than DatePartProvider's 1: this part is always shown once enabled,
    // whether it has forecast data or needs to fall back to a "grant location" prompt. Ranking
    // on forecast availability made the whole dynamic zone row vanish whenever there was no data
    // yet; ranking equal to Date's made getActivePart's maxByOrNull tie-break always pick Date
    // instead (it's added to the provider list first), so enabling weather appeared to do nothing.
    override fun getRanking(context: Context): Flow<Int> = flow {
        emit(2)
    }

    @Composable
    override fun Component(compactLayout: Boolean) {
        val context = LocalContext.current
        val forecasts by weatherRepository.getForecasts(limit = 1).collectAsState(emptyList())
        val forecast = forecasts.firstOrNull()
        val hasLocationPermission by permissionsManager.hasPermission(PermissionGroup.Location)
            .collectAsState(true)
        val measurementSystem = LocalMeasurementSystem.current

        if (forecast == null && !hasLocationPermission) {
            Row(
                modifier = Modifier.clickable {
                    val activity = context as? AppCompatActivity ?: return@clickable
                    permissionsManager.requestPermission(activity, PermissionGroup.Location)
                },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(BaseR.drawable.location_on_24px),
                    contentDescription = null,
                )
                Text(
                    text = stringResource(I18nR.string.clockwidget_weather_part_grant_location),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            return
        }

        if (forecast == null) return

        val weatherApp = remember {
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_WEATHER),
                0,
            )
        }

        Row(
            modifier = Modifier.clickable {
                val info = weatherApp?.activityInfo ?: return@clickable
                context.tryStartActivity(
                    Intent().setComponent(ComponentName(info.packageName, info.name)),
                )
            },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedWeatherIcon(
                icon = weatherIconById(forecast.icon),
                night = forecast.night,
                colors = WeatherIconDefaults.vividColors(),
                size = 56.dp,
            )
            Text(
                text = formatTemperature(context, forecast.temperature.toFloat(), measurementSystem),
                style = MaterialTheme.typography.displaySmall,
            )
        }
    }
}
