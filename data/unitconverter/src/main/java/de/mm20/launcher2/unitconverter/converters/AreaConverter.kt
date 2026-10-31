package de.mm20.launcher2.unitconverter.converters
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import de.mm20.launcher2.unitconverter.Dimension
internal class AreaConverter(context: Context) : SimpleFactorConverter() {
    override val dimension = Dimension.Area

    override val standardUnits = listOf(
            MeasureUnitWithFactor(
                    0.000001,
                    context.getString(I18nR.string.unit_sqkilometer_symbol),
                    I18nR.plurals.unit_sqkilometer
            ),
            MeasureUnitWithFactor(
                    0.0001,
                    context.getString(I18nR.string.unit_hectare_symbol),
                    I18nR.plurals.unit_hectare
            ),
            MeasureUnitWithFactor(
                    1.0,
                    context.getString(I18nR.string.unit_sqmeter_symbol),
                    I18nR.plurals.unit_sqmeter
            ),
            MeasureUnitWithFactor(
                    10000.0,
                    context.getString(I18nR.string.unit_sqcentimeter_symbol),
                    I18nR.plurals.unit_sqcentimeter
            ),
            MeasureUnitWithFactor(
                    1000000.0,
                    context.getString(I18nR.string.unit_sqmillimeter_symbol),
                    I18nR.plurals.unit_sqmillimeter
            ),
            MeasureUnitWithFactor(
                    100 * 100 / (2.54 * 2.54),
                    context.getString(I18nR.string.unit_sqinch_symbol),
                    I18nR.plurals.unit_sqinch
            ),
            MeasureUnitWithFactor(
                    100 * 100 / (2.54 * 2.54 * 144),
                    context.getString(I18nR.string.unit_sqfoot_symbol),
                    I18nR.plurals.unit_sqfoot
            ),
            MeasureUnitWithFactor(
                    100 * 100 / (2.54 * 2.54 * 1296),
                    context.getString(I18nR.string.unit_sqyard_symbol),
                    I18nR.plurals.unit_sqyard
            ),
            MeasureUnitWithFactor(
                    100 * 100 / (2.54 * 2.54 * 144 * 43560),
                    context.getString(I18nR.string.unit_acre_symbol),
                    I18nR.plurals.unit_acre
            )
    )
}