package de.mm20.launcher2.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TransparentLayer
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
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
    var query by rememberSaveable { mutableStateOf("") }
    val launcherTitle = stringResource(I18nR.string.telos_help_chapter_launcher)
    val launcherSum = stringResource(I18nR.string.telos_help_chapter_launcher_sum)
    val appsTitle = stringResource(I18nR.string.telos_help_chapter_apps)
    val appsSum = stringResource(I18nR.string.telos_help_chapter_apps_sum)
    val settingsTitle = stringResource(I18nR.string.telos_help_chapter_settings)
    val settingsSum = stringResource(I18nR.string.telos_help_chapter_settings_sum)
    val needle = query.trim().lowercase()

    val appTextMap = HelpCatalog.apps.associateWith { app ->
        (stringResource(app.nameRes) + " " + stringResource(app.descRes) + " " + stringResource(app.caveatRes)).lowercase()
    }
    val visibleApps = remember(needle, appTextMap) {
        HelpCatalog.apps.filter { app ->
            if (needle.isEmpty()) true
            else appTextMap[app]?.contains(needle) == true
        }
    }
    val showLauncher = needle.isEmpty() || launcherTitle.lowercase().contains(needle) || launcherSum.lowercase().contains(needle)
    val showSettings = needle.isEmpty() || settingsTitle.lowercase().contains(needle) || settingsSum.lowercase().contains(needle)
    val showAppsHeader = needle.isEmpty() || visibleApps.isNotEmpty()

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
                    modifier = Modifier.weight(1f).padding(start = if (backStack.size > 1) 0.dp else 8.dp),
                )
            }
            Text(
                text = stringResource(I18nR.string.telos_help_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text(stringResource(I18nR.string.telos_help_search_topics)) },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showLauncher) {
                    item(key = "ch-launcher") {
                        ChapterCard(
                            title = launcherTitle,
                            summary = launcherSum,
                            onClick = { backStack.add(HelpLessonRoute(HELP_TOPIC_LAUNCHER)) },
                        )
                    }
                }
                if (showSettings) {
                    item(key = "ch-settings") {
                        ChapterCard(
                            title = settingsTitle,
                            summary = settingsSum,
                            onClick = { backStack.add(HelpLessonRoute(HELP_TOPIC_SETTINGS)) },
                        )
                    }
                }
                if (showAppsHeader) {
                    item(key = "ch-apps") {
                        Text(
                            appsTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            appsSum,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(visibleApps, key = { it.key }) { app ->
                    AppRow(app) { backStack.add(HelpAppDetailRoute(app.key)) }
                }
            }
        }
    }
}

@Composable
private fun ChapterCard(title: String, summary: String, onClick: () -> Unit) {
    LauncherCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppRow(app: HelpAppLesson, onClick: () -> Unit) {
    val context = LocalContext.current
    LauncherCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ShapedLauncherIcon(
                size = 56.dp,
                icon = {
                    StaticLauncherIcon(
                        StaticIconLayer(ContextCompat.getDrawable(context, app.iconRes)!!, 1f),
                        TransparentLayer,
                    )
                },
            )
            Column(Modifier.weight(1f)) {
                Text(stringResource(app.nameRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(app.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Icon(painterResource(BaseR.drawable.arrow_right_24px), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
    }
}
