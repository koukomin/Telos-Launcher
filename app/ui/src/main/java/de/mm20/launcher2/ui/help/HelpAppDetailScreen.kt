package de.mm20.launcher2.ui.help
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.component.VirtualAppWindow
import de.mm20.launcher2.ui.locals.LocalBackStack

@Composable
fun HelpAppDetailScreen(appKey: String) {
    val backStack = LocalBackStack.current

    VirtualAppWindow {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (backStack.size > 1) {
                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(painterResource(BaseR.drawable.arrow_back_24px), stringResource(I18nR.string.hc_back))
                    }
                }
                Text(
                    text = stringResource(I18nR.string.telos_help_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = if (backStack.size > 1) 0.dp else 8.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Help / Tutorial",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Live App Preview capabilities are not yet fully built. Here, you'll be able to launch $appKey and see an interactive walkthrough of its features.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = { /* TODO: Launch actual app */ }) {
                    Text("Launch $appKey")
                }
            }
        }
    }
}
