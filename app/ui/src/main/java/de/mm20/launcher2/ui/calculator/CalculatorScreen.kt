package de.mm20.launcher2.ui.calculator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Serializable
data object CalculatorRoute : NavKey

private typealias Icons = de.mm20.launcher2.base.R.drawable

/**
 * Telos Calculator: a standard and scientific calculator with a VAT tab, unit and currency
 * conversion and a dated history. The layout follows the calculator of OxygenOS: the scientific keys
 * are shown in landscape or with the f(x) button, swiping down on the display opens the history.
 */
@Composable
fun CalculatorScreen() {
    val vm: CalculatorViewModel = viewModel()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(painterResource(Icons.calculate_24px), contentDescription = null) },
                    label = { Text("Calculator") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(painterResource(Icons.percent_discount_24px), contentDescription = null) },
                    label = { Text("VAT") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(painterResource(Icons.swap_horiz_24px), contentDescription = null) },
                    label = { Text("Convert") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            when (tab) {
                0 -> CalculatorTab(vm, onVat = { tab = 1 })
                1 -> VatTab(vm, onUseInCalculator = { vm.replaceExpression(it); tab = 0 })
                else -> ConvertTab(vm)
            }
        }
    }
}

// ---- calculator tab -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculatorTab(vm: CalculatorViewModel, onVat: () -> Unit) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showScientific by rememberSaveable { mutableStateOf(false) }
    var inverse by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val keyHeight = if (landscape) 44.dp else 62.dp

    val display: @Composable (Modifier) -> Unit = { modifier ->
        Display(vm, modifier, onHistory = { showHistory = true }, onScientific = { showScientific = !showScientific }, scientificToggle = !landscape)
    }

    if (landscape) {
        Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScientificKeys(vm, inverse, { inverse = !inverse }, keyHeight, Modifier.weight(1f))
            Column(Modifier.weight(1f)) {
                display(Modifier.weight(1f))
                StandardKeys(vm, keyHeight)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            display(Modifier.weight(1f))
            if (showScientific) {
                ScientificKeys(vm, inverse, { inverse = !inverse }, 48.dp, Modifier)
                Spacer(Modifier.height(6.dp))
            }
            StandardKeys(vm, keyHeight)
        }
    }

    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }, sheetState = rememberModalBottomSheetState()) {
            HistorySheet(vm, onPick = { vm.replaceExpression(it); showHistory = false })
        }
    }
}

@Composable
private fun Display(
    vm: CalculatorViewModel,
    modifier: Modifier,
    onHistory: () -> Unit,
    onScientific: () -> Unit,
    scientificToggle: Boolean,
) {
    val preview = vm.preview()
    val canVat = vm.currentValue() != null && vm.vatRate.replace(',', '.').toDoubleOrNull() != null
    var drag by remember { mutableStateOf(0f) }
    Column(
        modifier.fillMaxWidth().pointerInput(Unit) {
            // swipe down on the display opens the history, like in the OxygenOS calculator
            detectVerticalDragGestures(
                onDragStart = { drag = 0f },
                onDragEnd = { if (drag > 80f) onHistory() },
            ) { _, amount -> drag += amount }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onHistory) {
                Icon(painterResource(Icons.schedule_24px), contentDescription = "History")
            }
            if (scientificToggle) {
                TextButton(onClick = onScientific) { Text("f(x)") }
            }
            TextButton(onClick = { vm.toggleDegrees() }) { Text(if (vm.degrees) "DEG" else "RAD") }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                if (vm.error) "Invalid expression" else vm.expression.ifEmpty { "0" },
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), reverseScrolling = true),
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp),
                color = if (vm.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                preview ?: " ",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (canVat) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    AssistChip(onClick = { vm.applyVat(remove = false) }, label = { Text("+ VAT ${vm.vatRate}%") })
                    AssistChip(onClick = { vm.applyVat(remove = true) }, label = { Text("− VAT ${vm.vatRate}%") })
                }
            }
        }
    }
}

private enum class KeyKind { Number, Operator, Function, Equals, Clear }

