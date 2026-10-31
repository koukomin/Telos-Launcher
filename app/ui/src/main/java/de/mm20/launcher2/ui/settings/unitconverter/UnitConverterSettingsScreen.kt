package de.mm20.launcher2.ui.settings.unitconverter
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.locale.CurrencySettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object UnitConverterSettingsRoute: NavKey

@Composable
fun UnitConverterSettingsScreen() {
    val viewModel: UnitConverterSettingsScreenVM = viewModel()
    val backStack = LocalBackStack.current

    PreferenceScreen(
        title = stringResource(I18nR.string.preference_search_unitconverter),
        helpUrl = "https://koukomin.github.io/Telos-Launcher/docs/user-guide/search/unit-converter"
    ) {
        item {
            PreferenceCategory {
                val unitConverter by viewModel.unitConverter.collectAsState()
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_search_unitconverter),
                    summary = stringResource(I18nR.string.preference_search_unitconverter_summary),
                    value = unitConverter == true,
                    onValueChanged = {
                        viewModel.setUnitConverter(it)
                    }
                )
                val currencyConverter by viewModel.currencyConverter.collectAsState()
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_search_currencyconverter),
                    summary = stringResource(I18nR.string.preference_search_currencyconverter_summary),
                    enabled = unitConverter != false,
                    value = currencyConverter == true,
                    onValueChanged = {
                        viewModel.setCurrencyConverter(it)
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(I18nR.string.preference_search_supportedunits),
                    icon = BaseR.drawable.help_24px,
                    onClick = {
                        backStack.add(UnitConverterHelpSettingsRoute)
                    }
                )
                Preference(
                    title = stringResource(I18nR.string.preference_currency_settings),
                    icon = BaseR.drawable.open_in_new_24px,
                    onClick = {
                        backStack.add(CurrencySettingsRoute)
                    }
                )
            }
        }

    }
}
