package de.mm20.launcher2.ui.launcher.search.calculator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import android.os.Build
import android.widget.Toast
import de.mm20.launcher2.ui.R
import java.math.BigDecimal
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.Calculator

@Composable
fun CalculatorItem(
    calculator: Calculator
) {
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val copy = {
        // Copy a plain decimal ("5", not "5.0" or "1.0E10") so it can be pasted anywhere
        val text = if (calculator.solution.isNaN() || calculator.solution.isInfinite()) {
            calculator.formattedString
        } else {
            BigDecimal.valueOf(calculator.solution).stripTrailingZeros().toPlainString()
        }
        clipboardManager.setText(AnnotatedString(text))
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, context.getString(R.string.calculator_copied, text), Toast.LENGTH_SHORT).show()
        }
    }
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {

        Text(
            text = calculator.getBeatifiedTerm(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Row(
            modifier = Modifier.align(Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "= ${calculator.formattedString}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .combinedClickable(
                        onClick = { copy() },
                        onLongClick = { copy() },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
            )
            IconButton(onClick = { copy() }) {
                Icon(
                    painterResource(R.drawable.content_copy_24px),
                    contentDescription = stringResource(R.string.calculator_copy_result),
                )
            }
        }
        // Base conversions are only meaningful (and only computed correctly) for integer literals
        // that fit into an Int
        if (calculator.term.matches(Regex("0x[0-9a-fA-F]+|0b[01]+|[0-9]+")) &&
            calculator.solution in 0.0..Int.MAX_VALUE.toDouble()
        ) {
            Text(
                calculator.formattedBinaryString,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp),
            )
            Text(
                calculator.formattedHexString,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.align(Alignment.End),
            )
            Text(
                calculator.formattedOctString,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}