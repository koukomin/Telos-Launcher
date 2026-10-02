package de.mm20.launcher2.ui.comms

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.ui.R

private val DIALPAD_KEYS = listOf(
    Triple("1", "", ""),
    Triple("2", "ABC", "ΑΒΓ"),
    Triple("3", "DEF", "ΔΕΖ"),
    Triple("4", "GHI", "ΗΘΙ"),
    Triple("5", "JKL", "ΚΛΜ"),
    Triple("6", "MNO", "ΝΞΟ"),
    Triple("7", "PQRS", "ΠΡΣ"),
    Triple("8", "TUV", "ΤΥΦ"),
    Triple("9", "WXYZ", "ΧΨΩ"),
    Triple("*", "", ""),
    Triple("0", "+", ""),
    Triple("#", "", ""),
)

@Composable
fun DialpadScreen() {
    val viewModel: DialpadViewModel = viewModel()
    val context = LocalContext.current

    val input by viewModel.input.collectAsStateWithLifecycle()
    val t9Results by viewModel.t9Results.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        if (t9Results.isNotEmpty()) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(t9Results, key = { it.id }) { contact ->
                    T9ResultRow(
                        contact = contact,
                        onClick = { number -> viewModel.dial(context, number) },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = input.ifEmpty { " " },
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Light,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (row in DIALPAD_KEYS.chunked(3)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    for ((digit, latin, greek) in row) {
                        DialpadKey(
                            digit = digit,
                            sublabel = listOf(latin, greek).filter { it.isNotEmpty() }.joinToString(" "),
                            onClick = { viewModel.onKeyPressed(digit.first()) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FloatingActionButton(onClick = { viewModel.dial(context) }) {
                    Icon(painterResource(R.drawable.call_24px), contentDescription = "Call")
                }
                if (input.isNotEmpty()) {
                    Spacer(Modifier.width(24.dp))
                    IconButton(onClick = { viewModel.onBackspace() }) {
                        Icon(painterResource(R.drawable.close_24px), contentDescription = "Backspace")
                    }
                }
            }
        }
    }
}

@Composable
private fun DialpadKey(digit: String, sublabel: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = digit, style = MaterialTheme.typography.headlineSmall)
            if (sublabel.isNotEmpty()) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun T9ResultRow(contact: DialerContact, onClick: (String) -> Unit) {
    val primaryNumber = contact.phoneNumbers.firstOrNull() ?: return
    ListItem(
        headlineContent = {
            Text(contact.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(primaryNumber, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onClick(primaryNumber) },
        ),
    )
}
