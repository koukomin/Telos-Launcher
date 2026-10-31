package de.mm20.launcher2.unitconverter.converters
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import de.mm20.launcher2.unitconverter.Dimension
internal class VelocityConverter(context: Context) : SimpleFactorConverter() {
    override val dimension = Dimension.Velocity

    override val standardUnits = listOf(
            MeasureUnitWithFactor(
                    1.0,
                    context.getString(I18nR.string.unit_meter_per_second_symbol),
                    I18nR.plurals.unit_meter_per_second
            ),
            MeasureUnitWithFactor(
                    3.6,
                    context.getString(I18nR.string.unit_kilometer_per_hour_symbol),
                    I18nR.plurals.unit_kilometer_per_hour
            ),
            MeasureUnitWithFactor(
                    3600.0 / 1609.344,
                    context.getString(I18nR.string.unit_mile_per_hour_symbol),
                    I18nR.plurals.unit_mile_per_hour
            ),
            MeasureUnitWithFactor(
                    3600.0 / 1852.0,
                    context.getString(I18nR.string.unit_knot_symbol),
                    I18nR.plurals.unit_knot
            )
    )

}