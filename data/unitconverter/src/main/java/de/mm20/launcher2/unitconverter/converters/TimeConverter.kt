package de.mm20.launcher2.unitconverter.converters
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import de.mm20.launcher2.unitconverter.Dimension
internal class TimeConverter(context: Context) : SimpleFactorConverter() {
    override val dimension = Dimension.Time

    override val standardUnits = listOf(
            MeasureUnitWithFactor(
                    1.0,
                    context.getString(I18nR.string.unit_second_symbol),
                    I18nR.plurals.unit_second
            ),
            MeasureUnitWithFactor(
                    1000.0,
                    context.getString(I18nR.string.unit_millisecond_symbol),
                    I18nR.plurals.unit_millisecond
            ),
            MeasureUnitWithFactor(
                    1.0 / 60,
                    context.getString(I18nR.string.unit_minute_symbol),
                    I18nR.plurals.unit_minute
            ),
            MeasureUnitWithFactor(
                    1.0 / (60 * 60),
                    context.getString(I18nR.string.unit_hour_symbol),
                    I18nR.plurals.unit_hour
            ),
            MeasureUnitWithFactor(
                    1.0 / (60 * 60 * 24),
                    context.getString(I18nR.string.unit_day_symbol),
                    I18nR.plurals.unit_day
            ),
            MeasureUnitWithFactor(
                    1.0 / 31_557_600,
                    context.getString(I18nR.string.unit_year_symbol),
                    I18nR.plurals.unit_year
            )
    )

}