@Composable
private fun Key(
    label: String,
    kind: KeyKind,
    height: Dp,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (kind) {
        KeyKind.Number -> scheme.surfaceContainerHigh to scheme.onSurface
        KeyKind.Operator -> scheme.secondaryContainer to scheme.onSecondaryContainer
        KeyKind.Function -> scheme.surfaceContainer to scheme.onSurfaceVariant
        KeyKind.Equals -> scheme.primary to scheme.onPrimary
        KeyKind.Clear -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Box(
        modifier.padding(3.dp).height(height).clip(RoundedCornerShape(22.dp)).background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (iconRes != null) {
            Icon(painterResource(iconRes), contentDescription = label, tint = fg)
        } else {
            Text(label, color = fg, fontSize = if (kind == KeyKind.Function) 16.sp else 24.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun StandardKeys(vm: CalculatorViewModel, height: Dp) {
    @Composable
    fun KeyRow(vararg keys: @Composable (Modifier) -> Unit) {
        Row(Modifier.fillMaxWidth()) { keys.forEach { it(Modifier.weight(1f)) } }
    }
    fun num(label: String): @Composable (Modifier) -> Unit = { m -> Key(label, KeyKind.Number, height, m) { vm.input(label) } }
    fun op(label: String): @Composable (Modifier) -> Unit = { m -> Key(label, KeyKind.Operator, height, m) { vm.input(label) } }

    Column(Modifier.fillMaxWidth()) {
        KeyRow(
            { m -> Key("AC", KeyKind.Clear, height, m) { vm.clear() } },
            { m -> Key("⌫", KeyKind.Clear, height, m, iconRes = Icons.rd_ic_backspace) { vm.backspace() } },
            { m -> Key("%", KeyKind.Operator, height, m) { vm.input("%") } },
            op("÷"),
        )
        KeyRow(num("7"), num("8"), num("9"), op("×"))
        KeyRow(num("4"), num("5"), num("6"), op("−"))
        KeyRow(num("1"), num("2"), num("3"), op("+"))
        KeyRow(
            { m -> Key("( )", KeyKind.Operator, height, m) { vm.input("()") } },
            num("0"),
            num("."),
            { m -> Key("=", KeyKind.Equals, height, m) { vm.equals() } },
        )
    }
}

@Composable
private fun ScientificKeys(vm: CalculatorViewModel, inverse: Boolean, onInverse: () -> Unit, height: Dp, modifier: Modifier) {
    @Composable
    fun Fn(label: String, text: String = label, m: Modifier) =
        Key(label, KeyKind.Function, height, m) { vm.input(text) }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Key("INV", if (inverse) KeyKind.Operator else KeyKind.Function, height, Modifier.weight(1f)) { onInverse() }
            if (!inverse) {
                Fn("sin", "sin(", Modifier.weight(1f)); Fn("cos", "cos(", Modifier.weight(1f)); Fn("tan", "tan(", Modifier.weight(1f))
            } else {
                Fn("sin⁻¹", "asin(", Modifier.weight(1f)); Fn("cos⁻¹", "acos(", Modifier.weight(1f)); Fn("tan⁻¹", "atan(", Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Fn("ln", "ln(", Modifier.weight(1f)); Fn("log", "log(", Modifier.weight(1f))
            Fn("√", "√(", Modifier.weight(1f)); Fn("x!", "!", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Fn("x²", "^2", Modifier.weight(1f)); Fn("xʸ", "^", Modifier.weight(1f))
            Fn("π", "π", Modifier.weight(1f)); Fn("e", "e", Modifier.weight(1f))
        }
    }
}

@Composable
private fun HistorySheet(vm: CalculatorViewModel, onPick: (String) -> Unit) {
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (vm.history.isNotEmpty()) {
                IconButton(onClick = { vm.clearHistory() }) {
                    Icon(painterResource(Icons.delete_24px), contentDescription = "Clear history")
                }
            }
        }
        if (vm.history.isEmpty()) {
            Text("Calculations you finish with = are listed here, with the date and time.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(vm.history) { entry ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(entry.result) }.padding(vertical = 10.dp), horizontalAlignment = Alignment.End) {
                        Text(format.format(Date(entry.time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(entry.expression, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("= ${entry.result}", style = MaterialTheme.typography.headlineSmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

// ---- VAT tab --------------------------------------------------------------------------------

private fun money(value: java.math.BigDecimal): String = String.format(Locale.getDefault(), "%,.2f", value)

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Telos Calculator", text))
    Toast.makeText(context, "Copied $text", Toast.LENGTH_SHORT).show()
}

@Composable
private fun VatTab(vm: CalculatorViewModel, onUseInCalculator: (String) -> Unit) {
    val context = LocalContext.current
    var amountText by rememberSaveable { mutableStateOf("") }
    val amount = remember(amountText) { CalcEngine.evaluateOrNull(amountText, true) }
    val result = amount?.let { vm.vat(it) }
    val remove = vm.vatRemove

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("VAT", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

        OutlinedTextField(
            value = vm.vatRate,
            onValueChange = vm::updateVatRate,
            label = { Text("VAT rate") },
            suffix = { Text("%") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (preset in listOf("24", "13", "6", "0")) {
                FilterChip(selected = vm.vatRate == preset, onClick = { vm.updateVatRate(preset) }, label = { Text("$preset%") })
            }
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !remove, onClick = { vm.updateVatRemove(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Add VAT") }
            SegmentedButton(selected = remove, onClick = { vm.updateVatRemove(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Remove VAT") }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.take(40) },
            label = { Text(if (remove) "Amount with VAT" else "Amount without VAT") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            supportingText = { Text("You can type a calculation, for example 12.5*3") },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { vm.currentValue()?.let { amountText = CalcEngine.format(it) } }, enabled = vm.currentValue() != null) {
            Text("Use the result from the calculator")
        }

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val rows = listOf(
                    Triple("Without VAT", result?.net, remove),
                    Triple("VAT ${vm.vatRate}%", result?.vat, false),
                    Triple("With VAT", result?.gross, !remove),
                )
                for ((label, value, highlight) in rows) {
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = value != null) { value?.let { copy(context, it.toPlainString()) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, Modifier.weight(1f), style = if (highlight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
                        Text(
                            value?.let { money(it) } ?: "—",
                            style = if (highlight) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Text("Tap an amount to copy it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(
            onClick = { result?.let { onUseInCalculator((if (remove) it.net else it.gross).toPlainString()) } },
            enabled = result != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Use ${if (remove) "the amount without VAT" else "the amount with VAT"} in the calculator") }
    }
}

// ---- converter tab --------------------------------------------------------------------------

@Composable
private fun ConvertTab(vm: CalculatorViewModel) {
    val context = LocalContext.current
    val categories = vm.categories
    var categoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var input by rememberSaveable { mutableStateOf("1") }
    var fromIndex by rememberSaveable { mutableIntStateOf(0) }
    var toIndex by rememberSaveable { mutableIntStateOf(1) }
    var output by remember { mutableStateOf<Double?>(null) }

    val category = categories.getOrNull(categoryIndex)
    val from = category?.units?.getOrNull(fromIndex)
    val to = category?.units?.getOrNull(toIndex)

    LaunchedEffect(category, from?.symbol, to?.symbol, input) {
        val value = CalcEngine.evaluateOrNull(input, true)
        output = if (category != null && from != null && to != null && value != null) {
            vm.convert(context, category, from.symbol, to.symbol, value)
        } else null
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Convert", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        if (category == null) {
            Text("Loading units…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEachIndexed { i, c ->
                FilterChip(
                    selected = i == categoryIndex,
                    onClick = { categoryIndex = i; fromIndex = 0; toIndex = 1 },
                    label = { Text(stringResource(c.converter.dimension.resource)) },
                )
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(40) },
            label = { Text("Value") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UnitPicker("From", category, fromIndex) { fromIndex = it }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            IconButton(onClick = { val f = fromIndex; fromIndex = toIndex; toIndex = f }) {
                Icon(painterResource(Icons.swap_vert_24px), contentDescription = "Swap units")
            }
        }
        UnitPicker("To", category, toIndex) { toIndex = it }

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(16.dp).clickable(enabled = output != null) { output?.let { copy(context, CalcEngine.format(it)) } }) {
                Text("Result", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    output?.let { "${CalcEngine.format(it)} ${to?.symbol.orEmpty()}" } ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun UnitPicker(label: String, category: ConverterCategory, selected: Int, onSelect: (Int) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val unit = category.units.getOrNull(selected)
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${unit?.symbol.orEmpty()}  ${unit?.formatName(context, 2.0).orEmpty()}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            category.units.forEachIndexed { i, u ->
                DropdownMenuItem(
                    text = { Text("${u.symbol}  ${u.formatName(context, 2.0)}") },
                    onClick = { onSelect(i); open = false },
                )
            }
        }
    }
}
