package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map

class CalendarSearchSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val providers
        get() = dataStore.data.map { it.calendarSearch.calendarSearchProviders }

    val enabledProviders
        get() = dataStore.data.map { it.calendarSearch.calendarSearchProviders }

    fun isProviderEnabled(provider: String) = dataStore.data.map { it.calendarSearch.calendarSearchProviders.contains(provider) }

    fun setProviderEnabled(provider: String, enabled: Boolean) {
        dataStore.update {
            if (enabled) {
                it.copy(calendarSearch = it.calendarSearch.copy(calendarSearchProviders = it.calendarSearch.calendarSearchProviders + provider))
            } else {
                it.copy(calendarSearch = it.calendarSearch.copy(calendarSearchProviders = it.calendarSearch.calendarSearchProviders - provider))
            }
        }
    }

    val excludedCalendars
        get() = dataStore.data.map { it.calendarSearch.calendarSearchExcludedCalendars }
    fun setCalendarExcluded(calendarId: String, excluded: Boolean) {
        dataStore.update {
            if (excluded) {
                it.copy(calendarSearch = it.calendarSearch.copy(calendarSearchExcludedCalendars = it.calendarSearch.calendarSearchExcludedCalendars + calendarId))
            } else {
                it.copy(calendarSearch = it.calendarSearch.copy(calendarSearchExcludedCalendars = it.calendarSearch.calendarSearchExcludedCalendars - calendarId))
            }
        }
    }
}