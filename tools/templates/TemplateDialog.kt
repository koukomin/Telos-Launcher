package de.mm20.launcher2.ui.template

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.component.TelosCyberHeading

/**
 * Standard Cyber-Tech Dialog Template for AI Agents.
 */
@Composable
fun TemplateCyberDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading { Text(title) } },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(message)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(I18nR.string.hc_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
