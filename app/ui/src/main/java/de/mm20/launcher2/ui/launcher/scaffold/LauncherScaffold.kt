package de.mm20.launcher2.ui.launcher.scaffold

import android.app.WallpaperManager
import android.view.animation.PathInterpolator
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.Spring
import org.koin.compose.koinInject
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.waterfall
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import de.mm20.launcher2.ui.component.frostedGlass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.SharedLauncherActivity
import de.mm20.launcher2.ui.launcher.scaffold.animation.Offset3D
import de.mm20.launcher2.ui.launcher.scaffold.animation.PushScaffoldAnimationController
import de.mm20.launcher2.ui.launcher.scaffold.animation.RubberbandScaffoldAnimationController
import de.mm20.launcher2.ui.launcher.scaffold.animation.ScaffoldAnimationController
import de.mm20.launcher2.ui.launcher.scaffold.animation.ZoomInScaffoldAnimationController
import de.mm20.launcher2.ui.launcher.scaffold.components.TelosAppPageComponent
import de.mm20.launcher2.ui.launcher.scaffold.components.ScaffoldComponent
import de.mm20.launcher2.ui.launcher.scaffold.components.WidgetsComponent
import de.mm20.launcher2.ui.launcher.scaffold.components.ClockAndWidgetsHomeComponent
import de.mm20.launcher2.ui.launcher.scaffold.components.SearchComponent
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.search.filters.KeyboardFilterBar
import de.mm20.launcher2.ui.launcher.searchbar.LauncherSearchBar
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds





