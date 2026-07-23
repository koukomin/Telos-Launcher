package de.mm20.launcher2.ui.launcher.search.webappshortcut

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.ToolbarAction
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.settings.webapps.EditWebAppShortcutSheet
import de.mm20.launcher2.ui.webappspanel.WebAppsPanelManager
import de.mm20.launcher2.webappshortcuts.WebAppShortcutRepository
import org.koin.compose.koinInject

@Composable
fun WebAppShortcutItem(
    modifier: Modifier = Modifier,
    shortcut: WebAppShortcut,
    onBack: (() -> Unit)? = null,
) {
    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${shortcut.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()

    LaunchedEffect(shortcut, iconSize) {
        viewModel.init(shortcut, iconSize.toInt())
    }

    Column(
        modifier = modifier
            .padding(16.dp),
    ) {
        Text(
            text = shortcut.labelOverride ?: shortcut.label,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            text = shortcut.url,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        val toolbarActions = mutableListOf<ToolbarAction>()

        if (LocalFavoritesEnabled.current) {
            val isPinned by viewModel.isPinned.collectAsState(false)
            val favAction = if (isPinned) {
                DefaultToolbarAction(
                    label = stringResource(R.string.menu_favorites_unpin),
                    icon = R.drawable.star_24px_filled,
                    action = {
                        viewModel.unpin()
                        onBack?.invoke()
                    }
                )
            } else {
                DefaultToolbarAction(
                    label = stringResource(R.string.menu_favorites_pin),
                    icon = R.drawable.star_24px,
                    action = {
                        viewModel.pin()
                        onBack?.invoke()
                    })
            }
            toolbarActions.add(favAction)
        }

        var showEditSheet by remember { mutableStateOf(false) }
        toolbarActions.add(
            DefaultToolbarAction(
                label = stringResource(R.string.web_app_shortcut_edit),
                icon = R.drawable.tune_24px,
                action = { showEditSheet = true }
            )
        )

        Toolbar(
            leftActions = if (onBack != null) listOf(
                DefaultToolbarAction(
                    stringResource(id = R.string.menu_back),
                    icon = R.drawable.arrow_back_24px,
                    action = onBack
                )
            ) else emptyList(),
            rightActions = toolbarActions
        )

        val webAppShortcutRepository: WebAppShortcutRepository = koinInject()
        val panelManager: WebAppsPanelManager = koinInject()
        EditWebAppShortcutSheet(
            expanded = showEditSheet,
            existing = shortcut,
            onSave = { label, url, iconUri, faviconUrl, rendererPackage, showInGrid, showInPanel, iconSource, customCss ->
                webAppShortcutRepository.update(
                    shortcut, label, url, iconUri, faviconUrl, rendererPackage,
                    showInGrid, showInPanel, shortcut.order, iconSource, customCss,
                )
                showEditSheet = false
            },
            onDismiss = { showEditSheet = false },
            onImportIcon = { uri, sizePx -> panelManager.importIcon(uri, sizePx) },
            onFindFavicon = { url -> panelManager.findFavicon(url) },
            onExportIconPackIcon = { customIcon, sizePx -> panelManager.exportIconPackIcon(customIcon, sizePx) },
        )
    }
}

@Composable
fun WebAppShortcutItemGridPopup(
    shortcut: WebAppShortcut,
    show: MutableTransitionState<Boolean>,
    origin: IntRect,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        show,
        enter = expandIn(
            animationSpec = tween(300),
            expandFrom = Alignment.Center,
        ) { origin.size },
        exit = shrinkOut(
            animationSpec = tween(300),
            shrinkTowards = Alignment.Center,
        ) { origin.size },
    ) {
        WebAppShortcutItem(
            modifier = Modifier.fillMaxWidth(),
            shortcut = shortcut,
            onBack = onDismiss,
        )
    }
}
