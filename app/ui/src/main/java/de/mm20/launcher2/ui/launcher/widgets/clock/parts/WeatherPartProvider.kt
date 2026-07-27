package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.component.weather.WeatherIcon
import de.mm20.launcher2.ui.component.weather.WeatherIconDefaults
import de.mm20.launcher2.ui.launcher.widgets.weather.weatherIconById
import de.mm20.launcher2.ui.locals.LocalMeasurementSystem
import de.mm20.launcher2.ui.utils.formatTemperature
import de.mm20.launcher2.weather.WeatherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Shows the current temperature and condition icon alongside the clock, using the same
 * forecast data and formatting as the standalone Weather widget. */
class WeatherPartProvider : PartProvider, KoinComponent {
    private val weatherRepository: WeatherRepository by inject()

    override fun getRanking(context: Context): Flow<Int> {
        return weatherRepository.getForecasts(limit = 1).map { if (it.isNotEmpty()) 1 else 0 }
    }

    @Composable
    override fun Component(compactLayout: Boolean) {
        val context = LocalContext.current
        val forecasts by weatherRepository.getForecasts(limit = 1).collectAsState(emptyList())
        val forecast = forecasts.firstOrNull() ?: return
        val measurementSystem = LocalMeasurementSystem.current

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeatherIcon(
                icon = weatherIconById(forecast.icon),
                night = forecast.night,
                colors = WeatherIconDefaults.colors(LocalContentColor.current),
            )
            Text(
                text = formatTemperature(context, forecast.temperature.toFloat(), measurementSystem),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
