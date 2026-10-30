package de.mm20.launcher2.ui.weather

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.preferences.weather.WeatherLocation
import de.mm20.launcher2.preferences.weather.WeatherSettings
import de.mm20.launcher2.weather.DailyForecast
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.weather.WeatherRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WeatherViewModel : ViewModel(), KoinComponent {

    private val weatherRepository: WeatherRepository by inject()
    private val weatherSettings: WeatherSettings by inject()

    val location: StateFlow<WeatherLocation?> = weatherSettings.location
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val forecasts: StateFlow<List<Forecast>> = weatherRepository.getForecasts(48)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyForecasts: StateFlow<List<DailyForecast>> = weatherRepository.getDailyForecasts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val measurementSystem: StateFlow<MeasurementSystem> = weatherSettings.measurementSystem
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MeasurementSystem.Metric)

    fun setLocation(location: WeatherLocation) {
        viewModelScope.launch {
            weatherSettings.setLocation(location)
        }
    }

    fun refresh(context: Context) {
        viewModelScope.launch {
            weatherRepository.deleteForecasts()
        }
    }
}
