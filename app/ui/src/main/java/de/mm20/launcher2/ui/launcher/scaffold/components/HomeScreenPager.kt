package de.mm20.launcher2.ui.launcher.scaffold.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffoldState
import org.koin.compose.koinInject
import java.util.UUID

/**
 * Wraps a home component's content as page 1 of a [HorizontalPager], with extra swipeable pages
 * to the right (up to 9 total, see [UiSettings.homeScreenPageCount]) - each an independent widget
 * area reusing [WidgetsComponent] as-is, so add/move/resize/stack all work identically to the
 * dedicated Widgets screen.
 *
 * Deliberately self-contained: this pager owns horizontal drags on the home screen entirely by
 * itself rather than trying to hand off to LauncherScaffold's own (hand-rolled, non-nestedScroll)
 * gesture system at the last page - LauncherScaffold's swipeLeft/swipeRight *gesture actions*
 * (e.g. mapped to Feed) won't fire from the home screen while more than one page is configured.
 * That's a real, disclosed trade-off, not an oversight: map those actions to a different gesture
 * (long-press, double-tap, ...) if multi-page home screens are enabled. Vertical scrolling and
 * every other gesture are unaffected - HorizontalPager only claims the horizontal axis.
 *
 * When [UiSettings.homeScreenPageCount] is 1 (the default), this renders [firstPage] directly
 * with no pager involved at all - zero behavior change for anyone who hasn't opted in.
 */
@Composable
internal fun HomeScreenPager(
    modifier: Modifier,
    insets: PaddingValues,
    state: LauncherScaffoldState,
    firstPage: @Composable (Modifier, PaddingValues) -> Unit,
) {
    val uiSettings = koinInject<UiSettings>()
    val pageCount by uiSettings.homeScreenPageCount.collectAsStateWithLifecycle(1)

    if (pageCount <= 1) {
        firstPage(modifier, insets)
        return
    }

    val pagerState = rememberPagerState(pageCount = { pageCount })

    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            if (page == 0) {
                firstPage(Modifier.fillMaxSize(), insets)
            } else {
                val parentId = remember(page) { extraHomePageWidgetScopeId(page) }
                val component = remember(parentId) { WidgetsComponent.forId(parentId) }
                component.Component(Modifier.fillMaxSize(), insets, state)
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(insets)
                .padding(bottom = 12.dp),
        ) {
            for (page in 0 until pageCount) {
                val active = page == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (active) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                        )
                )
            }
        }
    }
}

/** Deterministic widget-repository scope id for extra home page [page] (1-based index within the
 * pager, i.e. page 1 is the second home screen). Independent of [de.mm20.launcher2.preferences.WidgetScreenTarget]
 * on purpose - these pages aren't individually reachable via a gesture mapping, so they shouldn't
 * show up in gesture-target pickers the way Widgets1-4 do. */
private fun extraHomePageWidgetScopeId(page: Int): UUID {
    return UUID.nameUUIDFromBytes("home_screen_page_$page".toByteArray())
}
