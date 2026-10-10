package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.ColorScheme
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.IconShape
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.ScreenOrientation
import de.mm20.launcher2.preferences.SearchBarColors
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.preferences.SystemBarColors
import de.mm20.launcher2.preferences.WidgetScreenTarget
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

data class CardStyle(
    val opacity: Float = 1f,
    val borderWidth: Int = 0,
)

data class GridSettings(
    val columnCount: Int = 5,
    val iconSize: Int = 48,
    val showLabels: Boolean = true,
    val labelSize: Float = 12f,
    val labelMaxLines: Int = 1,
    val labelShadow: Boolean = false,
    val labelColor: Int? = null,
    val showList: Boolean = false,
    val showListIcons: Boolean = true,
)

class UiSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {
    val favoritesEnabled
        get() = launcherDataStore.data.map { it.favorites.favoritesEnabled || it.home.homeScreenDock }

    val iconShape
        get() = launcherDataStore.data.map {
            it.icons.iconsShape
        }

    fun setIconShape(iconShape: IconShape) {
        launcherDataStore.update {
            it.copy(icons = it.icons.copy(iconsShape = iconShape))
        }
    }

    val gridSettings
        get() = launcherDataStore.data.map {
            GridSettings(
                showLabels = it.grid.gridLabels,
                labelSize = it.grid.gridLabelSize,
                labelMaxLines = it.grid.gridLabelMaxLines,
                labelShadow = it.grid.gridLabelShadow,
                labelColor = it.grid.gridLabelColor,
                showList = it.grid.gridList,
                showListIcons = it.grid.gridListIcons,
                iconSize = it.grid.gridIconSize,
                columnCount = it.grid.gridColumnCount,
            )
        }

    fun setGridColumnCount(columnCount: Int) {
        launcherDataStore.update {
            it.copy(grid = it.grid.copy(gridColumnCount = columnCount))
        }
    }

    fun setGridIconSize(iconSize: Int) {
        launcherDataStore.update {
            it.copy(grid = it.grid.copy(gridIconSize = iconSize))
        }
    }

    fun setGridShowLabels(showLabels: Boolean) {
        launcherDataStore.update {
            it.copy(grid = it.grid.copy(gridLabels = showLabels))
        }
    }

    val homeGridSettings
        get() = launcherDataStore.data.map {
            GridSettings(
                showLabels = it.grid.gridLabels,
                labelSize = it.grid.gridLabelSize,
                labelMaxLines = it.grid.gridLabelMaxLines,
                labelShadow = it.grid.gridLabelShadow,
                labelColor = it.grid.gridLabelColor,
                showList = it.grid.gridList,
                showListIcons = it.grid.gridListIcons,
                iconSize = it.grid.homeGridIconSize ?: it.grid.gridIconSize,
                columnCount = it.grid.homeGridColumnCount ?: it.grid.gridColumnCount,
            )
        }

