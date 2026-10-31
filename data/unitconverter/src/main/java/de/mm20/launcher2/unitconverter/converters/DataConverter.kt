package de.mm20.launcher2.unitconverter.converters
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import de.mm20.launcher2.unitconverter.Dimension
internal class DataConverter(context: Context) : SimpleFactorConverter() {
    override val dimension = Dimension.Data

    override val standardUnits = listOf(
            MeasureUnitWithFactor(
                    1.0,
                    context.getString(I18nR.string.unit_byte_symbol),
                    I18nR.plurals.unit_byte
            ),
            MeasureUnitWithFactor(
                    0.001,
                    context.getString(I18nR.string.unit_kilobyte_symbol),
                    I18nR.plurals.unit_kilobyte
            ),
            MeasureUnitWithFactor(
                    0.000001,
                    context.getString(I18nR.string.unit_megabyte_symbol),
                    I18nR.plurals.unit_megabyte
            ),
            MeasureUnitWithFactor(
                    0.000000001,
                    context.getString(I18nR.string.unit_gigabyte_symbol),
                    I18nR.plurals.unit_gigabyte
            ),
            MeasureUnitWithFactor(
                    0.000000000001,
                    context.getString(I18nR.string.unit_terabyte_symbol),
                    I18nR.plurals.unit_terabyte
            ),
            MeasureUnitWithFactor(
                    1.0 / 1024,
                    context.getString(I18nR.string.unit_kibibyte_symbol),
                    I18nR.plurals.unit_kibibyte
            ),
            MeasureUnitWithFactor(
                    1.0 / (1024 * 1024),
                    context.getString(I18nR.string.unit_mebibyte_symbol),
                    I18nR.plurals.unit_mebibyte
            ),
            MeasureUnitWithFactor(
                    1.0 / (1024 * 1024 * 1024),
                    context.getString(I18nR.string.unit_gibibyte_symbol),
                    I18nR.plurals.unit_gibibyte
            ),
            MeasureUnitWithFactor(
                    1.0 / (1024.0 * 1024 * 1024 * 1024),
                    context.getString(I18nR.string.unit_tebibyte_symbol),
                    I18nR.plurals.unit_tebibyte
            ),
            MeasureUnitWithFactor(
                    8.0,
                    context.getString(I18nR.string.unit_bit_symbol),
                    I18nR.plurals.unit_bit
            ),
            MeasureUnitWithFactor(
                    8.0 / 1000,
                    context.getString(I18nR.string.unit_kilobit_symbol),
                    I18nR.plurals.unit_kilobit
            ),
            MeasureUnitWithFactor(
                    8.0 / 1000000,
                    context.getString(I18nR.string.unit_megabit_symbol),
                    I18nR.plurals.unit_megabit
            ),
            MeasureUnitWithFactor(
                    8.0 / 1000000000,
                    context.getString(I18nR.string.unit_gigabit_symbol),
                    I18nR.plurals.unit_gigabit
            ),
            MeasureUnitWithFactor(
                    8.0 / 1000000000000,
                    context.getString(I18nR.string.unit_terabit_symbol),
                    I18nR.plurals.unit_terabit
            )
    )

}