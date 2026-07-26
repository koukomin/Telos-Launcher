package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.preferences.TimeFormat
import kotlinx.coroutines.flow.map


class LocaleSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {
    val timeFormat
        get() = launcherDataStore.data.map { it.locale.localeTimeFormat }

    fun setTimeFormat(timeFormat: TimeFormat) {
        launcherDataStore.update {
            it.copy(locale = it.locale.copy(localeTimeFormat = timeFormat))
        }
    }

    val measurementSystem
        get() = launcherDataStore.data.map { it.locale.localeMeasurementSystem }

    fun setMeasurementSystem(measurementSystem: MeasurementSystem) {
        launcherDataStore.update {
            it.copy(locale = it.locale.copy(localeMeasurementSystem = measurementSystem))
        }
    }

    val transliterator
        get() = launcherDataStore.data.map { it.locale.localeTransliterator }

    fun setTransliterator(transliterator: String?) {
        launcherDataStore.update {
            it.copy(locale = it.locale.copy(localeTransliterator = transliterator))
        }
    }

    val primaryCalendar
        get() = launcherDataStore.data.map { it.locale.localePrimaryCalendar }

    fun setPrimaryCalendar(primaryCalendar: String?) {
        launcherDataStore.update {
            it.copy(locale = it.locale.copy(localePrimaryCalendar = primaryCalendar))
        }
    }

    val secondaryCalendar
        get() = launcherDataStore.data.map { it.locale.localeSecondaryCalendar }

    fun setSecondaryCalendar(secondaryCalendar: String?) {
        launcherDataStore.update {
            it.copy(locale = it.locale.copy(localeSecondaryCalendar = secondaryCalendar))
        }
    }

    val currencies
        get() = launcherDataStore.data.map { it.locale.localeCurrencies.distinct() }

     fun setCurrencies(currencies: List<String>) {
         launcherDataStore.update {
             it.copy(locale = it.locale.copy(localeCurrencies = currencies.distinct()))
         }
     }
}