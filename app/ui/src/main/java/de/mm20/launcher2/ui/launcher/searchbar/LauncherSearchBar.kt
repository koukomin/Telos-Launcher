package de.mm20.launcher2.ui.launcher.searchbar
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.material3.Badge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.SearchBar
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager

@Composable
fun LauncherSearchBar(
    modifier: Modifier = Modifier,
    style: SearchBarStyle,
    level: () -> SearchBarLevel,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    actions: List<SearchAction>,
    highlightedAction: SearchAction? = null,
    isSearchOpen: Boolean = false,
    darkColors: Boolean = false,
    bottomSearchBar: Boolean = false,
    searchBarOffset: () -> Int = { 0 },
    onKeyboardActionGo: (KeyboardActionScope.() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val searchVM: SearchVM = viewModel()
    val voiceEnabled by searchVM.searchBarVoice.collectAsState(true)
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            searchVM.search(spoken)
        }
    }
    val readabilityUiSettings = org.koin.compose.koinInject<de.mm20.launcher2.preferences.ui.UiSettings>()
    val searchBarColors by remember { readabilityUiSettings.searchBarColor }
        .collectAsState(de.mm20.launcher2.preferences.SearchBarColors.Auto)
    val hiddenItemsButtonEnabled by searchVM.hiddenResultsButton.collectAsState(false)
    val hiddenItems = searchVM.hiddenResults

    val sheetManager = LocalBottomSheetManager.current

    LaunchedEffect(focused) {
        if (focused) focusRequester.requestFocus()
        else focusManager.clearFocus()
    }

    val value by searchVM.searchQuery

    Box(modifier = modifier) {
        SearchBar(
            modifier = Modifier
                .align(if (bottomSearchBar) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(8.dp)
                .offset { IntOffset(0, searchBarOffset()) },
            style = style, level = level(), value = value, onValueChange = {
                searchVM.search(it)
            },
            reverse = bottomSearchBar,
            darkColors = darkColors,
            automaticColors = !isSearchOpen && searchBarColors == de.mm20.launcher2.preferences.SearchBarColors.Auto,
            menu = {
                AnimatedVisibility(
                    hiddenItemsButtonEnabled && isSearchOpen && hiddenItems.isNotEmpty(),
                    enter = scaleIn(tween(100)),
                    exit = scaleOut(tween(100))
                ) {
                    IconToggleButton(
                        checked = sheetManager.hiddenItemsSheetShown.value,
                        onCheckedChange = { if (it) sheetManager.showHiddenItemsSheet() },
                    ) {
                        Icon(
                            painter = painterResource(
                                if (sheetManager.hiddenItemsSheetShown.value) BaseR.drawable.visibility_off_24px_filled
                                else BaseR.drawable.visibility_off_24px
                            ),
                            contentDescription = stringResource(I18nR.string.preference_hidden_items)
                        )
                    }
                }
                AnimatedVisibility(
                    isSearchOpen,
                    enter = scaleIn(tween(100)),
                    exit = scaleOut(tween(100))
                ) {
                    IconToggleButton(
                        checked = searchVM.showFilters.value,
                        onCheckedChange = {
                            searchVM.showFilters.value = it
                        },
                    ) {
                        Box {
                            Icon(
                                painter = painterResource(
                                    if (searchVM.showFilters.value)BaseR.drawable.filter_alt_24px_filled
                                    else BaseR.drawable.filter_alt_24px
                                ),
                                contentDescription = stringResource(
                                    if (searchVM.showFilters.value) I18nR.string.menu_hide_filters else I18nR.string.menu_show_filters
                                )
                            )
                            androidx.compose.animation.AnimatedVisibility(
                                !searchVM.filters.value.allCategoriesEnabled,
                                enter = scaleIn(tween(100)),
                                exit = scaleOut(tween(100)),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(-3.dp, -3.dp)
                            ) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
                SecurityHubStatusPill()
                SearchBarMenu(searchBarValue = value, onInputClear = {
                    searchVM.reset()
                })
            },
            actions = {
                SearchBarActions(
                    actions = actions,
                    reverse = bottomSearchBar,
                    highlightedAction = highlightedAction,
                    searchQuery = value,
                )
            },
            focusRequester = focusRequester,
            onFocus = { onFocusChange(true) },
            onUnfocus = { onFocusChange(false) },
            onKeyboardActionGo = onKeyboardActionGo,
            trailing = {
                if (voiceEnabled) {
                    IconButton(
                        onClick = {
                            val intent = android.content.Intent(
                                android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                            ).apply {
                                putExtra(
                                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                )
                            }
                            runCatching { speechLauncher.launch(intent) }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.mic_24px),
                            contentDescription = stringResource(I18nR.string.search_bar_voice),
                        )
                    }
                }
            },
        )
    }
}