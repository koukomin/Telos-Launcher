package de.mm20.launcher2.ui.calculator

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.unitconverter.MeasureUnit
import de.mm20.launcher2.unitconverter.UnitConverterRepository
import de.mm20.launcher2.unitconverter.converters.Converter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.math.BigDecimal
import java.math.RoundingMode

data class HistoryEntry(val expression: String, val result: String, val time: Long)

/** The three amounts of a VAT calculation, rounded to cents so that net + VAT = gross */
data class VatResult(val net: BigDecimal, val vat: BigDecimal, val gross: BigDecimal)

/** One page of the converter: a converter and the units it knows */
class ConverterCategory(val converter: Converter, val units: List<MeasureUnit>)

class CalculatorViewModel(application: Application) : AndroidViewModel(application), KoinComponent {

    private val prefs = application.getSharedPreferences("telos_calculator", 0)
    private val unitRepository: UnitConverterRepository by inject()

    var expression by mutableStateOf("")
        private set
    var degrees by mutableStateOf(prefs.getBoolean("degrees", true))
        private set
    var vatRate by mutableStateOf(prefs.getString("vat_rate", "24") ?: "24")
        private set
    var vatRemove by mutableStateOf(prefs.getBoolean("vat_remove", false))
        private set
    var error by mutableStateOf(false)
        private set

    val history = mutableStateListOf<HistoryEntry>()
    val categories = mutableStateListOf<ConverterCategory>()

    /** After "=" a digit starts a new calculation, an operator continues with the result */
    private var justEvaluated = false

