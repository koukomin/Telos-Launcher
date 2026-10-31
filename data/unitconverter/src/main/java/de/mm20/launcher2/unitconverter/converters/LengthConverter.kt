package de.mm20.launcher2.unitconverter.converters
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import de.mm20.launcher2.unitconverter.Dimension
internal class LengthConverter(context: Context) : SimpleFactorConverter() {
    override val dimension = Dimension.Length

    override val standardUnits = listOf(
            MeasureUnitWithFactor(
                    1.0,
                    context.getString(I18nR.string.unit_meter_symbol),
                    I18nR.plurals.unit_meter
            ),
            MeasureUnitWithFactor(
                    0.001,
                    context.getString(I18nR.string.unit_kilometer_symbol),
                    I18nR.plurals.unit_kilometer
            ),
            MeasureUnitWithFactor(
                    10.0,
                    context.getString(I18nR.string.unit_decimeter_symbol),
                    I18nR.plurals.unit_decimeter
            ),
            MeasureUnitWithFactor(
                    100.0,
                    context.getString(I18nR.string.unit_centimeter_symbol),
                    I18nR.plurals.unit_centimeter
            ),
            MeasureUnitWithFactor(
                    1000.0,
                    context.getString(I18nR.string.unit_millimeter_symbol),
                    I18nR.plurals.unit_millimeter
            ),
            MeasureUnitWithFactor(
                    100 / 2.54,
                    context.getString(I18nR.string.unit_inch_symbol),
                    I18nR.plurals.unit_inch
            ),
            MeasureUnitWithFactor(
                    100 / (2.54 * 12),
                    context.getString(I18nR.string.unit_foot_symbol),
                    I18nR.plurals.unit_foot
            ),
            MeasureUnitWithFactor(
                    100 / (2.54 * 12 * 3),
                    context.getString(I18nR.string.unit_yard_symbol),
                    I18nR.plurals.unit_yard
            ),
            MeasureUnitWithFactor(
                    100 / (2.54 * 12 * 3 * 1760),
                    context.getString(I18nR.string.unit_mile_symbol),
                    I18nR.plurals.unit_mile
            ),
            MeasureUnitWithFactor(
                    1 / 1852.0,
                    context.getString(I18nR.string.unit_nautic_mile_symbol),
                    I18nR.plurals.unit_nautic_mile
            )
    )

}