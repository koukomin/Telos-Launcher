package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import android.content.Context
import android.appwidget.AppWidgetManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.DockItem
import de.mm20.launcher2.preferences.ui.GridSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.searchable.PinnedLevel
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.common.grid.GridItem
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import de.mm20.launcher2.ui.common.SearchablePicker
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.launcher.sheets.FolderCreationSheet
import de.mm20.launcher2.widgets.AppWidget
import org.koin.compose.koinInject
import de.mm20.launcher2.applications.FolderImpl
import java.util.UUID

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
        val gridSettings by uiSettings.dockGridSettings.collectAsState(GridSettings())
        val columns by uiSettings.dockColumns.collectAsState(5)
        val dockRows by uiSettings.dockRows.collectAsState(1)
        val dockPages by uiSettings.dockPages.collectAsState(emptyList())
        val defaultPage by uiSettings.dockDefaultPage.collectAsState(0)

        val desktopLocked by uiSettings.desktopLocked.collectAsState(false)
        val dockBackgroundEnabled by uiSettings.dockBackgroundEnabled.collectAsState(false)
        val dockBackgroundColor by uiSettings.dockBackgroundColor.collectAsState(null)
        val dockBackgroundOpacity by uiSettings.dockBackgroundOpacity.collectAsState(0.3f)
        val dockBackgroundShadow by uiSettings.dockBackgroundShadow.collectAsState(0)

        var showSlotMenuForSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var showSearchablePickerForSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var showFolderCreationForSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var showWidgetActionsForSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var showWidgetPickerForSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }

        if (columns == 0) return

        CompositionLocalProvider(LocalGridSettings provides gridSettings) {
            Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
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
                    if (itemsPerPage > 0) {
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
                                    centerRows = true,
                                )
                            }
                        }
                    }
                } else {
                    val pagerState = rememberPagerState(initialPage = defaultPage.coerceIn(0, dockPages.size - 1)) { dockPages.size }

                    // Box, not Column: the background plate and the pager need to OVERLAP (plate
                    // behind, pager on top), not stack as separate vertical siblings - a Column
                    // here would give the plate its own vertical slot above the pager instead of
                    // sitting behind it.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        if (dockBackgroundEnabled) {
                            val color = dockBackgroundColor?.let { Color(it) }
                                ?: if (de.mm20.launcher2.ui.locals.LocalDarkTheme.current) Color.Black else Color.White

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .fillMaxWidth()
                                    .height(gridSettings.iconSize.dp * dockRows + 16.dp)
                                    .let {
                                        if (dockBackgroundShadow > 0) {
                                            it.shadow(dockBackgroundShadow.dp, MaterialTheme.shapes.large)
                                        } else {
                                            it
                                        }
                                    }
                                    .clip(MaterialTheme.shapes.large)
                                    .background(color.copy(alpha = dockBackgroundOpacity))
                            )
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth().wrapContentHeight()
                        ) { pageIndex ->
                            val pageItems = dockPages[pageIndex]
                            
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                for (row in 0 until dockRows) {
                                    Row(
                                        modifier = Modifier.wrapContentWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        for (col in 0 until columns) {
                                            val itemIndex = row * columns + col
                                            val dockItem = pageItems.getOrNull(itemIndex)
                                            Box(
                                                modifier = Modifier
                                                    .size(gridSettings.iconSize.dp + 16.dp)
                                                    .combinedClickable(
                                                        onClick = {
                                                            // Handle click if needed
                                                        },
                                                        onLongClick = {
                                                            if (!desktopLocked && (dockItem == null || (dockItem is DockItem.Searchable && dockItem.key.isEmpty()))) {
                                                                showSlotMenuForSlot = pageIndex to itemIndex
                                                            }
                                                        }
                                                    ), 
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
                                                                        enableShutterGesture = true,
                                                                        inDock = true,
                                                                        onRemoveFromDock = if (desktopLocked) null else {
                                                                            {
                                                                                val updated = dockPages.toMutableList()
                                                                                val page = updated[pageIndex].toMutableList()
                                                                                page[itemIndex] = DockItem.Searchable("")
                                                                                updated[pageIndex] = page
                                                                                uiSettings.setDockPages(updated)
                                                                            }
                                                                        },
                                                                        onReplaceInDock = if (desktopLocked) null else {
                                                                            {
                                                                                showSearchablePickerForSlot = pageIndex to itemIndex
                                                                            }
                                                                        }
                                                                    )
                                                                }
                                                            } else {
                                                                // Placeholder icon
                                                                Icon(
                                                                    painter = androidx.compose.ui.res.painterResource(R.drawable.add_24px),
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                                )
                                                            }
                                                        }
                                                        is DockItem.Widget -> {
                                                            val widgetInfo = remember(dockItem.widgetId) {
                                                                AppWidgetManager.getInstance(context)
                                                                    .getAppWidgetInfo(dockItem.widgetId)
                                                            }
                                                            if (widgetInfo != null) {
                                                                Box(modifier = Modifier.fillMaxSize().combinedClickable(
                                                                    onClick = {},
                                                                    onLongClick = {
                                                                        if (!desktopLocked) {
                                                                            showWidgetActionsForSlot = pageIndex to itemIndex
                                                                        }
                                                                    }
                                                                )) {
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
            }
        }

        if (showSlotMenuForSlot != null) {
            val (pageIdx, itemIdx) = showSlotMenuForSlot!!
            DismissableBottomSheet(
                expanded = true,
                onDismissRequest = { showSlotMenuForSlot = null }
            ) {
                Column(modifier = Modifier.padding(16.dp).navigationBarsPadding()) {
                    Preference(
                        title = "Add app",
                        icon = R.drawable.add_24px,
                        onClick = {
                            showSearchablePickerForSlot = pageIdx to itemIdx
                            showSlotMenuForSlot = null
                        }
                    )
                    Preference(
                        title = "Add folder",
                        icon = de.mm20.launcher2.base.R.drawable.folder_24px,
                        onClick = {
                            showFolderCreationForSlot = pageIdx to itemIdx
                            showSlotMenuForSlot = null
                        }
                    )
                }
            }
        }

        if (showSearchablePickerForSlot != null) {
            DismissableBottomSheet(
                expanded = true,
                onDismissRequest = { showSearchablePickerForSlot = null }
            ) {
                SearchablePicker(
                    value = null,
                    onValueChanged = { searchable ->
                        if (searchable != null) {
                            val (pageIdx, itemIdx) = showSearchablePickerForSlot!!
                            val updated = dockPages.toMutableList()
                            val page = updated[pageIdx].toMutableList()
                            
                            while (page.size <= itemIdx) {
                                page.add(DockItem.Searchable(""))
                            }
                            
                            page[itemIdx] = DockItem.Searchable(searchable.key)
                            updated[pageIdx] = page
                            uiSettings.setDockPages(updated)
                        }
                        showSearchablePickerForSlot = null
                    },
                    contentPadding = PaddingValues(16.dp)
                )
            }
        }

        if (showFolderCreationForSlot != null) {
            val (pageIdx, itemIdx) = showFolderCreationForSlot!!
            FolderCreationSheet(
                expanded = true,
                onDismiss = { showFolderCreationForSlot = null },
                onCreate = { name, isCover ->
                    val folder = FolderImpl(
                        id = UUID.randomUUID().toString(),
                        label = name,
                        itemKeys = emptyList(),
                        isCover = isCover
                    )
                    searchableRepository.insert(folder)
                    
                    val updated = dockPages.toMutableList()
                    val page = updated[pageIdx].toMutableList()
                    while (page.size <= itemIdx) {
                        page.add(DockItem.Searchable(""))
                    }
                    page[itemIdx] = DockItem.Searchable(folder.key)
                    updated[pageIdx] = page
                    uiSettings.setDockPages(updated)
                    
                    showFolderCreationForSlot = null
                }
            )
        }

        if (showWidgetActionsForSlot != null) {
            val (pageIdx, itemIdx) = showWidgetActionsForSlot!!
            DismissableBottomSheet(
                expanded = true,
                onDismissRequest = { showWidgetActionsForSlot = null }
            ) {
                Column(modifier = Modifier.padding(16.dp).navigationBarsPadding()) {
                    Preference(
                        title = stringResource(R.string.dock_menu_replace),
                        icon = R.drawable.autorenew_24px,
                        onClick = {
                            showWidgetPickerForSlot = pageIdx to itemIdx
                            showWidgetActionsForSlot = null
                        }
                    )
                    Preference(
                        title = stringResource(R.string.dock_menu_remove),
                        icon = R.drawable.delete_24px,
                        onClick = {
                            val updated = dockPages.toMutableList()
                            val page = updated[pageIdx].toMutableList()
                            page[itemIdx] = DockItem.Searchable("")
                            updated[pageIdx] = page
                            uiSettings.setDockPages(updated)
                            showWidgetActionsForSlot = null
                        }
                    )
                }
            }
        }

        if (showWidgetPickerForSlot != null) {
            de.mm20.launcher2.ui.launcher.sheets.WidgetPickerSheet(
                expanded = true,
                onDismiss = { showWidgetPickerForSlot = null },
                onWidgetSelected = { widget ->
                    val (pageIdx, itemIdx) = showWidgetPickerForSlot!!
                    val updated = dockPages.toMutableList()
                    val page = updated[pageIdx].toMutableList()
                    if (widget is AppWidget) {
                        page[itemIdx] = DockItem.Widget(
                            widgetId = widget.config.widgetId,
                            providerPackage = "",
                            providerClassName = ""
                        )
                        updated[pageIdx] = page
                        uiSettings.setDockPages(updated)
                    }
                    showWidgetPickerForSlot = null
                }
            )
        }
    }
}
