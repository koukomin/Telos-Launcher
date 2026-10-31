package de.mm20.launcher2.unitconverter.converters
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import de.mm20.launcher2.unitconverter.Dimension
internal class MassConverter(context: Context): SimpleFactorConverter() {
    override val dimension = Dimension.Mass

    override val standardUnits = listOf(
            MeasureUnitWithFactor(
                    1.0,
                    context.getString(I18nR.string.unit_kilogram_symbol),
                    I18nR.plurals.unit_kilogram
            ),
            MeasureUnitWithFactor(
                    1000.0,
                    context.getString(I18nR.string.unit_gram_symbol),
                    I18nR.plurals.unit_gram
            ),
            MeasureUnitWithFactor(
                    0.001,
                    context.getString(I18nR.string.unit_metric_ton_symbol),
                    I18nR.plurals.unit_metric_ton
            ),
            MeasureUnitWithFactor(
                    1000.0 / (453.59237 * 2240.0),
                    context.getString(I18nR.string.unit_long_ton_symbol),
                    I18nR.plurals.unit_long_ton
            ),
            MeasureUnitWithFactor(
                    1000.0 / (453.59237 * 14.0),
                    context.getString(I18nR.string.unit_stone_symbol),
                    I18nR.plurals.unit_stone
            ),
            MeasureUnitWithFactor(
                    1000.0 / 453.59237,
                    context.getString(I18nR.string.unit_pound_symbol),
                    I18nR.plurals.unit_pound
            ),
            MeasureUnitWithFactor(
                    16.0 * 1000.0 / 453.59237,
                    context.getString(I18nR.string.unit_ounce_symbol),
                    I18nR.plurals.unit_ounce
            ),
            MeasureUnitWithFactor(
                    1000.0 / (453.59237 * 2000.0),
                    context.getString(I18nR.string.unit_short_ton_symbol),
                    I18nR.plurals.unit_short_ton
            )
    )

}