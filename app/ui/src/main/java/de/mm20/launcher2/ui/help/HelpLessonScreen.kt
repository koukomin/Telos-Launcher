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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.VirtualAppWindow
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
data class HelpLessonRoute(val topicId: String) : NavKey

@Composable
fun HelpLessonScreen(topicId: String) {
    val backStack = LocalBackStack.current
    val context = LocalContext.current
    val steps = HelpCatalog.stepsFor(topicId)
    var step by rememberSaveable { mutableIntStateOf(0) }
    val index = step.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
    val current = steps.getOrNull(index)

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
                    text = stringResource(
                        if (topicId == HELP_TOPIC_SETTINGS) I18nR.string.telos_help_chapter_settings
                        else I18nR.string.telos_help_chapter_launcher
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            }
            if (current == null) return@Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    stringResource(I18nR.string.telos_help_step, index + 1, steps.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                LauncherCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            stringResource(current.titleRes),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(current.bodyRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current.tryRoute != null) {
                    OutlinedButton(
                        onClick = { openHelpRoute(context, current.tryRoute) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(I18nR.string.telos_help_try_it))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { if (index > 0) step = index - 1 else backStack.removeLastOrNull() }) {
                        Text(stringResource(if (index > 0) I18nR.string.hc_back else I18nR.string.widgets_tutorial_skip))
                    }
                    Button(onClick = {
                        if (index < steps.lastIndex) step = index + 1 else backStack.removeLastOrNull()
                    }) {
                        Text(
                            stringResource(
                                if (index < steps.lastIndex) I18nR.string.widgets_tutorial_next
                                else I18nR.string.widgets_tutorial_done
                            )
                        )
                    }
                }
            }
        }
    }
}
