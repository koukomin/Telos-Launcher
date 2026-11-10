package de.mm20.launcher2.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.VirtualAppWindow
import de.mm20.launcher2.ui.locals.LocalBackStack

@Composable
fun HelpAppDetailScreen(appKey: String) {
    val backStack = LocalBackStack.current
    val context = LocalContext.current
    val app = HelpCatalog.apps.firstOrNull { it.key == appKey }

    VirtualAppWindow {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { backStack.removeLastOrNull() }) {
                    Icon(painterResource(BaseR.drawable.arrow_back_24px), stringResource(I18nR.string.hc_back))
                }
                Text(
                    text = stringResource(app?.nameRes ?: I18nR.string.telos_help_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            }
            if (app == null) return@Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(app.descRes), style = MaterialTheme.typography.bodyLarge)
                LauncherCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        app.bullets.forEach { res ->
                            Text("•  " + stringResource(res), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Text(
                    stringResource(app.caveatRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (app.route != null) {
                Button(
                    onClick = { openHelpRoute(context, app.route, app.commsTab) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(stringResource(I18nR.string.telos_help_open_app, stringResource(app.nameRes)))
                }
            }
        }
    }
}
