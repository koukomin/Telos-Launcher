package de.mm20.launcher2.ui.files
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R

/** null means a regular ZIP; the split length is entered in whole MiB to avoid fractional ambiguity. */
internal fun splitZipBytes(enabled: Boolean, sizeMiB: String): Long? = if (!enabled) null else
    sizeMiB.toLongOrNull()?.takeIf { it in 1..4_194_304 }?.let { it * 1_048_576 }

@Composable
internal fun SplitZipOptions(
    enabled: Boolean, onEnabled: (Boolean) -> Unit, sizeMiB: String, onSize: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(enabled, onEnabled)
            Text(stringResource(I18nR.string.files_archive_split))
        }
        if (enabled) {
            OutlinedTextField(
                value = sizeMiB, onValueChange = onSize,
                label = { Text(stringResource(I18nR.string.files_archive_split_size)) },
                supportingText = { Text(stringResource(I18nR.string.files_archive_split_note)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, isError = splitZipBytes(true, sizeMiB) == null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (splitZipBytes(true, sizeMiB) == null) Text(
                stringResource(I18nR.string.files_archive_split_invalid),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
