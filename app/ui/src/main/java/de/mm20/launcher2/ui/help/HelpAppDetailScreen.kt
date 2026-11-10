package de.mm20.launcher2.ui.help
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.search.TelosAppLabels
import de.mm20.launcher2.ui.component.VirtualAppWindow
import de.mm20.launcher2.ui.locals.LocalBackStack

@Composable
fun HelpAppDetailScreen(appKey: String) {
    val backStack = LocalBackStack.current
    val context = LocalContext.current
    val appName = remember(appKey) { TelosAppLabels.forDomain(context, appKey) }
    val route = remember(appKey) { helpAppRoute(appKey) }

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
                    text = stringResource(I18nR.string.telos_help_tutorial),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(I18nR.string.telos_help_preview_body, appName),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (route != null) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            val intent = Intent().setClassName(
                                context.packageName,
                                SettingsDeepLinkContract.ACTIVITY_CLASS_NAME,
                            )
                                .setPackage(context.packageName)
                                .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, route)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            if (appKey == "telos_messages_app") {
                                intent.putExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB, "messages")
                            }
                            runCatching { context.startActivity(intent) }
                        },
                    ) {
                        Text(stringResource(I18nR.string.telos_help_open_app, appName))
                    }
                }
            }
        }
    }
}

private fun helpAppRoute(appKey: String): String? = when (appKey) {
    "telos_calculator_app" -> SettingsDeepLinkContract.ROUTE_CALCULATOR
    "telos_media_app" -> SettingsDeepLinkContract.ROUTE_MEDIA
    "telos_files_app" -> SettingsDeepLinkContract.ROUTE_FILES
    "telos_notes_app" -> SettingsDeepLinkContract.ROUTE_NOTES
    "telos_system_app" -> SettingsDeepLinkContract.ROUTE_SYSTEM
    "telos_network_app" -> SettingsDeepLinkContract.ROUTE_NETWORK
    "telos_store_app" -> SettingsDeepLinkContract.ROUTE_STORE
    "telos_phone_app" -> SettingsDeepLinkContract.ROUTE_COMMS
    "telos_messages_app" -> SettingsDeepLinkContract.ROUTE_COMMS
    "telos_ermis_app" -> SettingsDeepLinkContract.ROUTE_ERMIS
    "telos_akou_app" -> "settings/akou"
    "telos_sync_app" -> SettingsDeepLinkContract.ROUTE_SYNC
    else -> null
}
