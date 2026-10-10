package de.mm20.launcher2.ui.calculator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.unitconverter.Dimension
import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.text.DecimalFormatSymbols
import java.util.Date
import java.util.Locale

@Serializable
data object CalculatorRoute : NavKey

private enum class Page { Calculator, Units, UnitDetail, Vat }

/** The tiles of the unit converter. Pressure, energy and the numeral systems are converted by [CalcUnits], the rest by the launcher's unit converter. */
private enum class UnitCat(val label: Int, val icon: Int?, val glyph: String?, val dimension: Dimension?) {
    Currency(R.string.calculator_cat_currency, null, "¥", Dimension.Currency),
    Length(R.string.calculator_cat_length, R.drawable.ic_calc_length, null, Dimension.Length),
    Area(R.string.calculator_cat_area, R.drawable.ic_calc_area, null, Dimension.Area),
    Volume(R.string.calculator_cat_volume, R.drawable.ic_calc_volume, null, Dimension.Volume),
    Weight(R.string.calculator_cat_weight, R.drawable.ic_calc_weight, null, Dimension.Mass),
    Temperature(R.string.calculator_cat_temperature, R.drawable.ic_calc_temperature, null, Dimension.Temperature),
    Speed(R.string.calculator_cat_speed, R.drawable.ic_calc_speed, null, Dimension.Velocity),
    Pressure(R.string.calculator_cat_pressure, R.drawable.ic_calc_pressure, null, null),
    Energy(R.string.calculator_cat_energy, R.drawable.ic_calc_energy, null, null),
    Numeral(R.string.calculator_cat_numeral, null, "01", null),
    Time(R.string.calculator_cat_time, R.drawable.ic_calc_time, null, Dimension.Time),
    Data(R.string.calculator_cat_data, R.drawable.ic_calc_data, null, Dimension.Data),
}

private val decimalSeparator: Char = DecimalFormatSymbols.getInstance().decimalSeparator

/** The calculator works with a decimal point, the display uses the separator of the language */
private fun String.toDisplay(): String = if (decimalSeparator == '.') this else replace('.', decimalSeparator)

/**
 * Telos Calculator. The layout follows the calculator of OxygenOS (round keys, 4 columns for the
 * standard keys and 5 for the scientific keys, a grid of unit categories, "00" key) and the look
 * follows the Telos theme. VAT is the addition of Telos.
 */
