package de.mm20.launcher2.uikmp

class CalculatorEngine {
    var display: String = "0"
        private set
    private var acc = 0.0
    private var op: Char? = null

    fun key(key: String) {
        when (key) {
            "C" -> {
                display = "0"
                acc = 0.0
                op = null
            }
            "+", "-", "*", "/" -> apply(key[0])
            "=" -> apply('=')
            else -> digit(key)
        }
    }

    private fun digit(d: String) {
        display = if (display == "0") d else display + d
    }

    private fun apply(next: Char?) {
        val v = display.toDoubleOrNull() ?: 0.0
        acc = when (op) {
            '+' -> acc + v
            '-' -> acc - v
            '*' -> acc * v
            '/' -> if (v == 0.0) acc else acc / v
            else -> v
        }
        display = if (next == '=') format(acc) else "0"
        op = if (next == '=') null else next
        if (next == '=') acc = display.toDoubleOrNull() ?: 0.0
    }

    private fun format(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
