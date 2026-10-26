package de.mm20.launcher2.ui.media.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R

/** One action strip; changing/reordering sources lives on the title instead of a second overflow icon. */
@Composable
internal fun MediaToolbarHeader(
    title: String,
    onBack: () -> Unit,
    onOpenSources: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    sourceMenu: @Composable () -> Unit = {},
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val actionsWidth = (maxWidth * 0.42f).coerceAtLeast(48.dp)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.hc_back))
                }
                Box(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onOpenSources)
                        .padding(horizontal = 4.dp).testTag("media-source-selector"), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Icon(painterResource(R.drawable.arrow_drop_down_24px), null, Modifier.size(24.dp))
                    }
                    sourceMenu()
                }
                Row(Modifier.widthIn(max = actionsWidth).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}

