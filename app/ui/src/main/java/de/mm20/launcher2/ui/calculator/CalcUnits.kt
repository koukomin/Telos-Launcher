package de.mm20.launcher2.ui.calculator

/** A unit that is converted by multiplying with a constant: [factor] is how many of the base unit one of this unit is */
data class SimpleUnit(val symbol: String, val factor: Double)

/**
 * The units that the launcher's unit converter does not have: pressure, energy and the numeral
 * systems. Everything else (length, mass, area, volume, speed, temperature, time, data, currency)
 * is converted by the launcher's unit converter, see [CalculatorViewModel.categories].
 */
object CalcUnits {

    /** Base unit: pascal */
    val pressure = listOf(
        SimpleUnit("Pa", 1.0),
        SimpleUnit("kPa", 1_000.0),
        SimpleUnit("hPa", 100.0),
        SimpleUnit("bar", 100_000.0),
        SimpleUnit("mbar", 100.0),
        SimpleUnit("atm", 101_325.0),
        SimpleUnit("psi", 6_894.757293168),
        SimpleUnit("mmHg", 133.322387415),
        SimpleUnit("inHg", 3_386.389),
    )

    /** Base unit: joule */
    val energy = listOf(
        SimpleUnit("J", 1.0),
        SimpleUnit("kJ", 1_000.0),
        SimpleUnit("cal", 4.184),
        SimpleUnit("kcal", 4_184.0),
        SimpleUnit("Wh", 3_600.0),
        SimpleUnit("kWh", 3_600_000.0),
        SimpleUnit("BTU", 1_055.05585262),
        SimpleUnit("eV", 1.602176634e-19),
    )

    /** The numeral systems, by radix */
    val radixes = listOf(2, 8, 10, 16)

    fun convert(units: List<SimpleUnit>, from: Int, to: Int, value: Double): Double =
        value * units[from].factor / units[to].factor

    /** Converts [text] written in [fromRadix] to [toRadix], or null if it is not a number in that system */
    fun convertRadix(text: String, fromRadix: Int, toRadix: Int): String? {
        var clean = text.trim().removePrefix("-")
        if (fromRadix == 16) clean = clean.removePrefix("0x").removePrefix("0X")
        if (fromRadix == 2) clean = clean.removePrefix("0b").removePrefix("0B")
        if (clean.isEmpty()) return null
        val value = clean.toLongOrNull(fromRadix) ?: return null
        val signed = if (text.trim().startsWith("-")) -value else value
        return signed.toString(toRadix).uppercase()
    }
}
