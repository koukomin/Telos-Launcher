package de.mm20.launcher2.ui.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcEngineTest {

    private fun calc(expression: String, degrees: Boolean = true) = CalcEngine.evaluate(expression, degrees)

    @Test
    fun `operator precedence and brackets`() {
        assertEquals(14.0, calc("2+3×4"), 1e-9)
        assertEquals(9.0, calc("(1+2)×3"), 1e-9)
        assertEquals(2.5, calc("10÷4"), 1e-9)
        assertEquals(5.0, calc("(2+3"), 1e-9)
    }

    @Test
    fun `powers, factorial and roots`() {
        assertEquals(1024.0, calc("2^10"), 1e-9)
        assertEquals(-4.0, calc("-2^2"), 1e-9)
        assertEquals(0.5, calc("2^-1"), 1e-9)
        assertEquals(120.0, calc("5!"), 1e-9)
        assertEquals(4.0, calc("√16"), 1e-9)
        assertEquals(4.0, calc("√(16)"), 1e-9)
    }

    @Test
    fun `percent works like on a pocket calculator`() {
        assertEquals(220.0, calc("200+10%"), 1e-9)
        assertEquals(180.0, calc("200−10%"), 1e-9)
        assertEquals(0.5, calc("50%"), 1e-9)
    }

    @Test
    fun `functions and constants`() {
        assertEquals(0.5, calc("sin(30)"), 1e-9)
        assertEquals(30.0, calc("asin(0.5)"), 1e-9)
        assertEquals(0.5, calc("sin(${Math.PI}/6)", degrees = false), 1e-9)
        assertEquals(1.0, calc("ln(e)"), 1e-9)
        assertEquals(3.0, calc("log(1000)"), 1e-9)
        assertEquals(3.0, calc("log2(8)"), 1e-9)
        assertEquals(2 * Math.PI, calc("2π"), 1e-9)
        assertEquals(15.0, calc("3(4+1)"), 1e-9)
    }

    @Test
    fun `vat arithmetic`() {
        assertEquals(124.0, calc("100×1.24"), 1e-9)
        assertEquals(100.0, calc("124÷1.24"), 1e-9)
    }

    @Test
    fun `invalid input is rejected`() {
        assertNull(CalcEngine.evaluateOrNull("2+", true))
        assertNull(CalcEngine.evaluateOrNull("", true))
        assertNull(CalcEngine.evaluateOrNull("abc(3)", true))
        assertNull(CalcEngine.evaluateOrNull("1/0", true))
        assertNull(CalcEngine.evaluateOrNull("(-1)!", true))
    }

    @Test
    fun `results have no floating point noise`() {
        assertEquals("0.3", CalcEngine.format(0.1 + 0.2))
        assertEquals("0.333333333333", CalcEngine.format(1.0 / 3))
        assertEquals("1E20", CalcEngine.format(1e20))
        assertEquals("-2.5E30", CalcEngine.format(-2.5e30))
        assertEquals("1000", CalcEngine.format(1000.0))
    }
}
