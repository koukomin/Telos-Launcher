package de.mm20.launcher2.ui.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/**
 * The expression evaluator of Telos Calculator. A small recursive descent parser, so that the
 * calculator does not depend on anything else and behaves the same everywhere.
 *
 * Supports + - × ÷ ^ ! % ( ), implicit multiplication (2π, 3(4+1)), the constants π and e, and the
 * functions sin cos tan asin acos atan sinh cosh tanh ln log log2 sqrt cbrt abs exp. A missing
 * closing bracket at the end is added. "200 + 10%" means 200 + 10% of 200, like on a pocket
 * calculator, and a lone "10%" is 0.1.
 */
object CalcEngine {

    class CalcException(message: String) : Exception(message)

    fun evaluate(expression: String, degrees: Boolean): Double =
        Parser(expression, degrees).parse()

    fun evaluateOrNull(expression: String, degrees: Boolean): Double? =
        try {
            evaluate(expression, degrees).takeIf { it.isFinite() }
        } catch (e: CalcException) {
            null
        }

    /** Result text without floating point noise: 12 significant digits, no trailing zeros */
    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "Error"
        if (value == 0.0) return "0"
        val rounded = BigDecimal(value).round(MathContext(12, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val magnitude = abs(value)
        return if (magnitude >= 1e15 || magnitude < 1e-6) {
            val unscaled = rounded.unscaledValue().toString().trimStart('-')
            val exponent = unscaled.length - 1 - rounded.scale()
            val digits = unscaled.trimEnd('0').ifEmpty { "0" }
            val mantissa = if (digits.length > 1) digits[0] + "." + digits.substring(1) else digits
            (if (value < 0) "-" else "") + mantissa + "E" + exponent
        } else {
            rounded.toPlainString()
        }
    }

    private class Parser(source: String, private val degrees: Boolean) {
        private val s = source
            .replace('×', '*').replace('÷', '/').replace('−', '-').replace('–', '-')
            .replace(',', '.').replace("\\s".toRegex(), "")
        private var pos = 0

        fun parse(): Double {
            if (s.isEmpty()) throw CalcException("Empty")
            val (value, _) = expression()
            if (pos != s.length) throw CalcException("Unexpected '${s[pos]}'")
            return value
        }

        private fun peek(): Char? = s.getOrNull(pos)

        private fun accept(c: Char): Boolean {
            if (peek() == c) { pos++; return true }
            return false
        }

        // returns the value and whether it ended with a percent sign
        private fun expression(): Pair<Double, Boolean> {
            var (left, pct) = term()
            while (true) {
                val op = peek()
                if (op != '+' && op != '-') break
                pos++
                val (right, rightPct) = term()
                // 200 + 10% -> 200 + 20
                val operand = if (rightPct) left * right else right
                left = if (op == '+') left + operand else left - operand
                pct = false
            }
            return left to pct
        }

        private fun term(): Pair<Double, Boolean> {
            var (left, pct) = unary()
            while (true) {
                val c = peek() ?: break
                when {
                    c == '*' || c == '/' -> {
                        pos++
                        val (right, _) = unary()
                        left = if (c == '*') left * right else left / right
                        pct = false
                    }
                    startsOperand(c) -> {
                        val (right, _) = unary()
                        left *= right
                        pct = false
                    }
                    else -> break
                }
            }
            return left to pct
        }

        private fun startsOperand(c: Char) = c.isDigit() || c == '.' || c == '(' || c == 'π' || c == '√' || c.isLetter()

        private fun unary(): Pair<Double, Boolean> {
            if (accept('-')) { val (v, p) = unary(); return -v to p }
            if (accept('+')) return unary()
            return power()
        }

        private fun power(): Pair<Double, Boolean> {
            val (base, pct) = postfix()
            if (accept('^')) {
                val (exponent, _) = unary()
                return base.pow(exponent) to false
            }
            return base to pct
        }

        private fun postfix(): Pair<Double, Boolean> {
            var value = primary()
            var pct = false
            while (true) {
                when {
                    accept('!') -> { value = factorial(value); pct = false }
                    accept('%') -> { value /= 100.0; pct = true }
                    else -> break
                }
            }
            return value to pct
        }

        private fun primary(): Double {
            val c = peek() ?: throw CalcException("Unexpected end")
            return when {
                c.isDigit() || c == '.' -> number()
                c == '(' -> {
                    pos++
                    val (v, _) = expression()
                    accept(')') // a missing bracket at the end is tolerated
                    v
                }
                c == 'π' -> { pos++; PI }
                c == '√' -> { pos++; sqrt(operandOfFunction()) }
                c.isLetter() -> identifier()
                else -> throw CalcException("Unexpected '$c'")
            }
        }

        private fun operandOfFunction(): Double {
            if (peek() == '(') {
                pos++
                val (v, _) = expression()
                accept(')')
                return v
            }
            return power().first
        }

        private fun number(): Double {
            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
            // scientific notation, 1.5E3 (the E must be followed by digits, so that "2e" is Euler's number)
            if (peek() == 'E' && s.getOrNull(pos + 1)?.let { it.isDigit() || it == '-' || it == '+' } == true) {
                val save = pos
                pos++
                if (peek() == '-' || peek() == '+') pos++
                if (peek()?.isDigit() != true) pos = save
                while (peek()?.isDigit() == true) pos++
            }
            return s.substring(start, pos).toDoubleOrNull() ?: throw CalcException("Bad number")
        }

        private fun identifier(): Double {
            val start = pos
            while (peek()?.let { it.isLetter() && it != 'π' } == true) pos++
            if (s.substring(start, pos) == "log" && peek() == '2') pos++
            val name = s.substring(start, pos).lowercase()
            if (name == "e") return Math.E
            if (name == "pi") return PI
            val arg = operandOfFunction()
            val rad = if (degrees) Math.toRadians(arg) else arg
            return when (name) {
                "sin" -> sin(rad)
                "cos" -> cos(rad)
                "tan" -> tan(rad)
                "asin" -> asin(arg).let { if (degrees) Math.toDegrees(it) else it }
                "acos" -> acos(arg).let { if (degrees) Math.toDegrees(it) else it }
                "atan" -> atan(arg).let { if (degrees) Math.toDegrees(it) else it }
                "sinh" -> sinh(arg)
                "cosh" -> cosh(arg)
                "tanh" -> tanh(arg)
                "ln" -> ln(arg)
                "log" -> log10(arg)
                "log2" -> log2(arg)
                "sqrt" -> sqrt(arg)
                "cbrt" -> cbrt(arg)
                "abs" -> abs(arg)
                "exp" -> exp(arg)
                else -> throw CalcException("Unknown function $name")
            }
        }

        private fun factorial(x: Double): Double {
            if (x < 0 || x != Math.floor(x) || x > 170) throw CalcException("Factorial")
            var r = 1.0
            for (i in 2..x.toInt()) r *= i
            return r
        }
    }
}