@Composable
fun CalculatorScreen() {
    val vm: CalculatorViewModel = viewModel()
    var page by rememberSaveable { mutableStateOf(Page.Calculator) }
    var category by rememberSaveable { mutableStateOf(UnitCat.Length) }

    BackHandler(enabled = page != Page.Calculator) {
        page = if (page == Page.UnitDetail) Page.Units else Page.Calculator
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding(),
    ) {
        when (page) {
            Page.Calculator -> CalculatorPage(vm, onUnits = { page = Page.Units }, onVat = { page = Page.Vat })
            Page.Units -> UnitsPage(onBack = { page = Page.Calculator }, onPick = { category = it; page = Page.UnitDetail })
            Page.UnitDetail -> UnitDetailPage(vm, category, onBack = { page = Page.Units })
            Page.Vat -> VatPage(vm, onBack = { page = Page.Calculator }, onUseInCalculator = { vm.replaceExpression(it); page = Page.Calculator })
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = stringResource(R.string.calculator_back))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

// ---- calculator page ------------------------------------------------------------------------

/** In landscape the keys cannot be round and square: seven rows of them would not fit the screen. */
private val LocalCompactKeys = compositionLocalOf { false }

private enum class KeyKind { Number, Operator, Clear, Function, Equals }

@Composable
private fun RoundKey(
    label: String,
    kind: KeyKind,
    modifier: Modifier = Modifier,
    fontSize: Int = 30,
    selected: Boolean = false,
    iconRes: Int? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (kind) {
        KeyKind.Number -> scheme.surfaceContainerHigh to scheme.onSurface
        KeyKind.Operator -> scheme.secondaryContainer to scheme.onSecondaryContainer
        KeyKind.Clear -> scheme.surfaceContainerHighest to scheme.onSurface
        KeyKind.Function -> scheme.surfaceContainer to (if (selected) scheme.primary else scheme.onSurfaceVariant)
        KeyKind.Equals -> scheme.primary to scheme.onPrimary
    }
    val compact = LocalCompactKeys.current
    Box(
        modifier.padding(4.dp).then(if (compact) Modifier.height(32.dp) else Modifier.aspectRatio(1f)).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (iconRes != null) {
            Icon(painterResource(iconRes), contentDescription = label, tint = fg)
        } else {
            Text(
                label,
                color = fg,
                fontSize = (if (compact) minOf(fontSize, 18) else fontSize).sp,
                fontWeight = if (kind == KeyKind.Clear || selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculatorPage(vm: CalculatorViewModel, onUnits: () -> Unit, onVat: () -> Unit) {
    val context = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var scientificChoice by rememberSaveable { mutableStateOf(false) }
    val scientific = scientificChoice || landscape
    var inverse by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val topBar: @Composable () -> Unit = {
        // top bar: scientific or standard keys, VAT, unit converter, more
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (!landscape) {
                IconButton(onClick = { scientificChoice = !scientificChoice }) {
                    Text(
                        text = if (scientific) "+ −\n× =" else "√ π\ne =",
                        fontSize = 13.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }
            IconButton(onClick = onVat) {
                Icon(painterResource(R.drawable.ic_calc_vat), contentDescription = stringResource(R.string.calculator_vat))
            }
            IconButton(onClick = onUnits) {
                Icon(painterResource(R.drawable.ic_calc_grid), contentDescription = stringResource(R.string.calculator_unit_converter))
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(R.drawable.more_vert_24px), contentDescription = stringResource(R.string.calculator_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.calculator_history)) }, onClick = { menu = false; showHistory = true })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.calculator_copy_result)) },
                        onClick = {
                            menu = false
                            val text = vm.currentValue()?.let { CalcEngine.format(it) } ?: vm.expression
                            if (text.isNotEmpty()) copyToClipboard(context, text.toDisplay())
                        },
                    )
                }
            }
        }
    }

    if (landscape) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                topBar()
                Display(vm, Modifier.weight(1f), onHistory = { showHistory = true })
            }
            Box(Modifier.weight(1.5f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalCompactKeys provides true) {
                    ScientificKeys(vm, inverse, { inverse = !inverse }, Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    } else Column(Modifier.fillMaxSize()) {
        topBar()
        Display(vm, Modifier.weight(1f), onHistory = { showHistory = true })
        if (scientific) {
            ScientificKeys(vm, inverse, { inverse = !inverse }, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        } else {
            StandardKeys(vm, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }

    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }, sheetState = rememberModalBottomSheetState()) {
            HistorySheet(vm, onPick = { vm.replaceExpression(it); showHistory = false })
        }
    }
}

@Composable
private fun Display(vm: CalculatorViewModel, modifier: Modifier, onHistory: () -> Unit) {
    val preview = vm.preview()
    val canVat = vm.currentValue() != null && vm.vatRate.replace(',', '.').toDoubleOrNull() != null
    var drag by remember { mutableStateOf(0f) }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .pointerInput(Unit) {
                // swipe down on the display opens the history, like in the OxygenOS calculator
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (drag > 80f) onHistory() },
                ) { _, amount -> drag += amount }
            },
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            if (vm.error) stringResource(R.string.calculator_invalid) else vm.expression.toDisplay(),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), reverseScrolling = true),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp),
            color = if (vm.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            preview?.toDisplay() ?: " ",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (canVat) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                AssistChip(onClick = { vm.applyVat(remove = false) }, label = { Text(stringResource(R.string.calculator_chip_add_vat, vm.vatRate)) })
                AssistChip(onClick = { vm.applyVat(remove = true) }, label = { Text(stringResource(R.string.calculator_chip_remove_vat, vm.vatRate)) })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StandardKeys(vm: CalculatorViewModel, modifier: Modifier) {
    @Composable
    fun KeyRow(vararg keys: @Composable (Modifier) -> Unit) {
        Row(Modifier.fillMaxWidth()) { keys.forEach { it(Modifier.weight(1f)) } }
    }
    fun num(label: String, token: String = label): @Composable (Modifier) -> Unit = { m -> RoundKey(label, KeyKind.Number, m) { vm.input(token) } }
    fun op(label: String): @Composable (Modifier) -> Unit = { m -> RoundKey(label, KeyKind.Operator, m, fontSize = 34) { vm.input(label) } }

    Column(modifier.fillMaxWidth()) {
        KeyRow(
            { m -> RoundKey("AC", KeyKind.Clear, m) { vm.clear() } },
            { m -> RoundKey("%", KeyKind.Clear, m) { vm.input("%") } },
            { m -> RoundKey("⌫", KeyKind.Clear, m, iconRes = R.drawable.rd_ic_backspace) { vm.backspace() } },
            op("÷"),
        )
        KeyRow(num("7"), num("8"), num("9"), op("×"))
        KeyRow(num("4"), num("5"), num("6"), op("−"))
        KeyRow(num("1"), num("2"), num("3"), op("+"))
        KeyRow(
            num("00"), num("0"), num(decimalSeparator.toString(), "."),
            { m -> RoundKey("=", KeyKind.Equals, m, fontSize = 34) { vm.equals() } },
        )
    }
}

@Composable
private fun ScientificKeys(vm: CalculatorViewModel, inverse: Boolean, onInverse: () -> Unit, modifier: Modifier) {
    @Composable
    fun KeyRow(vararg keys: @Composable (Modifier) -> Unit) {
        Row(Modifier.fillMaxWidth()) { keys.forEach { it(Modifier.weight(1f)) } }
    }
    fun fn(label: String, token: String = label, selected: Boolean = false, onClick: (() -> Unit)? = null): @Composable (Modifier) -> Unit =
        { m -> RoundKey(label, KeyKind.Function, m, fontSize = 20, selected = selected) { if (onClick != null) onClick() else vm.input(token) } }
    fun num(label: String, token: String = label): @Composable (Modifier) -> Unit = { m -> RoundKey(label, KeyKind.Number, m, fontSize = 26) { vm.input(token) } }
    fun op(label: String): @Composable (Modifier) -> Unit = { m -> RoundKey(label, KeyKind.Operator, m, fontSize = 28) { vm.input(label) } }
    val degrees = vm.degrees

    Column(modifier.fillMaxWidth()) {
        KeyRow(
            if (inverse) fn("sin⁻¹", "asin(") else fn("sin", "sin("),
            if (inverse) fn("cos⁻¹", "acos(") else fn("cos", "cos("),
            if (inverse) fn("tan⁻¹", "atan(") else fn("tan", "tan("),
            fn(stringResource(R.string.calculator_radians), selected = !degrees, onClick = { vm.chooseDegrees(false) }),
            fn(stringResource(R.string.calculator_degrees), selected = degrees, onClick = { vm.chooseDegrees(true) }),
        )
        KeyRow(
            if (inverse) fn("10ˣ", "10^") else fn("log", "log("),
            if (inverse) fn("eˣ", "exp(") else fn("ln", "ln("),
            fn("(", "("), fn(")", ")"),
            fn("inv", selected = inverse, onClick = onInverse),
        )
        KeyRow(
            fn("!", "!"),
            { m -> RoundKey("AC", KeyKind.Clear, m, fontSize = 22) { vm.clear() } },
            { m -> RoundKey("%", KeyKind.Clear, m, fontSize = 24) { vm.input("%") } },
            { m -> RoundKey("⌫", KeyKind.Clear, m, iconRes = R.drawable.rd_ic_backspace) { vm.backspace() } },
            op("÷"),
        )
        KeyRow(fn("^", "^"), num("7"), num("8"), num("9"), op("×"))
        KeyRow(fn("√", "√("), num("4"), num("5"), num("6"), op("−"))
        KeyRow(fn("π", "π"), num("1"), num("2"), num("3"), op("+"))
        KeyRow(
            fn("e", "e"), num("00"), num("0"), num(decimalSeparator.toString(), "."),
            { m -> RoundKey("=", KeyKind.Equals, m, fontSize = 30) { vm.equals() } },
        )
    }
}

@Composable
private fun HistorySheet(vm: CalculatorViewModel, onPick: (String) -> Unit) {
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.calculator_history), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (vm.history.isNotEmpty()) {
                IconButton(onClick = { vm.clearHistory() }) {
                    Icon(painterResource(R.drawable.delete_24px), contentDescription = stringResource(R.string.calculator_history_clear))
                }
            }
        }
        if (vm.history.isEmpty()) {
            Text(stringResource(R.string.calculator_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(vm.history) { entry ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(entry.result) }.padding(vertical = 10.dp), horizontalAlignment = Alignment.End) {
                        Text(format.format(Date(entry.time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(entry.expression.toDisplay(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("= ${entry.result.toDisplay()}", style = MaterialTheme.typography.headlineSmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Telos Calculator", text))
    Toast.makeText(context, context.getString(R.string.calculator_copied, text), Toast.LENGTH_SHORT).show()
}

// ---- unit converter -------------------------------------------------------------------------

@Composable
private fun UnitsPage(onBack: () -> Unit, onPick: (UnitCat) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.calculator_unit_converter), onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(UnitCat.entries.toList()) { cat ->
                Column(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onPick(cat) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (cat.icon != null) {
                        Icon(painterResource(cat.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
                    } else {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(cat.glyph.orEmpty(), color = MaterialTheme.colorScheme.surface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        stringResource(cat.label),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UnitDetailPage(vm: CalculatorViewModel, cat: UnitCat, onBack: () -> Unit) {
    val context = LocalContext.current
    var input by rememberSaveable(cat) { mutableStateOf("1") }
    var fromIndex by rememberSaveable(cat) { mutableIntStateOf(0) }
    var toIndex by rememberSaveable(cat) { mutableIntStateOf(1) }

    val library = cat.dimension?.let { dimension -> vm.categories.firstOrNull { it.converter.dimension == dimension } }
    val own: List<SimpleUnit>? = when (cat) {
        UnitCat.Pressure -> CalcUnits.pressure
        UnitCat.Energy -> CalcUnits.energy
        else -> null
    }
    val numeral = cat == UnitCat.Numeral
    val radixNames = listOf(
        stringResource(R.string.calculator_radix_2), stringResource(R.string.calculator_radix_8),
        stringResource(R.string.calculator_radix_10), stringResource(R.string.calculator_radix_16),
    )
    val labels: List<String> = when {
        numeral -> radixNames
        own != null -> own.map { it.symbol }
        library != null -> library.units.map { "${it.symbol}  ${it.formatName(context, 2.0)}" }
        else -> emptyList()
    }
    val symbols: List<String> = library?.units?.map { it.symbol }.orEmpty()
    var output by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cat, library, input, fromIndex, toIndex) {
        output = when {
            numeral -> CalcUnits.convertRadix(input, CalcUnits.radixes[fromIndex.coerceIn(0, 3)], CalcUnits.radixes[toIndex.coerceIn(0, 3)])
            own != null -> CalcEngine.evaluateOrNull(input, true)?.let { value ->
                CalcEngine.format(CalcUnits.convert(own, fromIndex.coerceIn(own.indices), toIndex.coerceIn(own.indices), value))
            }
            library != null -> CalcEngine.evaluateOrNull(input, true)?.let { value ->
                val from = symbols.getOrNull(fromIndex) ?: return@let null
                val to = symbols.getOrNull(toIndex) ?: return@let null
                vm.convert(context, library, from, to, value)?.let { CalcEngine.format(it) }
            }
            else -> null
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(stringResource(cat.label), onBack)
        if (labels.size < 2) {
            Text(
                text = stringResource(if (cat == UnitCat.Currency) R.string.calculator_currency_missing else R.string.calculator_unit_converter),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UnitCard(stringResource(R.string.calculator_from)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(40) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.calculator_value)) },
                    keyboardOptions = KeyboardOptions(keyboardType = if (numeral) KeyboardType.Ascii else KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                UnitPicker(labels, fromIndex) { fromIndex = it }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IconButton(onClick = { val f = fromIndex; fromIndex = toIndex; toIndex = f }) {
                    Icon(painterResource(R.drawable.swap_vert_24px), contentDescription = stringResource(R.string.calculator_swap))
                }
            }
            UnitCard(stringResource(R.string.calculator_to)) {
                Text(
                    output?.toDisplay() ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = output != null) { output?.let { copyToClipboard(context, it.toDisplay()) } }
                        .padding(vertical = 8.dp),
                )
                UnitPicker(labels, toIndex) { toIndex = it }
            }
        }
    }
}

@Composable
private fun UnitCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun UnitPicker(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(labels.getOrNull(selected).orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            labels.forEachIndexed { i, label ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(i); open = false })
            }
        }
    }
}

// ---- VAT ------------------------------------------------------------------------------------

private fun money(value: java.math.BigDecimal): String = String.format(Locale.getDefault(), "%,.2f", value)

@Composable
private fun VatPage(vm: CalculatorViewModel, onBack: () -> Unit, onUseInCalculator: (String) -> Unit) {
    val context = LocalContext.current
    var amountText by rememberSaveable { mutableStateOf("") }
    val amount = remember(amountText) { CalcEngine.evaluateOrNull(amountText, true) }
    val result = amount?.let { vm.vat(it) }
    val remove = vm.vatRemove

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(stringResource(R.string.calculator_vat), onBack)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = vm.vatRate,
                onValueChange = vm::updateVatRate,
                label = { Text(stringResource(R.string.calculator_vat_rate)) },
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
                SegmentedButton(selected = !remove, onClick = { vm.updateVatRemove(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.calculator_vat_add)) }
                SegmentedButton(selected = remove, onClick = { vm.updateVatRemove(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.calculator_vat_remove)) }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.take(40) },
                label = { Text(stringResource(if (remove) R.string.calculator_vat_amount_gross else R.string.calculator_vat_amount_net)) },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.calculator_vat_amount_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { vm.currentValue()?.let { amountText = CalcEngine.format(it) } }, enabled = vm.currentValue() != null) {
                Text(stringResource(R.string.calculator_vat_use_result))
            }

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val rows = listOf(
                        Triple(stringResource(R.string.calculator_vat_without), result?.net, remove),
                        Triple(stringResource(R.string.calculator_vat_amount, vm.vatRate), result?.vat, false),
                        Triple(stringResource(R.string.calculator_vat_with), result?.gross, !remove),
                    )
                    for ((label, value, highlight) in rows) {
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = value != null) { value?.let { copyToClipboard(context, it.toPlainString()) } },
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
                    Text(stringResource(R.string.calculator_vat_tap_copy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(
                onClick = { result?.let { onUseInCalculator((if (remove) it.net else it.gross).toPlainString()) } },
                enabled = result != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (remove) R.string.calculator_vat_use_net else R.string.calculator_vat_use_gross)) }
        }
    }
}