@Composable
internal fun LauncherScaffold(
    modifier: Modifier,
    config: ScaffoldConfiguration,
) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalActivity.current as AppCompatActivity
    val view = LocalView.current

    val wallpaperManager = remember(activity) { WallpaperManager.getInstance(activity) }

    val performanceSettings = koinInject<de.mm20.launcher2.preferences.ui.PerformanceSettings>()
    val storedBounceDampingRatio by performanceSettings.bouncePhysics.collectAsState(Spring.DampingRatioNoBouncy)
    val reduceAnimations by performanceSettings.reduceAnimations.collectAsState(false)
    // "Reduce animations" also switches the bounce off
    val bounceDampingRatio = if (reduceAnimations) Spring.DampingRatioNoBouncy else storedBounceDampingRatio

    val density = LocalDensity.current
    val systemBarInsets = WindowInsets.displayCutout
        .union(WindowInsets.waterfall)
        .let { if (config.showStatusBar) it.union(WindowInsets.statusBars) else it }
        .let { if (config.showNavBar) it.union(WindowInsets.navigationBars) else it }

    val searchVM = viewModel<SearchVM>()
    val searchActions = searchVM.searchActionResults
    val highlightedResult by searchVM.bestMatch
    val filters by searchVM.filters
    val filterBar by searchVM.filterBar.collectAsState(false)
    val filterBarItems by searchVM.filterBarItems.collectAsState(emptyList())
    val launchOnEnter by searchVM.launchOnEnter.collectAsState(false)

    val glassSettings = koinInject<de.mm20.launcher2.preferences.ui.UiSettings>()
    val glassWidgets by remember { glassSettings.widgetsGlassBackground }.collectAsState(true)
    val desktopModeSettings = koinInject<de.mm20.launcher2.preferences.ui.DesktopModeSettings>()
    val keyboardShortcuts by desktopModeSettings.hardwareKeyboardShortcuts.collectAsState(true)

    BoxWithConstraints(
        modifier = modifier,
    ) {
        val width = this.maxWidth
        val height = this.maxHeight
        val widthPx = width.toPixels()
        val heightPx = height.toPixels()

        val touchSlop = LocalViewConfiguration.current.touchSlop
        val minFlingVelocity = 125.dp.toPixels()
        val rubberbandThreshold = 64.dp.toPixels()
        val maxSearchBarOffset = (
                if (config.searchBarPosition == SearchBarPosition.Top) systemBarInsets.getTop(
                    density
                )
                else systemBarInsets.getBottom(density)
                ) + 128.dp.toPixels()

        val hapticFeedback = LocalHapticFeedback.current

        val state =
            rememberSaveable(
                widthPx, heightPx, touchSlop, rubberbandThreshold, minFlingVelocity,
                saver = listSaver(
                    save = {
                        listOf(
                            it.currentGesture,
                            it.isSearchBarHidden,
                            it.isLocked,
                        )
                    },
                    restore = {
                        LauncherScaffoldState(
                            initialConfig = config,
                            size = Size(widthPx, heightPx),
                            touchSlop = touchSlop,
                            rubberbandThreshold = rubberbandThreshold,
                            velocityThreshold = minFlingVelocity,
                            maxSearchBarOffset = maxSearchBarOffset,
                            bounceDampingRatio = bounceDampingRatio,
                            onHapticFeedback = {
                                hapticFeedback.performHapticFeedback(it)
                            },
                            initialGesture = it[0] as Gesture?,
                            initialIsSearchBarHidden = it[1] as Boolean,
                            initialIsLocked = it[2] as Boolean,
                        )
                    }
                )
            ) {
                LauncherScaffoldState(
                    initialConfig = config,
                    size = Size(widthPx, heightPx),
                    touchSlop = touchSlop,
                    rubberbandThreshold = rubberbandThreshold,
                    velocityThreshold = minFlingVelocity,
                    maxSearchBarOffset = maxSearchBarOffset,
                    bounceDampingRatio = bounceDampingRatio,
                    onHapticFeedback = {
                        hapticFeedback.performHapticFeedback(it)
                    }
                )
            }
        // config is mutable on LauncherScaffoldState precisely so a settings change (e.g.
        // "widgets on home screen") can update it in place on this stable, already-created state
        // instead of forcing a full LauncherScaffoldState re-creation - see the property's doc.
        state.config = config
        state.bounceDampingRatio = bounceDampingRatio

        // Feeds the scaffold's own drag offset - the same value that drives every gesture
        // destination (WebAppsPanel, Widgets, Feed, ...) - into the system wallpaper offset API,
        // so a live/video wallpaper's parallax effect tracks whatever horizontal swipe is
        // currently in progress. There is no separate multi-page home screen concept active in
        // this build (see EXTRA_HOME_SCREENS_ENABLED), so this is the one continuous swipe
        // signal that actually exists to drive it.
        LaunchedEffect(state, widthPx) {
            snapshotFlow { state.currentOffset.x }.collect { offsetX ->
                val fraction = (0.5f - (offsetX / widthPx) * 0.5f).coerceIn(0f, 1f)
                view.windowToken?.let { token ->
                    wallpaperManager.setWallpaperOffsets(token, fraction, 0.5f)
                }
            }
        }

        LaunchedEffect(state.isAtTop, state.isAtBottom) {
            if (state.currentProgress > 0f && state.currentProgress < 1f) {
                return@LaunchedEffect
            }
            when (state.currentComponent?.reverseScrolling) {
                true -> if (state.isAtBottom) state.resetSearchBarOffset()
                false -> if (state.isAtTop) state.resetSearchBarOffset()
                else -> {}
            }
        }

        val searchBarDensity = LocalDensity.current
        var measuredSearchBarHeight by remember { mutableStateOf(56.dp) }
        val searchBarHeight by animateDpAsState(
            if (state.isSearchBarHidden) 0.dp else measuredSearchBarHeight
        )

        val isFilterBarVisible =
            (state.currentProgress == 1f && state.currentComponent is SearchComponent ||
                    state.currentProgress == 0f && config.homeComponent is SearchComponent) &&
                    filterBar && WindowInsets.isImeVisible


        val imeCurrent = WindowInsets.ime.getBottom(LocalDensity.current).toFloat()
        val imeSource = WindowInsets.imeAnimationSource.getBottom(LocalDensity.current).toFloat()
        val imeTarget = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current).toFloat()
        val imeProgress = (imeCurrent /
                (imeSource - imeTarget).absoluteValue.coerceAtLeast(imeCurrent))
            .coerceIn(0f, 1f)

        var measuredFilterBarHeight by remember { mutableStateOf(50.dp) }
        val filterBarHeight by animateDpAsState(if (isFilterBarVisible) imeProgress * measuredFilterBarHeight else 0.dp)

        LaunchedEffect(state) {
            config.homeComponent.onPreActivate(state)
            config.homeComponent.onActivate(state)

            val activity = (activity as? SharedLauncherActivity) ?: return@LaunchedEffect

            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    if (activity.pauseTime > 0L && System.currentTimeMillis() - activity.pauseTime < 50L && activity.isNewIntent) {
                        if (!state.isLocked) {
                            if (state.currentProgress > 0f) {
                                state.onPredictiveBackEnd()
                            } else {
                                state.onHomeButtonPress()
                            }
                        } else {
                            activity.onBackPressedDispatcher.onBackPressed()
                        }
                    } else if (activity.pauseTime > 0L && System.currentTimeMillis() - activity.pauseTime > 5000L) {
                        if (!state.isLocked) {
                            if (state.currentComponent?.survivesPause != true) {
                                state.reset()
                            }
                            searchVM.reset()
                        }
                    }
                    awaitCancellation()
                } finally {
                    activity.pauseTime = System.currentTimeMillis()
                    activity.pauseOnHome = !state.isSettledOnSecondaryPage
                }
            }
        }


        LaunchedEffect(
            config, activity, view,
            state.darkStatusBarIcons,
            state.darkNavBarIcons,
        ) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val insetsController = WindowInsetsControllerCompat(activity.window, view)
                insetsController.isAppearanceLightStatusBars = state.darkStatusBarIcons
                insetsController.isAppearanceLightNavigationBars = state.darkNavBarIcons
                if (config.showStatusBar) {
                    insetsController.show(WindowInsetsCompat.Type.statusBars())
                } else {
                    insetsController.hide(WindowInsetsCompat.Type.statusBars())
                }
                if (config.showNavBar) {
                    insetsController.show(WindowInsetsCompat.Type.navigationBars())
                } else {
                    insetsController.hide(WindowInsetsCompat.Type.navigationBars())
                }
            }
        }

        if (!config.finishOnBack || state.currentProgress > 0) {
            PredictiveBackHandler {
                try {
                    state.lock()
                    it.collect {
                        state.onPredictiveBack(it.progress)
                    }
                    scope.launch { state.onPredictiveBackEnd() }
                } catch (_: CancellationException) {
                    scope.launch { state.onPredictiveBackCancel() }
                } finally {
                    state.unlock()
                }
            }
        }

        val nestedScrollConnection = remember(state) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero

                    if (state.currentProgress != 0f && state.currentProgress != 1f) {
                        state.onDrag(available)
                        return available
                    }

                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (source == NestedScrollSource.UserInput) {
                        state.onDrag(available)
                        return available
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    return super.onPreFling(available)
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity
                ): Velocity {

                    if (state.currentProgress > 0f && !state.isSettledOnSecondaryPage) {
                        state.onDragStopped(available)
                        return available
                    } else if (state.currentProgress < 1f && state.isSettledOnSecondaryPage) {
                        state.onDragStopped(available)
                        return available
                    } else {
                        state.onDragStopped(available, disallowPageChange = true)
                        return available
                    }
                }
            }
        }

        var draggableOrientation by remember {
            mutableStateOf(state.currentGesture?.orientation)
        }

        LaunchedEffect(state.isSettledOnSecondaryPage) {
            if (!state.isSettledOnSecondaryPage) {
                draggableOrientation = null
            } else {
                draggableOrientation = state.currentGesture?.orientation
            }
        }


        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!keyboardShortcuts) Modifier
                    else Modifier.onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || !event.isMetaPressed) {
                            return@onPreviewKeyEvent false
                        }
                        when (event.key) {
                            Key.Spacebar, Key.A -> {
                                scope.launch { state.onSearchBarTap() }
                                true
                            }
                            Key.Enter -> {
                                de.mm20.launcher2.ui.common.openExternalTermux(activity)
                                true
                            }
                            Key.One -> {
                                scope.launch { state.navigateBack() }
                                true
                            }
                            else -> false
                        }
                    }
                )
                .nestedScroll(nestedScrollConnection)
                .multiFingerGestures(
                    enabled = config.pinchIn != null || config.pinchOut != null ||
                            config.twoFingerSwipeUp != null || config.twoFingerSwipeDown != null,
                    isActive = { !state.isSettledOnSecondaryPage },
                    onPinchIn = { scope.launch { state.onPinchIn() } },
                    onPinchOut = { scope.launch { state.onPinchOut() } },
                    onTwoFingerSwipeUp = { scope.launch { state.onTwoFingerSwipeUp() } },
                    onTwoFingerSwipeDown = { scope.launch { state.onTwoFingerSwipeDown() } },
                )
                .draggable2D(
                    state = rememberDraggable2DState {
                        state.onDrag(it)
                    },
                    enabled = draggableOrientation == null,
                    onDragStarted = {
                        scope.launch {
                            state.onDragStarted()
                        }
                    },
                    onDragStopped = { velocity ->
                        scope.launch {
                            state.onDragStopped(velocity)
                        }
                    }
                )
                .draggable(
                    state = rememberDraggableState {
                        state.onDrag(Offset(0f, it))
                    },
                    orientation = Orientation.Vertical,
                    enabled = draggableOrientation == Orientation.Vertical,
                    onDragStarted = {
                        scope.launch {
                            state.onDragStarted()
                        }
                    },
                    onDragStopped = { velocity ->
                        scope.launch {
                            state.onDragStopped(Velocity(0f, velocity))
                        }
                    },
                )
                .draggable(
                    state = rememberDraggableState {
                        state.onDrag(Offset(it, 0f))
                    },
                    orientation = Orientation.Horizontal,
                    enabled = draggableOrientation == Orientation.Horizontal,
                    onDragStarted = {
                        scope.launch {
                            state.onDragStarted()
                        }
                    },
                    onDragStopped = { velocity ->
                        scope.launch {
                            state.onDragStopped(Velocity(velocity, 0f))
                        }
                    }
                )
        ) {
            val searchBarInsets = WindowInsets(
                top = if (config.searchBarPosition == SearchBarPosition.Top) searchBarHeight + 8.dp else 8.dp,
                bottom = if (config.searchBarPosition == SearchBarPosition.Bottom) searchBarHeight + 8.dp else 8.dp
            )

            val filterBarInsets = WindowInsets(
                bottom = filterBarHeight
            )

            val systemGestureBottomInset =
                WindowInsets.systemGestures.getBottom(LocalDensity.current)

            CompositionLocalProvider(
                LocalScaffoldPage provides ScaffoldPage.Home,
                de.mm20.launcher2.ui.locals.LocalSearchBarHeight provides searchBarHeight,
            ) {
                config.homeComponent.Component(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(
                            wallpaperManager,
                            config.doubleTap,
                            config.longPress,
                            systemGestureBottomInset,
                        ) {
                            detectTapGestures(
                                onDoubleTap = config.doubleTap?.let {
                                    { scope.launch { state.onDoubleTap() } }
                                },
                                onLongPress = config.longPress?.let {
                                    { offset ->
                                        if (offset.y < (size.height - systemGestureBottomInset).toFloat()) {
                                            scope.launch { state.onLongPress() }
                                        }
                                    }
                                },
                                onTap = {
                                    wallpaperManager.sendWallpaperCommand(
                                        view.windowToken,
                                        WallpaperManager.COMMAND_TAP,
                                        it.x.toInt(),
                                        it.y.toInt(),
                                        0,
                                        null
                                    )
                                }
                            )
                        }
                        .homePageAnimation(
                            state,
                            if (config.homeComponent.drawBackground) {
                                config.backgroundColor.copy(alpha = if (glassWidgets && (config.homeComponent is WidgetsComponent || config.homeComponent is ClockAndWidgetsHomeComponent)) 0f else MaterialTheme.transparency.background)
                            } else {
                                Color.Transparent
                            }
                        ),
                    insets = systemBarInsets
                        .let { if (config.homeComponent.hasIme) it.union(WindowInsets.ime) else it }
                        .let {
                            if (config.searchBarStyle == SearchBarStyle.Hidden) it else it.add(
                                searchBarInsets
                            )
                        }
                        .asPaddingValues(),
                    state
                )
            }

            SecondaryPage(
                state = state,
                config = config,
                modifier = Modifier
                    .fillMaxSize(),
                insets = systemBarInsets
                    .let { if (state.currentComponent?.hasIme == true) it.union(WindowInsets.ime) else it }
                    .add(searchBarInsets).add(filterBarInsets)
                    .asPaddingValues(),
            )

            val isSearchOpen = state.currentComponent is SearchComponent && state.isSettledOnSecondaryPage ||
                    config.homeComponent is SearchComponent && !state.isSettledOnSecondaryPage

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .searchBarAnimation(
                        state,
                        config,
                        systemBarInsets
                            .union(WindowInsets.ime)
                            .add(filterBarInsets)
                            .asPaddingValues()
                    )
            ) {
                LauncherSearchBar(
                    modifier = Modifier
                        .onSizeChanged { measuredSearchBarHeight = with(searchBarDensity) { it.height.toDp() } }
                        .widthIn(max = 916.dp)
                        .align(
                            if (config.searchBarPosition == SearchBarPosition.Top) Alignment.TopCenter
                            else Alignment.BottomCenter
                        ),
                    searchBarOffset = { state.currentSearchBarOffset.roundToInt() },
                    style = config.searchBarStyle,
                    focused = state.isSearchBarFocused,
                    actions = searchActions,
                    level = { state.searchBarLevel },
                    bottomSearchBar = config.searchBarPosition == SearchBarPosition.Bottom,
                    onFocusChange = {
                        if (it) {
                            scope.launch { state.onSearchBarTap() }
                        }
                        state.isSearchBarFocused = it
                    },
                    onKeyboardActionGo = if (launchOnEnter) {
                        { searchVM.launchBestMatchOrAction(activity) }
                    } else null,
                    highlightedAction = highlightedResult as? SearchAction,
                    darkColors = if (isSearchOpen) config.darkSearchBarDrawer ?: config.darkSearchBar else config.darkSearchBar,
                    isSearchOpen = isSearchOpen,
                )
            }
            if (isFilterBarVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = (1f - imeProgress) * measuredFilterBarHeight)
                        .alpha(imeProgress),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    KeyboardFilterBar(
                        filters = filters,
                        onFiltersChange = { searchVM.setFilters(it) },
                        items = filterBarItems,
                        onContentHeightChanged = { measuredFilterBarHeight = with(searchBarDensity) { it.toDp() } },
                    )
                }
            }
        }


        AnimatedVisibility(
            state.statusBarScrim,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlass(
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MaterialTheme.transparency.background)
                    )
                    .statusBarsPadding()
            )
        }
        de.mm20.launcher2.ui.help.HelpIntroHost()

        AnimatedVisibility(
            state.navBarScrim,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlass(
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MaterialTheme.transparency.background)
                    )
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun SecondaryPage(
    state: LauncherScaffoldState,
    config: ScaffoldConfiguration,
    modifier: Modifier = Modifier,
    insets: PaddingValues,
) {

    val components = remember(config) {
        setOfNotNull(
            config.swipeUp?.component,
            config.swipeDown?.component,
            config.swipeLeft?.component,
            config.swipeRight?.component,
            config.doubleTap?.component,
            config.longPress?.component,
            config.homeButton?.component,
            config.pinchIn?.component,
            config.pinchOut?.component,
            config.twoFingerSwipeUp?.component,
            config.twoFingerSwipeDown?.component,
            config.searchComponent,
        )
    }

    val composables = remember(config) {
        components.associateWith {
            movableContentOf<Modifier, PaddingValues, LauncherScaffoldState> { modifier, insets, state ->
                it.Component(
                    modifier = modifier,
                    insets = insets,
                    state = state
                )
            }
        }
    }

    val component = state.currentComponent
    val drawerSettings: de.mm20.launcher2.preferences.ui.UiSettings = org.koin.compose.koinInject()
    val customDrawer by drawerSettings.drawerBackgroundEnabled.collectAsState(true)
    val glassWidgets by drawerSettings.widgetsGlassBackground.collectAsState(true)

    if (component != null) {

        val ownsGlass = component is SearchComponent && customDrawer || component is TelosAppPageComponent ||
            glassWidgets && (component is WidgetsComponent || component is ClockAndWidgetsHomeComponent)
        val pageColor = if (ownsGlass) Color.Transparent else config.backgroundColor.copy(alpha = MaterialTheme.transparency.background)
        val cachedPageBlur = !ownsGlass && config.wallpaperBlurRadius > 0.dp
        val mod = modifier
            .fillMaxSize()
            .secondaryPageAnimation(state, if (cachedPageBlur) Color.Transparent else pageColor)
            .then(if (cachedPageBlur) Modifier.frostedGlass(pageColor, wholeWallpaper = true) else Modifier)
        val composable = composables[component]

        CompositionLocalProvider(
            LocalScaffoldPage provides ScaffoldPage.Secondary
        ) {
            composable?.invoke(mod, insets, state)
        }
    }

    // Keep other components alive, but out of the viewport
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(x = state.size.width.toInt(), y = 0) }
    ) {
        for ((k, v) in composables) {
            if (k == component) continue
            v.invoke(modifier, insets, state)
        }
    }
}

