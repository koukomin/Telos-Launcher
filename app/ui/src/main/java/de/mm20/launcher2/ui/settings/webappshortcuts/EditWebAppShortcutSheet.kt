package de.mm20.launcher2.ui.settings.webappshortcuts

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheet
import de.mm20.launcher2.ui.ktx.toPixels
import kotlinx.coroutines.launch

@Composable
fun EditWebAppShortcutSheet(
    expanded: Boolean,
    existing: WebAppShortcut?,
    onSave: (label: String, url: String, iconUri: String?, faviconUrl: String?) -> Unit,
    onDismiss: () -> Unit,
    onImportIcon: suspend (uri: Uri, sizePx: Int) -> String?,
    onFindFavicon: suspend (url: String) -> String?,
) {
    BottomSheet(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        var label by remember(existing) { mutableStateOf(existing?.label ?: "") }
        var url by remember(existing) { mutableStateOf(existing?.url ?: "") }
        var iconUri by remember(existing) { mutableStateOf(existing?.iconUri) }
        var faviconUrl by remember(existing) { mutableStateOf(existing?.faviconUrl) }
        var findingFavicon by remember { mutableStateOf(false) }

        val scope = rememberCoroutineScope()
        val iconSizePx = 48.dp.toPixels().toInt()

        val pickIconLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) {
                    scope.launch {
                        val path = onImportIcon(uri, iconSizePx)
                        if (path != null) iconUri = path
                    }
                }
            }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(
                    if (existing == null) R.string.web_app_shortcut_create
                    else R.string.web_app_shortcut_edit
                ),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val iconModel = iconUri ?: faviconUrl
                if (iconModel != null) {
                    AsyncImage(
                        model = iconModel,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small),
                    )
                }
                OutlinedButton(onClick = { pickIconLauncher.launch("image/*") }) {
                    Text(stringResource(R.string.web_app_shortcut_pick_icon))
                }
            }

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.web_app_shortcut_label)) },
                singleLine = true,
            )

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                value = url,
                onValueChange = { url = it; findingFavicon = false },
                label = { Text(stringResource(R.string.web_app_shortcut_url)) },
                singleLine = true,
                trailingIcon = if (findingFavicon) {
                    { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
                } else null,
            )

            TextButton(
                modifier = Modifier.padding(top = 4.dp),
                enabled = url.isNotBlank() && !findingFavicon,
                onClick = {
                    findingFavicon = true
                    scope.launch {
                        val favicon = onFindFavicon(url)
                        findingFavicon = false
                        if (favicon != null) faviconUrl = favicon
                    }
                }
            ) {
                Text(stringResource(R.string.web_app_shortcut_detect_icon))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    enabled = label.isNotBlank() && url.isNotBlank(),
                    onClick = {
                        onSave(label.trim(), url.trim(), iconUri, faviconUrl)
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
