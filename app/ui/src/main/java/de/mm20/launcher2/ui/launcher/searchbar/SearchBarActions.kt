package de.mm20.launcher2.ui.launcher.searchbar
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.ui.AgentSettings
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.agents.AgentCatalog
import de.mm20.launcher2.ui.agents.AgentLauncher
import org.koin.compose.koinInject
import de.mm20.launcher2.ui.component.SearchActionIcon
import de.mm20.launcher2.ui.modifier.consumeAllScrolling
import de.mm20.launcher2.ui.settings.SettingsActivity

@Composable
fun ColumnScope.SearchBarActions(
    modifier: Modifier = Modifier,
    actions: List<SearchAction>,
    highlightedAction: SearchAction?,
    reverse: Boolean = false,
    searchQuery: String = "",
) {
    val context = LocalContext.current
    val agentSettings: AgentSettings = koinInject()
    val agentsEnabled by agentSettings.enabled.collectAsState(false)
    val agentId by agentSettings.agentId.collectAsState("grok")
    val transport by agentSettings.transport.collectAsState("auto")
    val agent = AgentCatalog.byId(agentId)
    val showAgent = agentsEnabled && searchQuery.isNotBlank()
    AnimatedVisibility(actions.isNotEmpty() || showAgent) {
        LazyRow(
            modifier = Modifier
                .consumeAllScrolling()
                .heightIn(min = 56.dp)
                .padding(bottom = if (reverse) 0.dp else 8.dp, top = if (reverse) 8.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(start = 8.dp, end = 4.dp)
        ) {
            if (showAgent) {
                item(key = "agent") {
                    AssistChip(
                        modifier = Modifier.padding(4.dp),
                        onClick = {
                            AgentLauncher.send(context, searchQuery, agentId, transport)
                        },
                        label = { Text(stringResource(I18nR.string.agent_ask, stringResource(agent.labelRes))) },
                    )
                }
            }
            items(actions) {
                AssistChip(
                    modifier = Modifier.padding(4.dp),
                    colors = if (it == highlightedAction) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            trailingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    } else AssistChipDefaults.assistChipColors(),
                    border = if (it == highlightedAction) {
                        AssistChipDefaults.assistChipBorder(
                            true,
                            borderColor = MaterialTheme.colorScheme.secondary,
                        )
                    } else AssistChipDefaults.assistChipBorder(true),
                    onClick = {
                        it.start(context)
                    },
                    label = { Text(it.label) },
                    leadingIcon = {
                        SearchActionIcon(
                            action = it,
                            size = AssistChipDefaults.IconSize,
                        )
                    }
                )
            }
            item {
                SmallFloatingActionButton(
                    modifier = Modifier.padding(start = 4.dp),
                    elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                    onClick = {
                        context.startActivity(
                            Intent(context, SettingsActivity::class.java).apply {
                                putExtra(SettingsActivity.EXTRA_ROUTE, SettingsActivity.ROUTE_SEARCH_ACTIONS)
                            }
                        )
                    }
                ) {

                    Icon(
                        painterResource(BaseR.drawable.edit_24px),
                        contentDescription = stringResource(I18nR.string.preference_screen_search_actions)
                    )
                }
            }
        }
    }
}
