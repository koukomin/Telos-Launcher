package de.mm20.launcher2.uikmp

import kotlin.test.Test
import kotlin.test.assertEquals

class CalculatorEngineTest {
    @Test
    fun addsAndClears() {
        val e = CalculatorEngine()
        e.key("2")
        e.key("+")
        e.key("3")
        e.key("=")
        assertEquals("5", e.display)
        e.key("C")
        assertEquals("0", e.display)
    }

    @Test
    fun divides() {
        val e = CalculatorEngine()
        e.key("8")
        e.key("/")
        e.key("2")
        e.key("=")
        assertEquals("4", e.display)
    }
}
