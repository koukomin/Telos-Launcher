package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import android.content.Context
import android.appwidget.AppWidgetManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.DockItem
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.searchable.PinnedLevel
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.ui.launcher.search.common.grid.GridItem
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FavoritesPartProvider : PartProvider, KoinComponent {

    private val searchableRepository: SavableSearchableRepository by inject()
    private val widgetRepository: WidgetRepository by inject()
    private val uiSettings: UiSettings by inject()

    override fun getRanking(context: Context): Flow<Int> = flow {
        emit(Int.MAX_VALUE)
    }

    @Composable
    override fun Component(compactLayout: Boolean) {
        val context = LocalContext.current
        val columns by uiSettings.dockColumns.collectAsState(5)
        val dockRows by uiSettings.dockRows.collectAsState(1)
        val dockPages by uiSettings.dockPages.collectAsState(emptyList())
        val defaultPage by uiSettings.dockDefaultPage.collectAsState(0)

        if (columns == 0) return

        if (dockPages.isEmpty()) {
            // Legacy / Auto-favorites mode
            val excludeCalendar by remember { widgetRepository.exists(CalendarWidget.Type) }.collectAsState(
                true
            )

            val favorites by remember(columns, dockRows, excludeCalendar) {
                searchableRepository.get(
                    excludeTypes = if (excludeCalendar) listOf("calendar", "tag") else listOf("tag"),
                    minPinnedLevel = PinnedLevel.FrequentlyUsed,
                    limit = 100 // Load more to support pagination
                )
            }.collectAsState(emptyList())

            val itemsPerPage = columns * dockRows
            if (itemsPerPage == 0) return
            val pageCount = (favorites.size + itemsPerPage - 1) / itemsPerPage
            val pagerState = rememberPagerState { pageCount.coerceAtLeast(1) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val start = page * itemsPerPage
                    val end = (start + itemsPerPage).coerceAtMost(favorites.size)
                    val pageItems = if (start < favorites.size) favorites.subList(start, end) else emptyList()

                    SearchResultGrid(
                        items = pageItems,
                        showLabels = false,
                        columns = columns,
                        transitionKey = page,
                        enableShutterGesture = true,
                    )
                }
            }
        } else {
            val pagerState = rememberPagerState(initialPage = defaultPage.coerceIn(0, dockPages.size - 1)) { dockPages.size }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
            ) { pageIndex ->
                val pageItems = dockPages[pageIndex]
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    for (row in 0 until dockRows) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until columns) {
                                val itemIndex = row * columns + col
                                val dockItem = pageItems.getOrNull(itemIndex)
                                Box(
                                    modifier = Modifier.weight(1f).aspectRatio(1f), 
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (dockItem != null) {
                                        when (dockItem) {
                                            is DockItem.Searchable -> {
                                                if (dockItem.key.isNotEmpty()) {
                                                    val searchables by remember(dockItem.key) {
                                                        searchableRepository.getByKeys(listOf(dockItem.key))
                                                    }.collectAsState(emptyList())
                                                    val searchable = searchables.firstOrNull()
                                                    searchable?.let {
                                                        GridItem(
                                                            item = it,
                                                            showLabels = false,
                                                            enableShutterGesture = true
                                                        )
                                                    }
                                                }
                                            }
                                            is DockItem.Widget -> {
                                                val widgetInfo = remember(dockItem.widgetId) {
                                                    AppWidgetManager.getInstance(context)
                                                        .getAppWidgetInfo(dockItem.widgetId)
                                                }
                                                if (widgetInfo != null) {
                                                    de.mm20.launcher2.ui.launcher.widgets.external.AppWidgetHost(
                                                        widgetId = dockItem.widgetId,
                                                        widgetInfo = widgetInfo,
                                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                                        borderless = true,
                                                        useThemeColors = true,
                                                        onLightBackground = !de.mm20.launcher2.ui.locals.LocalDarkTheme.current
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