    fun setHomeGridColumnCount(columnCount: Int?) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(homeGridColumnCount = columnCount)) }
    }

    fun setHomeGridIconSize(iconSize: Int?) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(homeGridIconSize = iconSize)) }
    }

    val drawerGridSettings
        get() = launcherDataStore.data.map {
            GridSettings(
                showLabels = it.grid.gridLabels,
                labelSize = it.grid.gridLabelSize,
                labelMaxLines = it.grid.gridLabelMaxLines,
                labelShadow = it.grid.gridLabelShadow,
                labelColor = it.grid.gridLabelColor,
                showList = it.grid.gridList,
                showListIcons = it.grid.gridListIcons,
                iconSize = it.grid.drawerGridIconSize ?: it.grid.gridIconSize,
                columnCount = it.grid.drawerGridColumnCount ?: it.grid.gridColumnCount,
            )
        }

    fun setDrawerGridColumnCount(columnCount: Int?) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(drawerGridColumnCount = columnCount)) }
    }

    fun setDrawerGridIconSize(iconSize: Int?) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(drawerGridIconSize = iconSize)) }
    }

    val dockGridSettings
        get() = launcherDataStore.data.map {
            GridSettings(
                showLabels = it.grid.gridLabels,
                labelSize = it.grid.gridLabelSize,
                labelMaxLines = it.grid.gridLabelMaxLines,
                labelShadow = it.grid.gridLabelShadow,
                labelColor = it.grid.gridLabelColor,
                showList = it.grid.gridList,
                showListIcons = it.grid.gridListIcons,
                iconSize = it.grid.dockGridIconSize ?: it.grid.gridIconSize,
                columnCount = it.grid.dockGridColumnCount ?: it.grid.gridColumnCount,
            )
        }

    fun setDockGridColumnCount(columnCount: Int?) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(dockGridColumnCount = columnCount)) }
    }

    fun setDockGridIconSize(iconSize: Int?) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(dockGridIconSize = iconSize)) }
    }

    fun setGridLabelSize(size: Float) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(gridLabelSize = size)) }
    }

    fun setGridLabelMaxLines(lines: Int) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(gridLabelMaxLines = lines)) }
    }

    fun setGridLabelShadow(enabled: Boolean) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(gridLabelShadow = enabled)) }
    }

    fun setGridLabelColor(color: Int?) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(gridLabelColor = color)) }
    }

    val desktopLocked
        get() = launcherDataStore.data.map { it.grid.desktopLocked }.distinctUntilChanged()

    fun setDesktopLocked(locked: Boolean) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(desktopLocked = locked)) }
    }

    val dockBackgroundEnabled
        get() = launcherDataStore.data.map { it.grid.dockBackgroundEnabled }.distinctUntilChanged()

    fun setDockBackgroundEnabled(enabled: Boolean) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(dockBackgroundEnabled = enabled)) }
    }

    val dockBackgroundColor
        get() = launcherDataStore.data.map { it.grid.dockBackgroundColor }.distinctUntilChanged()

    fun setDockBackgroundColor(color: Int?) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(dockBackgroundColor = color)) }
    }

    val dockBackgroundOpacity
        get() = launcherDataStore.data.map { it.grid.dockBackgroundOpacity }.distinctUntilChanged()

    fun setDockBackgroundOpacity(opacity: Float) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(dockBackgroundOpacity = opacity)) }
    }

    val dockBackgroundBlur
        get() = launcherDataStore.data.map { it.grid.dockBackgroundBlur }.distinctUntilChanged()

    fun setDockBackgroundBlur(radius: Int) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(dockBackgroundBlur = radius)) }
    }

    val dockBackgroundShadow
        get() = launcherDataStore.data.map { it.grid.dockBackgroundShadow }.distinctUntilChanged()

    fun setDockBackgroundShadow(elevation: Int) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(dockBackgroundShadow = elevation)) }
    }

    val drawerBackgroundEnabled
        get() = launcherDataStore.data.map { it.grid.drawerBackgroundEnabled }.distinctUntilChanged()

    fun setDrawerBackgroundEnabled(enabled: Boolean) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(drawerBackgroundEnabled = enabled)) }
    }

    val drawerBackgroundColor
        get() = launcherDataStore.data.map { it.grid.drawerBackgroundColor }.distinctUntilChanged()

    fun setDrawerBackgroundColor(color: Int?) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(drawerBackgroundColor = color)) }
    }

    val drawerBackgroundOpacity
        get() = launcherDataStore.data.map { it.grid.drawerBackgroundOpacity }.distinctUntilChanged()

    fun setDrawerBackgroundOpacity(opacity: Float) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(drawerBackgroundOpacity = opacity)) }
    }

    val folderBackgroundColor
        get() = launcherDataStore.data.map { it.grid.folderBackgroundColor }.distinctUntilChanged()

    fun setFolderBackgroundColor(color: Int?) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(folderBackgroundColor = color)) }
    }

    val dockPageIndicatorEnabled
        get() = launcherDataStore.data.map { it.grid.dockPageIndicatorEnabled }.distinctUntilChanged()

    fun setDockPageIndicatorEnabled(enabled: Boolean) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(dockPageIndicatorEnabled = enabled)) }
    }

    val dockPageIndicatorColor
        get() = launcherDataStore.data.map { it.grid.dockPageIndicatorColor }.distinctUntilChanged()

    fun setDockPageIndicatorColor(color: Int?) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(dockPageIndicatorColor = color)) }
    }

    val folderCoverEnabled
        get() = launcherDataStore.data.map { it.grid.folderCoverEnabled }.distinctUntilChanged()

    fun setFolderCoverEnabled(enabled: Boolean) {
        launcherDataStore.update { it.copy(grid = it.grid.copy(folderCoverEnabled = enabled)) }
    }

    fun setGridShowList(showList: Boolean) {
        launcherDataStore.update {
            it.copy(grid = it.grid.copy(gridList = showList))
        }
    }

    fun setGridShowListIcons(showIcons: Boolean) {
        launcherDataStore.update {
            it.copy(grid = it.grid.copy(gridListIcons = showIcons))
        }
    }

    val cardStyle
        get() = launcherDataStore.data.map {
            CardStyle(
                opacity = it.surfaces.surfacesOpacity,
                borderWidth = it.surfaces.surfacesBorderWidth,
            )
        }

    fun setCardOpacity(opacity: Float) {
        launcherDataStore.update {
            it.copy(surfaces = it.surfaces.copy(surfacesOpacity = opacity))
        }
    }

    fun setCardBorderWidth(borderWidth: Int) {
        launcherDataStore.update {
            it.copy(surfaces = it.surfaces.copy(surfacesBorderWidth = borderWidth))
        }
    }

    val colorScheme
        get() = launcherDataStore.data.map {
            it.ui.uiColorScheme
        }.distinctUntilChanged()

    val colorSchemeNightStart
        get() = launcherDataStore.data.map {
            it.ui.uiColorSchemeNightStart
        }.distinctUntilChanged()

    val colorSchemeDayStart
        get() = launcherDataStore.data.map {
            it.ui.uiColorSchemeDayStart
        }.distinctUntilChanged()

    val compatModeColors
        get() = launcherDataStore.data.map {
            it.ui.uiCompatModeColors
        }.distinctUntilChanged()

    fun setCompatModeColors(enabled: Boolean) {
        launcherDataStore.update {
            it.copy(ui = it.ui.copy(uiCompatModeColors = enabled))
        }
    }

    val statusBarColor
        get() = launcherDataStore.data.map {
            it.systemBars.systemBarsStatusColors
        }.distinctUntilChanged()

    val hideStatusBar
        get() = launcherDataStore.data.map {
            it.systemBars.systemBarsHideStatus
        }.distinctUntilChanged()

    val hideNavigationBar
        get() = launcherDataStore.data.map {
            it.systemBars.systemBarsHideNav
        }.distinctUntilChanged()

    fun setHideStatusBar(hideStatusBar: Boolean) {
        launcherDataStore.update {
            it.copy(systemBars = it.systemBars.copy(systemBarsHideStatus = hideStatusBar))
        }
    }

    fun setHideNavigationBar(hideNavigationBar: Boolean) {
        launcherDataStore.update {
            it.copy(systemBars = it.systemBars.copy(systemBarsHideNav = hideNavigationBar))
        }
    }

    val navigationBarColor
        get() = launcherDataStore.data.map {
            it.systemBars.systemBarsNavColors
        }.distinctUntilChanged()

    fun setStatusBarColor(statusBarColor: SystemBarColors) {
        launcherDataStore.update {
            it.copy(systemBars = it.systemBars.copy(systemBarsStatusColors = statusBarColor))
        }
    }

    fun setNavigationBarColor(navigationBarColor: SystemBarColors) {
        launcherDataStore.update {
            it.copy(systemBars = it.systemBars.copy(systemBarsNavColors = navigationBarColor))
        }
    }

    val chargingAnimation
        get() = launcherDataStore.data.map {
            it.animations.animationsCharging
        }.distinctUntilChanged()

    fun setChargingAnimation(chargingAnimation: Boolean) {
        launcherDataStore.update {
            it.copy(animations = it.animations.copy(animationsCharging = chargingAnimation))
        }
    }

    val clockFillScreen
        get() = launcherDataStore.data.map {
            it.home.homeScreenWidgets
        }.distinctUntilChanged()

    val searchBarStyle
        get() = launcherDataStore.data.map {
            it.searchBar.searchBarStyle
        }.distinctUntilChanged()

    fun setSearchBarStyle(searchBarStyle: SearchBarStyle) {
        launcherDataStore.update {
            it.copy(searchBar = it.searchBar.copy(searchBarStyle = searchBarStyle))
        }
    }

    val searchBarColor
        get() = launcherDataStore.data.map {
            it.searchBar.searchBarColors
        }.distinctUntilChanged()

    fun setSearchBarColor(color: SearchBarColors) {
        launcherDataStore.update {
            it.copy(searchBar = it.searchBar.copy(searchBarColors = color))
        }
    }

    val searchBarColorDrawer
        get() = launcherDataStore.data.map {
            it.searchBar.searchBarColorsDrawer
        }.distinctUntilChanged()

    fun setSearchBarColorDrawer(color: SearchBarColors?) {
        launcherDataStore.update {
            it.copy(searchBar = it.searchBar.copy(searchBarColorsDrawer = color))
        }
    }

    val bottomSearchBar
        get() = launcherDataStore.data.map {
            it.searchBar.searchBarBottom
        }.distinctUntilChanged()

    fun setBottomSearchBar(bottomSearchBar: Boolean) {
        launcherDataStore.update {
            it.copy(searchBar = it.searchBar.copy(searchBarBottom = bottomSearchBar))
        }
    }

    val reverseSearchResults
        get() = launcherDataStore.data.map {
            it.searchResults.searchResultsReversed
        }.distinctUntilChanged()

    fun setReverseSearchResults(reverseSearchResults: Boolean) {
        launcherDataStore.update {
            it.copy(searchResults = it.searchResults.copy(searchResultsReversed = reverseSearchResults))
        }
    }

    val fixedSearchBar
        get() = launcherDataStore.data.map {
            it.searchBar.searchBarFixed
        }.distinctUntilChanged()

    fun setFixedSearchBar(fixedSearchBar: Boolean) {
        launcherDataStore.update {
            it.copy(searchBar = it.searchBar.copy(searchBarFixed = fixedSearchBar))
        }
    }

    val crashReporterEnabled
        get() = launcherDataStore.data.map {
            it.misc.crashReporterEnabled
        }.distinctUntilChanged()

    fun setCrashReporterEnabled(enabled: Boolean) {
        launcherDataStore.update {
            it.copy(misc = it.misc.copy(crashReporterEnabled = enabled))
        }
    }

    val rememberScrollPosition
        get() = launcherDataStore.data.map {
            it.searchBar.searchRememberScrollPosition
        }.distinctUntilChanged()

    fun setRememberScrollPosition(remember: Boolean) {
        launcherDataStore.update {
            it.copy(searchBar = it.searchBar.copy(searchRememberScrollPosition = remember))
        }
    }

    val openKeyboardOnSearch
        get() = launcherDataStore.data.map {
            it.searchBar.searchBarKeyboard
        }.distinctUntilChanged()


    val orientation
        get() = launcherDataStore.data.map {
            it.ui.uiOrientation
        }.distinctUntilChanged()

    fun setOrientation(orientation: ScreenOrientation) {
        launcherDataStore.update {
            it.copy(ui = it.ui.copy(uiOrientation = orientation))
        }
    }


    val colorsId
        get() = launcherDataStore.data.map {
            it.ui.uiColorsId
        }.distinctUntilChanged()

    fun setColorsId(colorsId: UUID) {
        launcherDataStore.update {
            it.copy(ui = it.ui.copy(uiColorsId = colorsId))
        }
    }

    val shapesId
        get() = launcherDataStore.data.map {
            it.ui.uiShapesId
        }.distinctUntilChanged()

    fun setShapesId(shapesId: UUID) {
        launcherDataStore.update {
            it.copy(ui = it.ui.copy(uiShapesId = shapesId))
        }
    }

    val transparenciesId
        get() = launcherDataStore.data.map {
            it.ui.uiTransparenciesId
        }.distinctUntilChanged()

    fun setTransparenciesId(transparenciesId: UUID) {
        launcherDataStore.update {
            it.copy(ui = it.ui.copy(uiTransparenciesId = transparenciesId))
        }
    }

    val typographyId
        get() = launcherDataStore.data.map {
            it.ui.uiTypographyId
        }.distinctUntilChanged()

    fun setTypographyId(typographyId: UUID) {
        launcherDataStore.update {
            it.copy(ui = it.ui.copy(uiTypographyId = typographyId))
        }
    }

    val fontScale
        get() = launcherDataStore.data.map {
            it.ui.uiFontScale
        }.distinctUntilChanged()

    fun setFontScale(fontScale: Float) {
        launcherDataStore.update {
            it.copy(ui = it.ui.copy(uiFontScale = fontScale.coerceIn(0.8f, 2f)))
        }
    }

    fun setColorScheme(colorScheme: ColorScheme) {
        launcherDataStore.update {
            it.copy(ui = it.ui.copy(uiColorScheme = colorScheme))
        }
    }

    fun setColorSchemeNightStart(hour: Int) {
        launcherDataStore.update {
            it.copy(ui = it.ui.copy(uiColorSchemeNightStart = hour.coerceIn(0, 23)))
        }
    }

    fun setColorSchemeDayStart(hour: Int) {
        launcherDataStore.update {
            it.copy(ui = it.ui.copy(uiColorSchemeDayStart = hour.coerceIn(0, 23)))
        }
    }

    val dock
        get() = launcherDataStore.data.map {
            it.home.homeScreenDock
        }.distinctUntilChanged()

    fun setDock(dock: Boolean) {
        launcherDataStore.update {
            it.copy(home = it.home.copy(homeScreenDock = dock))
        }
    }

    val dockRows
        get() = launcherDataStore.data.map {
            it.home.homeScreenDockRows
        }.distinctUntilChanged()

    fun setDockRows(rows: Int) {
        launcherDataStore.update {
            it.copy(home = it.home.copy(homeScreenDockRows = rows))
        }
    }

    val dockColumns
        get() = launcherDataStore.data.map {
            it.home.homeScreenDockColumns
        }.distinctUntilChanged()

    fun setDockColumns(columns: Int) {
        launcherDataStore.update {
            it.copy(home = it.home.copy(homeScreenDockColumns = columns))
        }
    }

    val dockDefaultPage
        get() = launcherDataStore.data.map {
            it.home.homeScreenDockDefaultPage
        }.distinctUntilChanged()

    fun setDockDefaultPage(page: Int) {
        launcherDataStore.update {
            it.copy(home = it.home.copy(homeScreenDockDefaultPage = page))
        }
    }

    val homeScreenWidgets
        get() = launcherDataStore.data.map {
            it.home.homeScreenWidgets
        }.distinctUntilChanged()

    fun setHomeScreenWidgets(widgets: Boolean) {
        launcherDataStore.update {
            it.copy(home = it.home.copy(homeScreenWidgets = widgets))
        }
    }

    val widgetsTutorialShown
        get() = launcherDataStore.data.map {
            it.home.widgetsTutorialShown
        }.distinctUntilChanged()

    fun setWidgetsTutorialShown(shown: Boolean) {
        launcherDataStore.update {
            it.copy(home = it.home.copy(widgetsTutorialShown = shown))
        }
    }

    val homeScreenPageCount
        get() = launcherDataStore.data.map {
            it.home.homeScreenPageCount
        }.distinctUntilChanged()

    fun setHomeScreenPageCount(count: Int) {
        launcherDataStore.update {
            it.copy(home = it.home.copy(homeScreenPageCount = count.coerceIn(1, 9)))
        }
    }

    val widgetEditButton
        get() = launcherDataStore.data.map {
            it.widgets.widgetsEditButton
        }.distinctUntilChanged()

    fun setWidgetEditButton(editButton: Boolean) {
        launcherDataStore.update {
            it.copy(widgets = it.widgets.copy(widgetsEditButton = editButton))
        }
    }

    val dockPages
        get() = launcherDataStore.data.map { it.home.homeScreenDockPages }

    fun setDockPages(pages: List<List<de.mm20.launcher2.preferences.DockItem>>) {
        launcherDataStore.update { it.copy(home = it.home.copy(homeScreenDockPages = pages)) }
    }

    val dockAutoPopulated
        get() = launcherDataStore.data.map { it.home.homeScreenDockAutoPopulated }.distinctUntilChanged()

    fun setDockAutoPopulated(populated: Boolean) {
        launcherDataStore.update { it.copy(home = it.home.copy(homeScreenDockAutoPopulated = populated)) }
    }

    /** Seeds the dock and marks it auto-populated in one update, so the two fields can't land as
     * two separate fire-and-forget writes that race and clobber one another. Also flips the
     * dock's own visibility toggle on: a first-launch seed is pointless if the dock stays hidden
     * until the user separately discovers and enables it in settings. */
    fun setDockPagesAndMarkAutoPopulated(pages: List<List<de.mm20.launcher2.preferences.DockItem>>) {
        launcherDataStore.update {
            it.copy(
                home = it.home.copy(
                    homeScreenDock = true,
                    homeScreenDockPages = pages,
                    homeScreenDockAutoPopulated = true,
                )
            )
        }
    }
}
