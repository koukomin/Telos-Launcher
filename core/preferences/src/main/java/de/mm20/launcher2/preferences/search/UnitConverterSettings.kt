package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class UnitConverterSettingsData(
    val enabled: Boolean,
    val currencies: Boolean,
)

class UnitConverterSettings internal constructor(
    private val dataStore: LauncherDataStore,
) : Flow<UnitConverterSettingsData> by (dataStore.data.map {
    UnitConverterSettingsData(
        enabled = it.unitConverter.unitConverterEnabled,
        currencies = it.unitConverter.unitConverterCurrencies,
    )
}.distinctUntilChanged()) {
    val enabled: Flow<Boolean>
        get() = dataStore.data.map { it.unitConverter.unitConverterEnabled }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(unitConverter = it.unitConverter.copy(unitConverterEnabled = enabled)) }
    }

    val currenciesEnabled: Flow<Boolean>
        get() = dataStore.data.map { it.unitConverter.unitConverterCurrencies }.distinctUntilChanged()

    fun setCurrenciesEnabled(currencies: Boolean) {
        dataStore.update { it.copy(unitConverter = it.unitConverter.copy(unitConverterCurrencies = currencies)) }
    }

    val preferredCurrencies: Flow<List<String>>
        get() = dataStore.data.map { it.locale.localeCurrencies }.distinctUntilChanged()
}