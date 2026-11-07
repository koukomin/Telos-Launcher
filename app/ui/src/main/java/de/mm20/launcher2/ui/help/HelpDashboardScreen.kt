package de.mm20.launcher2.ui.help
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.component.VirtualAppWindow
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
data object HelpDashboardRoute : NavKey

@Serializable
data class HelpAppDetailRoute(val appKey: String) : NavKey

@Composable
fun HelpDashboardScreen() {
    val backStack = LocalBackStack.current

    val apps = remember {
        listOf(
            HelpAppItem("telos_calculator_app", BaseR.drawable.ic_app_calculator_full, I18nR.string.calculator_title, I18nR.string.telos_calculator_help_desc),
            HelpAppItem("telos_media_app", BaseR.drawable.ic_app_media_full, I18nR.string.dl_m_tab, I18nR.string.telos_media_help_desc),
            HelpAppItem("telos_files_app", BaseR.drawable.ic_app_files_full, I18nR.string.pdft_files, I18nR.string.telos_files_help_desc),
            HelpAppItem("telos_notes_app", BaseR.drawable.ic_app_notes_full, I18nR.string.notes_title, I18nR.string.telos_notes_help_desc),
            HelpAppItem("telos_system_app", BaseR.drawable.ic_app_system_full, I18nR.string.telos_system_label, I18nR.string.telos_system_help_desc),
            HelpAppItem("telos_network_app", BaseR.drawable.ic_app_network_full, I18nR.string.hc_network, I18nR.string.telos_network_help_desc),
            HelpAppItem("telos_store_app", BaseR.drawable.ic_app_store_full, I18nR.string.hf_store_title, I18nR.string.telos_store_help_desc),
            HelpAppItem("telos_phone_app", BaseR.drawable.ic_app_phone_full, I18nR.string.virtual_app_phone, I18nR.string.telos_phone_help_desc),
            HelpAppItem("telos_messages_app", BaseR.drawable.ic_app_messages_full, I18nR.string.hc_messages, I18nR.string.telos_messages_help_desc),
            HelpAppItem("telos_ermis_app", BaseR.drawable.ic_app_ermis_full, I18nR.string.ermis_title, I18nR.string.telos_ermis_help_desc),
            HelpAppItem("telos_akou_app", BaseR.drawable.ic_app_akou_full, I18nR.string.telos_akou_app_label, I18nR.string.telos_akou_help_desc),
            HelpAppItem("telos_sync_app", BaseR.drawable.ic_app_sync_full, I18nR.string.notes_sync_settings, I18nR.string.telos_sync_help_desc),
        )
    }

    VirtualAppWindow {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), Color.Transparent)))
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

            Text(
                text = stringResource(I18nR.string.telos_help_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(apps) { app ->
                    de.mm20.launcher2.ui.component.LauncherCard(
                        modifier = Modifier.clickable {
                            backStack.add(HelpAppDetailRoute(app.key))
                        }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            de.mm20.launcher2.ui.component.ShapedLauncherIcon(
                                size = 56.dp,
                                icon = {
                                    de.mm20.launcher2.icons.StaticLauncherIcon(
                                        de.mm20.launcher2.icons.StaticIconLayer(
                                            androidx.core.content.ContextCompat.getDrawable(context, app.iconRes)!!, 1f
                                        ),
                                        de.mm20.launcher2.icons.TransparentLayer
                                    )
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(app.nameRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    stringResource(app.descRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Icon(painterResource(BaseR.drawable.arrow_right_24px), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

data class HelpAppItem(val key: String, val iconRes: Int, val nameRes: Int, val descRes: Int)
