package de.mm20.launcher2.ui.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcUnitsTest {

    @Test
    fun `pressure`() {
        val units = CalcUnits.pressure
        val bar = units.indexOfFirst { it.symbol == "bar" }
        val kpa = units.indexOfFirst { it.symbol == "kPa" }
        val atm = units.indexOfFirst { it.symbol == "atm" }
        assertEquals(100.0, CalcUnits.convert(units, bar, kpa, 1.0), 1e-9)
        assertEquals(1.01325, CalcUnits.convert(units, atm, bar, 1.0), 1e-9)
    }

    @Test
    fun `energy`() {
        val units = CalcUnits.energy
        val kwh = units.indexOfFirst { it.symbol == "kWh" }
        val kj = units.indexOfFirst { it.symbol == "kJ" }
        val kcal = units.indexOfFirst { it.symbol == "kcal" }
        assertEquals(3600.0, CalcUnits.convert(units, kwh, kj, 1.0), 1e-9)
        assertEquals(1.0, CalcUnits.convert(units, kcal, units.indexOfFirst { it.symbol == "cal" }, 0.001), 1e-9)
    }

    @Test
    fun `numeral systems`() {
        assertEquals("1010", CalcUnits.convertRadix("10", 10, 2))
        assertEquals("FF", CalcUnits.convertRadix("255", 10, 16))
        assertEquals("255", CalcUnits.convertRadix("ff", 16, 10))
        assertEquals("-5", CalcUnits.convertRadix("-101", 2, 10))
        assertNull(CalcUnits.convertRadix("2", 2, 10))
        assertNull(CalcUnits.convertRadix("", 10, 2))
    }
}
