package de.mm20.launcher2.ui.kmp
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.uikmp.CalculatorEngine
import de.mm20.launcher2.uikmp.SharedCalculatorScreen
import de.mm20.launcher2.uikmp.SharedCalendarScreen
import de.mm20.launcher2.uikmp.SharedClockScreen
import de.mm20.launcher2.uikmp.SharedNotesScreen
import java.time.LocalDate
import java.time.YearMonth

/** Android hosts for `:ui-kmp` screens. Titles come from `:core:i18n`. */
@Composable
fun SharedCalculatorHost(modifier: Modifier = Modifier) {
    val engine = remember { CalculatorEngine() }
    var display by remember { mutableStateOf(engine.display) }
    SharedCalculatorScreen(
        title = stringResource(I18nR.string.calculator_title),
        display = display,
        onKey = { key ->
            engine.key(key)
            display = engine.display
        },
        modifier = modifier,
    )
}

@Composable
fun SharedClockHost(title: String, time: String, date: String, modifier: Modifier = Modifier) {
    SharedClockScreen(title, time, date, modifier)
}

@Composable
fun SharedNotesHost(text: String, onText: (String) -> Unit, modifier: Modifier = Modifier) {
    SharedNotesScreen(
        title = stringResource(I18nR.string.notes_title),
        placeholder = stringResource(I18nR.string.notes_widget_placeholder),
        text = text,
        onText = onText,
        modifier = modifier,
    )
}

@Composable
fun SharedCalendarHost(modifier: Modifier = Modifier, month: YearMonth = YearMonth.now(), today: Int = LocalDate.now().dayOfMonth) {
    SharedCalendarScreen("${month.month} ${month.year}", month.lengthOfMonth(), today, modifier)
}
