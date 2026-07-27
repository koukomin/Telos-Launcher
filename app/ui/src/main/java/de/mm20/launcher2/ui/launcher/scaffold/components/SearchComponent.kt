package de.mm20.launcher2.ui.launcher.scaffold.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.ui.GridSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.ui.launcher.scaffold.Gesture
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffoldState
import de.mm20.launcher2.ui.launcher.search.SearchColumn
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.locals.LocalGridSettings
import org.koin.compose.koinInject

internal class SearchComponent(
    private val reverse: Boolean = false,
    private val openKeyboard: Boolean = true,
) : ScaffoldComponent() {

    override val isAtTop: MutableState<Boolean?> = mutableStateOf(true)

    override val isAtBottom: MutableState<Boolean?> = mutableStateOf(true)

    override val reverseScrolling: Boolean = reverse

    override val hasIme: Boolean = true


    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        val uiSettings: UiSettings = koinInject()
        val gridSettings by uiSettings.drawerGridSettings.collectAsState(GridSettings())
        val drawerBackgroundEnabled by uiSettings.drawerBackgroundEnabled.collectAsState(false)
        val drawerBackgroundColor by uiSettings.drawerBackgroundColor.collectAsState(null)
        val drawerBackgroundOpacity by uiSettings.drawerBackgroundOpacity.collectAsState(0.9f)
        val rememberScrollPosition by uiSettings.rememberScrollPosition.collectAsState(false)

        val searchVM = viewModel<SearchVM>()
        val lazyListState = rememberLazyListState()

        LaunchedEffect(isActive) {
            if (!isActive) {
                searchVM.reset()
                if (!rememberScrollPosition) {
                    lazyListState.scrollToItem(0, 0)
                }
            }
        }

        LaunchedEffect(searchVM.searchQuery.value, searchVM.filters.value) {
            if (searchVM.searchQuery.value.isNotEmpty() || !rememberScrollPosition) {
                lazyListState.requestScrollToItem(0, 0)
            }
        }

        LaunchedEffect(lazyListState.canScrollForward, lazyListState.canScrollBackward) {
            isAtBottom.value =
                !lazyListState.canScrollForward && !reverse || !lazyListState.canScrollBackward && reverse
            isAtTop.value =
                !lazyListState.canScrollForward && reverse || !lazyListState.canScrollBackward && !reverse
        }


        val scrollConnection = remember(state) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    searchVM.bestMatch.value = null
                    state.isSearchBarFocused = false
                    state.onComponentScroll(
                        if (reverse) consumed.y else -consumed.y,
                    )
                    return super.onPostScroll(consumed, available, source)
                }
            }
        }

        CompositionLocalProvider(LocalGridSettings provides gridSettings) {
            Box(
                modifier = if (drawerBackgroundEnabled) {
                    val color = drawerBackgroundColor?.let { androidx.compose.ui.graphics.Color(it) }
                        ?: if (de.mm20.launcher2.ui.locals.LocalDarkTheme.current) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
                    modifier.background(color.copy(alpha = drawerBackgroundOpacity))
                } else {
                    modifier
                },
                contentAlignment = Alignment.Center
            ) {

                SearchColumn(
                    modifier = Modifier.nestedScroll(scrollConnection).widthIn(max = 916.dp).fillMaxHeight(),
                    paddingValues = insets,
                    state = lazyListState,
                    reverse = reverse,
                    userScrollEnabled = !state.isDragged,
                    onHideKeyboard = {
                        state.isSearchBarFocused = false
                    }
                )
            }
        }
    }

    override suspend fun onDismiss(state: LauncherScaffoldState) {
        super.onDismiss(state)
    }

    override fun onPreActivate(state: LauncherScaffoldState) {
        super.onPreActivate(state)
        // Swipe-down is a "peek at search" gesture, not a deliberate tap into the text field -
        // opening the keyboard immediately on every swipe down is intrusive, and the user can
        // still tap the search bar explicitly to focus it (that path is unaffected, see
        // LauncherScaffold's onFocusChange handling).
        if (openKeyboard && state.currentGesture != Gesture.SwipeDown) {
            state.isSearchBarFocused = true
        }
    }

    override fun onPreDismiss(state: LauncherScaffoldState) {
        super.onPreDismiss(state)
        state.isSearchBarFocused = false
    }
}