private fun Modifier.homePageAnimation(
    state: LauncherScaffoldState,
    backgroundColor: Color,
): Modifier {
    val dir = state.currentGesture ?: return this.background(backgroundColor)
    val component = state.currentComponent ?: return this.background(backgroundColor)

    if (state.currentAnimation == ScaffoldAnimation.Rubberband) {
        return this then component.homePageModifier(
            state,
            Modifier
                .background(backgroundColor)
                .graphicsLayer {
                    translationX =
                        if (dir.orientation == Orientation.Horizontal) state.currentOffset.x else 0f
                    translationY =
                        if (dir.orientation == Orientation.Vertical) state.currentOffset.y else 0f
                    if (!state.isSettledOnSecondaryPage) {
                        alpha = (1f - state.currentProgress).coerceAtMost(1f)
                        scaleX = 1f - (state.currentProgress * 0.03f)
                        scaleY = 1f - (state.currentProgress * 0.03f)
                    } else {
                        alpha = ((1f - state.currentProgress) * 2f - 1f).coerceAtLeast(0f)
                    }
                }
        )
    }

    if (state.currentAnimation == ScaffoldAnimation.ZoomIn) {
        return this then Modifier
            .graphicsLayer {
                scaleX = 1f - (state.currentProgress * 0.03f)
                scaleY = 1f - (state.currentProgress * 0.03f)
                alpha = (1f - state.currentProgress).coerceIn(0f, 1f)
            }
            .background(backgroundColor)
    }

    return this then component.homePageModifier(
        state,
        Modifier
            .background(backgroundColor)
            .absoluteOffset {
                IntOffset(
                    x = if (dir.orientation == Orientation.Horizontal) state.currentOffset.x.toInt() else 0,
                    y = if (dir.orientation == Orientation.Vertical) state.currentOffset.y.toInt() else 0
                )
            })
}

