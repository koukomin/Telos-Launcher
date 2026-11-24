package de.mm20.launcher2.ui.launcher.scaffold

import android.view.animation.PathInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Velocity
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.launcher.scaffold.animation.Offset3D
import de.mm20.launcher2.ui.launcher.scaffold.animation.PushScaffoldAnimationController
import de.mm20.launcher2.ui.launcher.scaffold.animation.RubberbandScaffoldAnimationController
import de.mm20.launcher2.ui.launcher.scaffold.animation.ScaffoldAnimationController
import de.mm20.launcher2.ui.launcher.scaffold.animation.ZoomInScaffoldAnimationController
import de.mm20.launcher2.ui.launcher.scaffold.components.ScaffoldComponent
import de.mm20.launcher2.ui.launcher.scaffold.components.SearchComponent
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

internal class LauncherScaffoldState(
    initialConfig: ScaffoldConfiguration,
    val size: Size,
    private val touchSlop: Float,
    /**
     * The threshold (in px) where a rubberband gesture is considered to be over the threshold
     * (releasing it would snap to the next page)
     */
    private val rubberbandThreshold: Float,
    /**
     * The minimum velocity (in px/s) where a fling will snap to the next page, regardless of the
     * current offset.
     */
    private val velocityThreshold: Float,
    private val maxSearchBarOffset: Float,
    bounceDampingRatio: Float = Spring.DampingRatioNoBouncy,
    private val onHapticFeedback: (HapticFeedbackType) -> Unit,
    initialGesture: Gesture? = null,
    initialIsLocked: Boolean = false,
    initialIsSearchBarHidden: Boolean = false,
) {
    /**
     * Mutable rather than constructor-fixed so the caller can push config updates (e.g. a
     * settings toggle like "widgets on home screen" swapping [ScaffoldConfiguration.homeComponent])
     * into an existing, stable state instance instead of forcing this whole class - and every
     * gesture/animation/search-bar-offset field it holds - to be torn down and recreated. That
     * full recreation was the cause of the home screen/wallpaper glitch on such toggles: the
     * entire scaffold (including the home component) briefly remounted from scratch.
     */
    var config: ScaffoldConfiguration by mutableStateOf(initialConfig)

    /**
     * Updated in place (not part of the state's remember keys): the preference is loaded
     * asynchronously, and re-keying on it would recreate this state right after the first frame
     * and drop the gesture that was restored after a configuration change / process death.
     */
    var bounceDampingRatio: Float = bounceDampingRatio

    private val rubberbandAnimationController = RubberbandScaffoldAnimationController(
        rubberbandThreshold = rubberbandThreshold,
        velocityThreshold = velocityThreshold,
    )
    private val pushAnimationController = PushScaffoldAnimationController(
        velocityThreshold = velocityThreshold,
    )

    private fun getAnimationController(animation: ScaffoldAnimation?): ScaffoldAnimationController? {
        return when (animation) {
            ScaffoldAnimation.Rubberband -> rubberbandAnimationController
            ScaffoldAnimation.Push -> pushAnimationController
            ScaffoldAnimation.ZoomIn -> ZoomInScaffoldAnimationController
            null -> null
        }
    }

    private fun getAnimationController(gesture: Gesture?): ScaffoldAnimationController? {
        val animation = gesture?.let { config[it]?.animation }
        return getAnimationController(animation)
    }

    private val initialOffset3D: Offset3D = initialGesture?.let { gesture ->
        getAnimationController(config[gesture]?.animation)?.initialOffset(gesture, size)
    } ?: Offset3D.Zero

    private val currentPage: ScaffoldPage
        get() = if (isSettledOnSecondaryPage) ScaffoldPage.Secondary else ScaffoldPage.Home

    private var currentOffset3D by mutableStateOf(initialOffset3D)

    private fun isOverThreshold(animation: ScaffoldAnimation, offset: Offset3D): Boolean {
        return when (animation) {
            ScaffoldAnimation.Rubberband -> {
                offset.x.absoluteValue > rubberbandThreshold || offset.y.absoluteValue > rubberbandThreshold
            }

            ScaffoldAnimation.Push -> {
                offset.x.absoluteValue > size.width * 0.5f || offset.y.absoluteValue > size.height * 0.5f
            }

            ScaffoldAnimation.ZoomIn -> false
        }
    }

    val currentOffset: Offset
        get() = currentOffset3D.toOffset2D()
    val currentZOffset: Float
        get() = currentOffset3D.z
    var currentGesture by mutableStateOf<Gesture?>(initialGesture)
        private set

    /**
     * True if the search is animating to open after tapping the search bar or performing a tap gesture.
     */
    private var isOpeningSearch by mutableStateOf(false)
    val currentSearchBarOffset by derivedStateOf {
        val base = when {
            currentComponent?.showSearchBar == false -> homePageSearchBarOffset
            else -> homePageSearchBarOffset * (1 - currentProgress) + secondaryPageSearchBarOffset * currentProgress
        }
        base + if (currentAnimation == ScaffoldAnimation.Rubberband && !isOpeningSearch) currentOffset.y else 0f
    }
    private var homePageSearchBarOffset by mutableFloatStateOf(0f)
    private var secondaryPageSearchBarOffset by mutableFloatStateOf(0f)

    var isSearchBarFocused by mutableStateOf(config.homeComponent is SearchComponent)

    val statusBarScrim by derivedStateOf {
        !isAtTop
    }

    val navBarScrim by derivedStateOf {
        !isAtBottom
    }

    val darkStatusBarIcons by derivedStateOf {
        val isLightBackground = config.backgroundColor.luminance() > 0.5f
        when {
            statusBarScrim -> isLightBackground
            currentProgress < 0.5f && !config.homeComponent.drawBackground -> {
                config.darkStatusBarIcons
            }

            currentProgress >= 0.5f && currentComponent?.drawBackground == false -> {
                config.darkStatusBarIcons
            }

            else -> {
                isLightBackground
            }
        }
    }
    val darkNavBarIcons by derivedStateOf {
        val isLightBackground = config.backgroundColor.luminance() > 0.5f
        when {
            navBarScrim -> isLightBackground
            currentProgress < 0.5f && !config.homeComponent.drawBackground -> {
                config.darkNavBarIcons
            }

            currentProgress >= 0.5f && currentComponent?.drawBackground == false -> {
                config.darkNavBarIcons
            }

            else -> {
                isLightBackground
            }
        }
    }

    val isAtTop by derivedStateOf {
        val component = if (!isSettledOnSecondaryPage) null else currentComponent
        (component?.isAtTop?.value ?: config.homeComponent.isAtTop.value) != false
    }

    val isAtBottom by derivedStateOf {
        val component = if (!isSettledOnSecondaryPage) null else currentComponent
        (component?.isAtBottom?.value ?: config.homeComponent.isAtBottom.value) != false
    }

    val searchBarLevel by derivedStateOf {
        val component = currentComponent

        val homeLevel = if (config.searchBarPosition == SearchBarPosition.Top) {
            when (config.homeComponent.isAtTop.value) {
                true if config.homeComponent.drawBackground -> SearchBarLevel.Active
                true -> SearchBarLevel.Resting
                false -> SearchBarLevel.Raised
                null -> null
            }
        } else {
            when (config.homeComponent.isAtBottom.value) {
                true if config.homeComponent.drawBackground -> SearchBarLevel.Active
                true -> SearchBarLevel.Resting
                false -> SearchBarLevel.Raised
                null -> null
            }
        }

        val secondaryLevel = if (config.searchBarPosition == SearchBarPosition.Top) {
            when (component?.isAtTop?.value) {
                true if (component.drawBackground) -> SearchBarLevel.Active
                true -> SearchBarLevel.Resting
                false -> SearchBarLevel.Raised
                null -> null
            }
        } else {
            when (component?.isAtBottom?.value) {
                true if (component.drawBackground) -> SearchBarLevel.Active
                true -> SearchBarLevel.Resting
                false -> SearchBarLevel.Raised
                null -> null
            }
        }

        when (currentProgress) {
            0f -> homeLevel ?: SearchBarLevel.Active
            1f -> secondaryLevel ?: homeLevel ?: SearchBarLevel.Active
            else if homeLevel != null && secondaryLevel != null -> maxOf(homeLevel, secondaryLevel)
            else if homeLevel != null -> homeLevel
            else -> SearchBarLevel.Active
        }
    }

    /**
     * True if any page is open, false if on home page.
     */
    var isSettledOnSecondaryPage by mutableStateOf(initialGesture != null)
        private set

    /**
     * 0..1 current progress
     * 0: home page
     * 1: any other page
     */
    val currentProgress by derivedStateOf {
        val dir = currentGesture ?: return@derivedStateOf 0f
        val controller = getAnimationController(dir) ?: return@derivedStateOf 0f
        controller.calculateProgress(
            direction = dir,
            offset = currentOffset3D,
            currentPage = currentPage,
            size = size,
        )
    }

    val currentAnimation by derivedStateOf {
        val dir = currentGesture ?: return@derivedStateOf null
        config[dir]?.animation
    }

    val currentComponent by derivedStateOf {
        val dir = currentGesture ?: return@derivedStateOf null
        config[dir]?.component
    }

    private val offsetAnimatable =
        Animatable(Offset3D.Zero, Offset3D.VectorConverter)

    private suspend fun animateOffset3DTo(
        target: Offset3D,
        initialVelocity: Offset3D = Offset3D.Zero,
        animationSpec: AnimationSpec<Offset3D> = spring(dampingRatio = bounceDampingRatio),
    ) {
        offsetAnimatable.snapTo(currentOffset3D)
        offsetAnimatable.animateTo(
            target,
            animationSpec = animationSpec,
            initialVelocity = initialVelocity,
        ) {
            currentOffset3D = this.value
        }
    }

    private val searchBarAnimatable =
        Animatable(0f, Float.VectorConverter)

    var isDragged by mutableStateOf(false)
        private set

    suspend fun onDragStarted() {
        if (isLocked || currentZOffset > 0f) return
        isDragged = true
        offsetAnimatable.stop()
    }


    fun onDrag(offset: Offset) {
        if (isLocked || currentZOffset > 0f) return
        if (currentGesture == null || (!isSettledOnSecondaryPage && currentOffset.x.absoluteValue <= touchSlop && currentOffset.y.absoluteValue <= touchSlop)) {
            currentGesture = getSwipeDirection(config, offset)
        }

        val direction = currentGesture ?: return

        val gesture = config[direction] ?: return
        val controller = getAnimationController(gesture.animation) ?: return
        val wasOverThreshold = isOverThreshold(gesture.animation, currentOffset3D)

        val targetOffset = controller.calculateOffset(
            direction = direction,
            currentOffset = currentOffset3D,
            delta = Offset3D.from2D(offset),
            currentPage = currentPage,
            size = size,
            isAtTop = isAtTop,
            isAtBottom = isAtBottom,
        )
        currentOffset3D = targetOffset

        val isOverThreshold = isOverThreshold(gesture.animation, currentOffset3D)
        if (wasOverThreshold != isOverThreshold) {
            onHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }

    private fun getSwipeDirection(config: ScaffoldConfiguration, offset: Offset): Gesture? {
        when {
            (offset.x >= 0 && offset.y >= 0) -> {
                return if (offset.x > offset.y && config.swipeRight != null) {
                    Gesture.SwipeRight
                } else if (offset.x < offset.y && config.swipeDown != null && isAtTop) {
                    Gesture.SwipeDown
                } else {
                    null
                }
            }

            (offset.x < 0 && offset.y < 0) -> {
                return if (offset.x < offset.y && config.swipeLeft != null) {
                    Gesture.SwipeLeft
                } else if (offset.x > offset.y && config.swipeUp != null && isAtBottom) {
                    Gesture.SwipeUp
                } else {
                    null
                }
            }

            (offset.x >= 0 && offset.y < 0) -> {
                return if (offset.x > -offset.y && config.swipeRight != null) {
                    Gesture.SwipeRight
                } else if (offset.x < -offset.y && config.swipeUp != null && isAtBottom) {
                    Gesture.SwipeUp
                } else {
                    null
                }
            }

            (offset.x < 0 && offset.y >= 0) -> {
                return if (offset.x < -offset.y && config.swipeLeft != null) {
                    Gesture.SwipeLeft
                } else if (offset.x > -offset.y && config.swipeDown != null && isAtTop) {
                    Gesture.SwipeDown
                } else {
                    null
                }
            }
        }
        return null
    }

    /**
     * Called when the drag gesture is stopped.
     * This will perform the fling animation and snap to the next page if needed.
     * @param velocity The velocity at the end of the drag gesture.
     * @param disallowPageChange If true, the page will not change even if the velocity is over the threshold.
     */
    suspend fun onDragStopped(velocity: Velocity, disallowPageChange: Boolean = false) {
        isDragged = false
        if (isLocked || currentZOffset > 0f) return
        val velocity = when {
            !isAtTop && !isAtBottom -> velocity.copy(y = 0f)
            !isAtTop -> velocity.copy(y = velocity.y.coerceAtMost(0f))
            !isAtBottom -> velocity.copy(y = velocity.y.coerceAtLeast(0f))
            else -> velocity
        }
        if (currentGesture == null) {
            currentOffset3D = Offset3D.Zero
        }
        val direction = currentGesture ?: return
        val wasPageOpen = isSettledOnSecondaryPage

        val gesture = config[direction] ?: return

        val controller = getAnimationController(gesture.animation) ?: return
        val currentOffset3DValue = currentOffset3D
        val wasOverThreshold = isOverThreshold(gesture.animation, currentOffset3DValue)

        val targetPage = if (disallowPageChange) {
            currentPage
        } else {
            controller.calculateTargetPage(
                direction = direction,
                offset = currentOffset3DValue,
                velocity = velocity,
                currentPage = currentPage,
                size = size,
            )
        }
        isSettledOnSecondaryPage = targetPage == ScaffoldPage.Secondary

        val targetOffset = controller.calculateTargetOffset(
            direction = direction,
            targetPage = targetPage,
            size = size,
        )

        if (gesture.animation == ScaffoldAnimation.Push) {
            val isOverThreshold = isOverThreshold(gesture.animation, targetOffset)
            if (wasOverThreshold != isOverThreshold) {
                onHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }
        }

        val didPageChange = isSettledOnSecondaryPage != wasPageOpen
        if (didPageChange && gesture.animation == ScaffoldAnimation.Rubberband) {
            onHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }


        val settleTarget = currentOffset3D.copy(x = targetOffset.x, y = targetOffset.y)
        if (didPageChange) {
            transitionToPage(
                targetPage = if (isSettledOnSecondaryPage) ScaffoldPage.Secondary else ScaffoldPage.Home,
                component = gesture.component,
            ) {
                animateOffset3DTo(
                    target = settleTarget,
                    initialVelocity = Offset3D(velocity.x, velocity.y, 0f),
                )
            }
        } else {
            animateOffset3DTo(
                target = settleTarget,
                initialVelocity = Offset3D(velocity.x, velocity.y, 0f),
            )
        }

        if (!isSettledOnSecondaryPage) currentGesture = null
    }

    suspend fun onDoubleTap() {
        performTapGesture(Gesture.DoubleTap)
    }

    suspend fun onLongPress() {
        performTapGesture(Gesture.LongPress)
    }

    suspend fun onPinchIn() {
        performTapGesture(Gesture.PinchIn)
    }

    suspend fun onPinchOut() {
        performTapGesture(Gesture.PinchOut)
    }

    suspend fun onTwoFingerSwipeUp() {
        performTapGesture(Gesture.TwoFingerSwipeUp)
    }

    suspend fun onTwoFingerSwipeDown() {
        performTapGesture(Gesture.TwoFingerSwipeDown)
    }

    suspend fun onHomeButtonPress() {
        performTapGesture(Gesture.HomeButton)

    }

    suspend fun onSearchBarTap() {
        if (currentComponent is SearchComponent) return
        openSearch()
    }

    /**
     * Called by components to notify the scaffold that a child component has been scrolled vertically.
     * Used to update the search bar position and visibility.
     * Note that we aren't interested in horizontal scroll events.
     *
     * @param delta The y delta of the scroll event, in pixels.
     */
    fun onComponentScroll(delta: Float) {
        if (isSearchBarHidden || config.fixedSearchBar) return
        if (isSettledOnSecondaryPage) {
            secondaryPageSearchBarOffset = if (config.searchBarPosition == SearchBarPosition.Top) {
                (secondaryPageSearchBarOffset - delta).coerceIn(-maxSearchBarOffset, 0f)
            } else {
                (secondaryPageSearchBarOffset + delta).coerceIn(0f, maxSearchBarOffset)
            }
        } else {
            homePageSearchBarOffset = if (config.searchBarPosition == SearchBarPosition.Top) {
                (homePageSearchBarOffset - delta).coerceIn(-maxSearchBarOffset, 0f)
            } else {
                (homePageSearchBarOffset + delta).coerceIn(0f, maxSearchBarOffset)
            }
        }
    }

    fun setProgress(progress: Float) {
        val gesture = currentGesture ?: return
        val anim = currentAnimation ?: return

        when (gesture) {
            Gesture.TapSearchBar, Gesture.DoubleTap, Gesture.LongPress -> {
                currentOffset3D = Offset3D(z = progress)
            }

            else -> {
                val x = when (gesture) {
                    Gesture.SwipeLeft -> -1f
                    Gesture.SwipeRight -> 1f
                    else -> 0f
                }
                val y = when (gesture) {
                    Gesture.SwipeUp -> -1f
                    Gesture.SwipeDown -> 1f
                    else -> 0f
                }

                currentOffset3D = if (anim == ScaffoldAnimation.Push) {
                    Offset3D(
                        x * size.width * progress,
                        y * size.height * progress
                    )
                } else {
                    Offset3D(
                        x * rubberbandThreshold * (1f - progress),
                        y * rubberbandThreshold * (1f - progress)
                    )
                }

            }
        }
    }

    private val backInterpolation = PathInterpolator(0f, 0f, 0f, 1f)
    fun onPredictiveBack(progress: Float) {
        if (!isSettledOnSecondaryPage) return
        val controller = getAnimationController(currentAnimation) ?: return

        val progress = backInterpolation.getInterpolation(progress)

        val p = controller.calculatePredictiveBackProgress(progress)

        setProgress(p)
    }

    suspend fun onPredictiveBackCancel() {
        val gesture = currentGesture ?: return
        val anim = currentAnimation ?: return

        if (gesture.orientation == null) {
            animateOffset3DTo(
                currentOffset3D.copy(z = 1f),
                animationSpec = tween(100),
            )
        } else {
            val targetOffset = if (anim == ScaffoldAnimation.Rubberband) {
                Offset.Zero
            } else {
                when (gesture) {
                    Gesture.SwipeLeft -> Offset(-size.width, 0f)
                    Gesture.SwipeRight -> Offset(size.width, 0f)
                    Gesture.SwipeUp -> Offset(0f, -size.height)
                    Gesture.SwipeDown -> Offset(0f, size.height)
                    else -> Offset.Zero
                }
            }

            animateOffset3DTo(currentOffset3D.copy(x = targetOffset.x, y = targetOffset.y))
        }
    }

    /**
     * End the predictive back gesture and return to the home page.
     */
    suspend fun onPredictiveBackEnd() {
        navigateBack()
    }

    suspend fun navigateBack(fast: Boolean = false) {
        val gesture = currentGesture ?: return
        val component = currentComponent ?: return

        unlock()
        isSettledOnSecondaryPage = false

        transitionToPage(ScaffoldPage.Home, component) {
            if (gesture.orientation == null) {
                animateOffset3DTo(
                    Offset3D.Zero,
                    animationSpec = tween(100),
                )
            } else {
                animateOffset3DTo(
                    Offset3D.Zero,
                    animationSpec = if (fast) tween(150) else spring(dampingRatio = bounceDampingRatio),
                )
            }
        }
    }

    private suspend fun performTapGesture(gesture: Gesture) {
        val component = config[gesture]?.component ?: return

        if (component.hapticFeedback) onHapticFeedback(HapticFeedbackType.LongPress)

        if (component is SearchComponent && gesture != Gesture.TapSearchBar) {
            openSearch()
            return
        }
        currentGesture = gesture

        currentOffset3D = currentOffset3D.copy(z = 0f)
        transitionToPage(ScaffoldPage.Secondary, component) {
            animateOffset3DTo(
                Offset3D(z = 1f),
                animationSpec = tween(300),
            )
            isSettledOnSecondaryPage = true
        }
    }

    private suspend fun transitionToPage(
        targetPage: ScaffoldPage,
        component: ScaffoldComponent,
        animate: suspend () -> Unit,
    ) = withContext(NonCancellable) {
        when (targetPage) {
            ScaffoldPage.Secondary -> {
                config.homeComponent.onPreDismiss(this@LauncherScaffoldState)
                component.onPreActivate(this@LauncherScaffoldState)
            }

            ScaffoldPage.Home -> {
                component.onPreDismiss(this@LauncherScaffoldState)
                config.homeComponent.onPreActivate(this@LauncherScaffoldState)
            }
        }

        animate()

        when (targetPage) {
            ScaffoldPage.Secondary -> {
                component.onActivate(this@LauncherScaffoldState)
                config.homeComponent.onDismiss(this@LauncherScaffoldState)
                homePageSearchBarOffset = 0f
            }

            ScaffoldPage.Home -> {
                config.homeComponent.onActivate(this@LauncherScaffoldState)
                component.onDismiss(this@LauncherScaffoldState)
                currentGesture = null
                secondaryPageSearchBarOffset = 0f
            }
        }
    }

    private suspend fun openSearch() {
        if (currentComponent != null && currentComponent !is SearchComponent) {
            navigateBack(fast = true)
        }

        if (config.homeComponent is SearchComponent) {
            return
        }

        // If there is any swipe gesture that is a SearchComponent, this takes precedence over the tap
        // This allows to close the search with a reversed swipe
        val gestures =
            listOf(Gesture.SwipeDown, Gesture.SwipeUp, Gesture.SwipeLeft, Gesture.SwipeRight)
        val gesture = gestures.find { config[it]?.component is SearchComponent }

        if (gesture == null) {
            performTapGesture(Gesture.TapSearchBar)
            return
        }
        isOpeningSearch = true
        navigateToPage(gesture)
        isOpeningSearch = false
    }

    suspend fun navigateToPage(gesture: Gesture) {
        currentGesture = gesture
        val anim = config[gesture]?.animation ?: return
        val component = config[gesture]!!.component

        transitionToPage(ScaffoldPage.Secondary, component) {
            if (anim == ScaffoldAnimation.Rubberband) {
                val targetOffset = when (gesture) {
                    Gesture.SwipeLeft -> Offset(-rubberbandThreshold, 0f)
                    Gesture.SwipeRight -> Offset(rubberbandThreshold, 0f)
                    Gesture.SwipeUp -> Offset(0f, -rubberbandThreshold)
                    Gesture.SwipeDown -> Offset(0f, rubberbandThreshold)
                    else -> Offset.Zero
                }

                animateOffset3DTo(currentOffset3D.copy(x = targetOffset.x, y = targetOffset.y))
                isSettledOnSecondaryPage = true
                animateOffset3DTo(currentOffset3D.copy(x = 0f, y = 0f))
            } else {
                val targetOffset = when (gesture) {
                    Gesture.SwipeLeft -> Offset(-size.width, 0f)
                    Gesture.SwipeRight -> Offset(size.width, 0f)
                    Gesture.SwipeUp -> Offset(0f, -size.height)
                    Gesture.SwipeDown -> Offset(0f, size.height)
                    else -> Offset.Zero
                }
                animateOffset3DTo(currentOffset3D.copy(x = targetOffset.x, y = targetOffset.y))
                isSettledOnSecondaryPage = true
            }
        }
    }

    suspend fun reset() {
        isSearchBarFocused = false
        currentOffset3D = Offset3D.Zero
        unlock()
        isSettledOnSecondaryPage = false

        currentComponent?.let {
            transitionToPage(ScaffoldPage.Home, it) {
                // No extra animation required on reset.
            }
        }
    }

    /**
     * If true, all gestures are ignored.
     */
    var isLocked by mutableStateOf(initialIsLocked)
        private set
    var isSearchBarHidden by mutableStateOf(initialIsSearchBarHidden)
        private set

    /**
     * Lock the scaffold to the current page, disable all gestures and animations.
     * Optionally hide the search bar.
     */
    suspend fun lock(hideSearchBar: Boolean = false) {
        isLocked = true
        if (hideSearchBar) {
            isSearchBarHidden = true
            searchBarAnimatable.snapTo(currentSearchBarOffset)
            searchBarAnimatable.animateTo(
                if (config.searchBarPosition == SearchBarPosition.Bottom) maxSearchBarOffset else -maxSearchBarOffset,
                tween(500)
            ) {
                if (isSettledOnSecondaryPage) {
                    secondaryPageSearchBarOffset = this.value
                } else {
                    homePageSearchBarOffset = this.value
                }
            }
        }
    }

    suspend fun unlock() {
        isLocked = false
        if (isSearchBarHidden) {
            isSearchBarHidden = false
            resetSearchBarOffset()
        }
    }

    suspend fun resetSearchBarOffset() {
        searchBarAnimatable.snapTo(currentSearchBarOffset)
        searchBarAnimatable.animateTo(
            0f,
            tween(500)
        ) {
            if (isSettledOnSecondaryPage) {
                secondaryPageSearchBarOffset = this.value
            } else {
                homePageSearchBarOffset = this.value
            }
        }
    }
}