    init {
        loadHistory()
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.Default) {
                runCatching { unitRepository.getAvailableConverters(true) }.getOrDefault(emptyList())
                    .mapNotNull { converter ->
                        val units = runCatching { converter.getSupportedUnits() }.getOrDefault(emptyList())
                        if (units.size >= 2) ConverterCategory(converter, units) else null
                    }
            }
            categories.addAll(loaded)
        }
    }

    // ---- calculator -------------------------------------------------------------------------

    fun input(token: String) {
        error = false
        val isOperator = token in OPERATORS
        val isPostfix = token == "%" || token == "!"
        if (justEvaluated && !isOperator && !isPostfix) expression = ""
        justEvaluated = false
        when {
            isOperator -> {
                if (expression.isEmpty()) {
                    if (token == "−") expression = "−"
                    return
                }
                val last = expression.last()
                if (last.toString() in OPERATORS) {
                    expression = expression.dropLast(1) + token
                } else if (last != '(' || token == "−") {
                    expression += token
                }
            }
            isPostfix -> {
                val last = expression.lastOrNull() ?: return
                if (last.isDigit() || last == ')' || last == '%' || last == 'π' || last == 'e') expression += token
            }
            token == "." -> {
                val run = expression.takeLastWhile { it.isDigit() || it == '.' }
                if ('.' in run) return
                expression += if (run.isEmpty()) "0." else "."
            }
            token == "()" -> expression += nextBracket()
            else -> expression += token
        }
    }

    private fun nextBracket(): String {
        val open = expression.count { it == '(' }
        val close = expression.count { it == ')' }
        val last = expression.lastOrNull()
        val closes = open > close && last != null && (last.isDigit() || last == ')' || last == 'π' || last == 'e' || last == '%' || last == '!')
        return if (closes) ")" else "("
    }

    fun backspace() {
        error = false
        justEvaluated = false
        if (expression.isEmpty()) return
        // remove "sin(" as one piece
        if (expression.endsWith("(")) {
            val without = expression.dropLast(1)
            expression = when {
                without.endsWith("log2") -> without.dropLast(4)
                without.endsWith("√") -> without.dropLast(1)
                else -> without.dropLast(without.takeLastWhile { it.isLetter() }.length)
            }
            return
        }
        expression = expression.dropLast(1)
    }

    fun clear() {
        expression = ""
        error = false
        justEvaluated = false
    }

    fun toggleDegrees() {
        degrees = !degrees
        prefs.edit().putBoolean("degrees", degrees).apply()
    }

    /** The value of the expression, or null if it is incomplete or invalid */
    fun currentValue(): Double? = CalcEngine.evaluateOrNull(expression, degrees)

    /** Result to show under the expression while typing; null when it would only repeat the input */
    fun preview(): String? {
        if (expression.isEmpty()) return null
        val trivial = expression.all { it.isDigit() || it == '.' || it == '−' }
        if (trivial) return null
        return currentValue()?.let { CalcEngine.format(it) }
    }

    fun equals() {
        val value = currentValue()
        if (value == null) {
            error = expression.isNotEmpty()
            return
        }
        val result = CalcEngine.format(value)
        if (result != expression) addHistory(HistoryEntry(expression, result, System.currentTimeMillis()))
        expression = result.replace('-', '−')
        justEvaluated = true
    }

    /** Replaces the expression with the result, adding or removing VAT at the current rate */
    fun applyVat(remove: Boolean) {
        val value = currentValue() ?: return
        val factor = 1.0 + (vatRate.replace(',', '.').toDoubleOrNull() ?: return) / 100.0
        val result = if (remove) value / factor else value * factor
        val text = CalcEngine.format(result)
        addHistory(HistoryEntry("$expression ${if (remove) "−" else "+"} VAT $vatRate%", text, System.currentTimeMillis()))
        expression = text.replace('-', '−')
        justEvaluated = true
    }

    fun replaceExpression(text: String) {
        expression = text
        error = false
        justEvaluated = true
    }

    // ---- history ----------------------------------------------------------------------------

    private fun addHistory(entry: HistoryEntry) {
        history.add(0, entry)
        while (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
        saveHistory()
    }

    fun clearHistory() {
        history.clear()
        saveHistory()
    }

    private fun loadHistory() {
        runCatching {
            val array = JSONArray(prefs.getString("history", "[]"))
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                history.add(HistoryEntry(o.getString("e"), o.getString("r"), o.getLong("t")))
            }
        }
    }

    private fun saveHistory() {
        val array = JSONArray()
        history.forEach { array.put(JSONObject().put("e", it.expression).put("r", it.result).put("t", it.time)) }
        prefs.edit().putString("history", array.toString()).apply()
    }

    // ---- VAT --------------------------------------------------------------------------------

    fun updateVatRate(text: String) {
        // digits and one decimal separator only
        val clean = text.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
        vatRate = clean
        prefs.edit().putString("vat_rate", clean).apply()
    }

    fun updateVatRemove(remove: Boolean) {
        vatRemove = remove
        prefs.edit().putBoolean("vat_remove", remove).apply()
    }

    /** [amount] is the net amount when adding VAT and the gross amount when removing it */
    fun vat(amount: Double): VatResult? {
        val rate = vatRate.replace(',', '.').toDoubleOrNull() ?: return null
        if (!amount.isFinite()) return null
        val value = BigDecimal(amount).setScale(2, RoundingMode.HALF_UP)
        val factor = BigDecimal.valueOf(rate).divide(BigDecimal(100))
        return if (!vatRemove) {
            val vat = value.multiply(factor).setScale(2, RoundingMode.HALF_UP)
            VatResult(net = value, vat = vat, gross = value + vat)
        } else {
            val net = value.divide(BigDecimal.ONE + factor, 2, RoundingMode.HALF_UP)
            VatResult(net = net, vat = value - net, gross = value)
        }
    }

    suspend fun convert(context: android.content.Context, category: ConverterCategory, from: String, to: String, value: Double): Double? =
        withContext(Dispatchers.Default) {
            runCatching {
                category.converter.convert(context, from, value, to).values.firstOrNull()?.value
            }.getOrNull()
        }

    companion object {
        private const val MAX_HISTORY = 100
        private val OPERATORS = setOf("+", "−", "×", "÷", "^")
    }
}
