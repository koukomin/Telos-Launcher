// === TELOS_PENDING_REVIEW_START: right_dialer_ui ===
package de.mm20.launcher2.ui.comms

import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ktx.tryStartActivity

private val DIALPAD_KEYS = listOf(
    Triple("1", "oo", ""),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialpadScreen() {
    val viewModel: DialpadViewModel = viewModel()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val input by viewModel.input.collectAsStateWithLifecycle()
    val t9Results by viewModel.t9Results.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top T9 Search Match Header
        AnimatedVisibility(
            visible = t9Results.isNotEmpty(),
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(t9Results, key = { it.id }) { contact ->
                    T9ResultCard(
                        contact = contact,
                        onClick = { number -> viewModel.dial(context, number) }
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Input Display Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = input.ifEmpty { " " },
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Keypad Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onKeyPressed(digit.first()) 
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (digit == "0") {
                                    viewModel.onKeyPressed('+')
                                } else if (digit.first().isDigit()) {
                                    // Speed dial placeholder
                                    viewModel.dial(context, digit)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Bottom Actions Row (Dual-SIM / Quick Call)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Add to contacts
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            type = ContactsContract.RawContacts.CONTENT_TYPE
                            putExtra(ContactsContract.Intents.Insert.PHONE, input)
                        }
                        context.tryStartActivity(intent)
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.person_add_24px),
                        contentDescription = "Add to contacts",
                        tint = if (input.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                // Call Button
                FloatingActionButton(
                    onClick = { viewModel.dial(context) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        painter = painterResource(R.drawable.call_24px),
                        contentDescription = "Call",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Backspace
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    if (input.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier
                                .size(56.dp)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.onBackspace() 
                                    },
                                    onLongClick = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.onClear() 
                                    }
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.close_24px),
                                    contentDescription = "Backspace",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadKey(digit: String, sublabel: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(76.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium
            )
            if (sublabel.isNotEmpty()) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun T9ResultCard(contact: DialerContact, onClick: (String) -> Unit) {
    val primaryNumber = contact.phoneNumbers.firstOrNull() ?: return
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick(primaryNumber) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = contact.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = primaryNumber,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}
// === TELOS_PENDING_REVIEW_END: right_dialer_ui ===
