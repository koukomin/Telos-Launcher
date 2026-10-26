package de.mm20.launcher2.ui.component

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.preferences.ui.WallpaperSettings
import de.mm20.launcher2.ui.theme.wallpaperColorsAsState
import de.mm20.launcher2.wallpapers.WallpapersService
import de.mm20.launcher2.wallpapers.VideoGlassRequest
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.math.roundToInt

internal data class FrostedBackdrop(val image: ImageBitmap?, val wholeWallpaper: ImageBitmap?)
internal val LocalFrostedBackdrop = staticCompositionLocalOf<State<FrostedBackdrop>?> { null }
internal val LocalGlassSurface = staticCompositionLocalOf { false }

/** One provider per Activity, inherited by inline Telos apps. Video readback samples only the wallpaper service surface. */
@Composable
fun FrostedGlassProvider(content: @Composable () -> Unit) {
    if (LocalFrostedBackdrop.current != null) { content(); return }
    val service: WallpapersService = koinInject()
    val settings: UiSettings = koinInject()
    val wallpaper: WallpaperSettings = koinInject()
    val radius by settings.drawerBackgroundBlur.collectAsStateWithLifecycle(32)
    val wholeEnabled by wallpaper.blurWallpaper.collectAsStateWithLifecycle(false)
    val wholeRadius by wallpaper.wallpaperBlurRadius.collectAsStateWithLifecycle(0)
    val revision by service.glassRevision.collectAsStateWithLifecycle()
    val colors by wallpaperColorsAsState()
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumed++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val config = LocalConfiguration.current
    val widthDp = config.screenWidthDp.coerceAtLeast(1)
    val heightDp = config.screenHeightDp.coerceAtLeast(1)
    var video by remember { mutableStateOf(false) }
    val backdrop = produceState(FrostedBackdrop(null, null), radius, wholeEnabled, wholeRadius, revision, colors, resumed, widthDp, heightDp) {
        video = false
        value = FrostedBackdrop(null, null) // Never keep old wallpaper pixels while validating a change.
        kotlinx.coroutines.delay(120) // Coalesce radius-slider and wallpaper-color updates.
        video = withContext(Dispatchers.IO) { service.isVideoWallpaperActive() }
        value = withContext(Dispatchers.IO) {
            FrostedBackdrop(
                if (radius > 0) service.loadGlassWallpaper(radius, widthDp, heightDp)?.asImageBitmap() else null,
                if (wholeEnabled && wholeRadius > 0) service.loadGlassWallpaper(wholeRadius, widthDp, heightDp)?.asImageBitmap() else null,
            )
        }
    }
    val request = remember(radius, widthDp, heightDp) { VideoGlassRequest(radius, widthDp, heightDp) }
    val wholeRequest = remember(wholeRadius, widthDp, heightDp) { VideoGlassRequest(wholeRadius, widthDp, heightDp) }
    LaunchedEffect(owner, video, request, wholeRequest, wholeEnabled) {
        if (video) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val token = Any()
            service.videoGlass.register(token, listOfNotNull(request.takeIf { radius > 0 },
                wholeRequest.takeIf { wholeEnabled && wholeRadius > 0 }))
            try { awaitCancellation() } finally { service.videoGlass.unregister(token) }
        }
    }
    val frames = service.videoGlass.frames.collectAsStateWithLifecycle()
    val resolved = remember(backdrop, frames, video, request, wholeRequest, wholeEnabled) {
        derivedStateOf {
            if (video) FrostedBackdrop(frames.value[request]?.asImageBitmap(),
                if (wholeEnabled) frames.value[wholeRequest]?.asImageBitmap() else null)
            else backdrop.value
        }
    }
    CompositionLocalProvider(LocalFrostedBackdrop provides resolved, content = content)
}

/** The tint is drawn exactly once. Every panel samples the same viewport-aligned snapshot. */
fun Modifier.frostedGlass(color: Color, shape: Shape = RectangleShape, enabled: Boolean = true,
    wholeWallpaper: Boolean = false, fallbackColor: Color? = null): Modifier = composed {
    val frame = LocalFrostedBackdrop.current
    val view = LocalView.current
    var position by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val resolved = color.copy(alpha = color.alpha.coerceIn(0f, 1f))
    this.onGloballyPositioned { coordinates ->
        position = coordinates.positionInWindow()
        viewport = IntSize(view.rootView.width, view.rootView.height)
    }.clip(shape).drawWithCache {
        val image = if (!enabled) null else if (wholeWallpaper) frame?.value?.wholeWallpaper else frame?.value?.image
        // A missing/stale wallpaper frame must not expose sharp pixels behind app text.
        val tint = frostedTint(resolved, image != null, fallbackColor)
        val crop = image?.let { FrostedGeometry.crop(it.width, it.height, viewport.width, viewport.height) }
        val destination = crop?.let { IntOffset((it.left - position.x).roundToInt(), (it.top - position.y).roundToInt()) }
        val outline = shape.createOutline(size, layoutDirection, this)
        val highlight = Brush.verticalGradient(listOf(Color.White.copy(alpha = .055f * (1f - resolved.alpha)), Color.Transparent))
        onDrawBehind {
            if (image != null && crop != null && destination != null && resolved.alpha < 1f) {
                drawImage(image, dstOffset = destination, dstSize = IntSize(crop.width, crop.height), filterQuality = FilterQuality.Low)
            }
            drawRect(tint)
            if (enabled && tint.alpha > 0f && tint.alpha < 1f) {
                drawRect(highlight)
                drawOutline(outline, Color.White.copy(alpha = .12f * (1f - resolved.alpha)), style = Stroke(1f))
            }
        }
    }
}

/** Root-only fallback; inner transparent chrome keeps its existing behavior. */
internal fun frostedTint(requested: Color, hasBackdrop: Boolean, fallback: Color?): Color =
    if (!hasBackdrop && fallback != null) fallback.copy(alpha = 1f) else requested