private fun Modifier.secondaryPageAnimation(
    state: LauncherScaffoldState,
    backgroundColor: Color,
): Modifier {
    val dir = state.currentGesture ?: return this
    val component = state.currentComponent ?: return this

    val background =
        if (component.drawBackground) backgroundColor.copy(alpha = backgroundColor.alpha * state.currentProgress) else Color.Transparent

    if (state.currentAnimation == ScaffoldAnimation.Rubberband) {
        return this then Modifier
            .background(background)
            .graphicsLayer {
                translationX =
                    if (dir.orientation == Orientation.Horizontal) state.currentOffset.x else 0f
                translationY =
                    if (dir.orientation == Orientation.Vertical) state.currentOffset.y else 0f
                if (state.isSettledOnSecondaryPage) {
                    alpha = (state.currentProgress).coerceAtMost(1f)
                    scaleX = 1f - ((1f - state.currentProgress) * 0.03f)
                    scaleY = 1f - ((1f - state.currentProgress) * 0.03f)
                } else {
                    alpha = (state.currentProgress * 2f - 1f).coerceAtLeast(0f)
                }
            }

    }
    if (state.currentAnimation == ScaffoldAnimation.ZoomIn) {
        return this then Modifier
            .background(background)
            .graphicsLayer {
                scaleX = 1f - ((1f - state.currentProgress) * 0.03f)
                scaleY = 1f - ((1f - state.currentProgress) * 0.03f)
                alpha = (state.currentProgress).coerceAtMost(1f)
            }
    }

    return this then Modifier
        .absoluteOffset {
            when (state.currentGesture) {
                Gesture.SwipeUp -> IntOffset(0, state.size.height.toInt())
                Gesture.SwipeDown -> IntOffset(0, -state.size.height.toInt())
                Gesture.SwipeLeft -> IntOffset(state.size.width.toInt(), 0)
                Gesture.SwipeRight -> IntOffset(-state.size.width.toInt(), 0)
                else -> IntOffset(state.size.width.toInt(), 0)
            }
        }
        .absoluteOffset {
            IntOffset(
                x = if (state.currentGesture?.orientation == Orientation.Horizontal) state.currentOffset.x.toInt() else 0,
                y = if (state.currentGesture?.orientation == Orientation.Vertical) state.currentOffset.y.toInt() else 0
            )
        }
        .composed {
            val shape = MaterialTheme.shapes.extraLarge.let {
                if (state.currentProgress < 0.95f) {
                    it
                } else {
                    val density = LocalDensity.current
                    val p = 1f - ((state.currentProgress - 0.95f) * 20f).coerceIn(0f, 1f)
                    if (it is CutCornerShape) {
                        CutCornerShape(
                            topStart = it.topStart.toPx(state.size, density) * p,
                            topEnd = it.topEnd.toPx(state.size, density) * p,
                            bottomEnd = it.bottomEnd.toPx(state.size, density) * p,
                            bottomStart = it.bottomStart.toPx(state.size, density) * p,
                        )
                    } else {
                        RoundedCornerShape(
                            topStart = it.topStart.toPx(state.size, density) * p,
                            topEnd = it.topEnd.toPx(state.size, density) * p,
                            bottomEnd = it.bottomEnd.toPx(state.size, density) * p,
                            bottomStart = it.bottomStart.toPx(state.size, density) * p,
                        )
                    }
                }
            }

            Modifier
                .background(background, shape)
                .clip(shape)
        }
}

private fun Modifier.searchBarAnimation(
    state: LauncherScaffoldState,
    config: ScaffoldConfiguration,
    insets: PaddingValues,
): Modifier {
    val offsetFactor = if (config.searchBarPosition == SearchBarPosition.Top) -1f else 1f

    val component = state.currentComponent
    val anim = state.currentAnimation

    val progress = if (anim == ScaffoldAnimation.Rubberband) {
        (state.currentProgress * 2f).coerceAtMost(1f)
    } else {
        state.currentProgress
    }

    val systemBarInset =
        if (config.searchBarPosition == SearchBarPosition.Top) insets.calculateTopPadding() else insets.calculateBottomPadding()

    val offset = if (config.searchBarStyle == SearchBarStyle.Hidden) {
        offsetFactor * (1 - progress).pow(2) * (128.dp + systemBarInset)
    } else {
        0.dp
    }

    val modifier =
        if (component?.showSearchBar == false && config.searchBarStyle == SearchBarStyle.Hidden) {
            Modifier.alpha(0f)
        } else {
            Modifier.offset(y = offset)
        }

    return this then (component?.searchBarModifier(state, modifier)
        ?: modifier) then Modifier.padding(insets)
}
