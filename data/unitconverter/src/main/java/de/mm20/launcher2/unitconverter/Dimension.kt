package de.mm20.launcher2.unitconverter
import de.mm20.launcher2.i18n.R as I18nR

import androidx.annotation.StringRes
enum class Dimension(@StringRes val resource: Int) {
    Length(I18nR.string.dimension_length),
    Mass(I18nR.string.dimension_mass),
    Velocity(I18nR.string.dimension_velocity),
    Volume(I18nR.string.dimension_volume),
    Area(I18nR.string.dimension_area),
    Currency(I18nR.string.dimension_currency),
    Data(I18nR.string.dimension_data),
    Bitrate(I18nR.string.dimension_bitrate),
    Pressure(I18nR.string.dimension_pressure),
    Energy(I18nR.string.dimension_energy),
    Frequency(I18nR.string.dimension_frequency),
    Temperature(I18nR.string.dimension_temperature),
    Time(I18nR.string.dimension_time),
}