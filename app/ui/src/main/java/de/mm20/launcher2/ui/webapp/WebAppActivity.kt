package de.mm20.launcher2.ui.webapp

import android.app.DownloadManager
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.preferences.ui.WebAppBrowsingSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideSettings
import de.mm20.launcher2.ui.overlays.OverlayHost
import de.mm20.launcher2.ui.settings.SettingsActivity
import de.mm20.launcher2.ui.settings.webapps.EditWebAppShortcutSheet
import de.mm20.launcher2.ui.theme.LauncherTheme
import de.mm20.launcher2.ui.webappspanel.WebAppsPanelManager
import de.mm20.launcher2.webapp.WebAppLaunchContract
import de.mm20.launcher2.webappshortcuts.WebAppShortcutRepository
import org.json.JSONObject
import org.koin.compose.koinInject

/**
 * The embedded WebView renderer for a WebAppShortcut (the default, and the silent fallback when
 * a chosen Custom Tabs browser is no longer installed/no longer supports Custom Tabs). Reached
 * only via an implicit intent by class name - see [WebAppLaunchContract] and
 * [de.mm20.launcher2.webappshortcuts.WebAppShortcutImpl.launch].
 */
class WebAppActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(WebAppLaunchContract.EXTRA_URL) ?: return finish()
        val label = intent.getStringExtra(WebAppLaunchContract.EXTRA_LABEL) ?: ""
        val customCss = intent.getStringExtra(WebAppLaunchContract.EXTRA_CUSTOM_CSS)
        val shortcutKey = intent.getStringExtra(WebAppLaunchContract.EXTRA_KEY)

        setContent {
            LauncherTheme {
                ProvideSettings {
                    // EditWebAppShortcutSheet (like every other BottomSheet in the app) renders
                    // itself via Overlay/LocalOverlayManager, which without an OverlayHost ancestor
                    // falls back to a fresh, unwatched OverlayManager - the sheet would register
                    // itself but nothing would ever draw it, so the "Edit this web app" menu
                    // action would silently no-op.
                    OverlayHost(modifier = Modifier.fillMaxSize()) {
                        WebAppScreen(
                            url = url,
                            label = label,
                            customCss = customCss,
                            shortcutKey = shortcutKey,
                            onOpenExternally = { openExternally(it) },
                            onClose = { finish() },
                        )
                    }
                }
            }
        }
    }

    private fun openExternally(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}

@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebAppScreen(
    url: String,
    label: String,
    customCss: String?,
    shortcutKey: String?,
    onOpenExternally: (String) -> Unit,
    onClose: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(url) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    /** Index into [shortcuts] the user has swiped to, or null while still showing the shortcut
     * this screen was originally launched with (see [WebAppScreen]'s doc on why the initial load
     * doesn't just start from this list instead). */
    var swipeIndex by remember { mutableStateOf<Int?>(null) }

    val context = LocalContext.current
    val density = LocalDensity.current
    val browsingSettings: WebAppBrowsingSettings = koinInject()
    val webAppShortcutRepository: WebAppShortcutRepository = koinInject()
    val panelManager: WebAppsPanelManager = koinInject()

    val adBlockEnabled by browsingSettings.adBlockEnabled.collectAsStateWithLifecycle(true)
    val adBlockEnabledState = rememberUpdatedState(adBlockEnabled)
    val adBlocker = remember { WebAdBlocker(context) }
    val zoomControlsEnabled by browsingSettings.zoomControlsEnabled.collectAsStateWithLifecycle(true)
    val trackingParamStrippingEnabled by browsingSettings.trackingParamStrippingEnabled.collectAsStateWithLifecycle(true)
    val trackingParamStrippingEnabledState = rememberUpdatedState(trackingParamStrippingEnabled)
    val topBarAtBottom by browsingSettings.topBarAtBottom.collectAsStateWithLifecycle(false)
    val swipeToSwitchEnabled by browsingSettings.swipeToSwitchEnabled.collectAsStateWithLifecycle(true)

    // Not a session/tab pool - each web app shortcut still launches its own Activity instance
    // (see WebAppShortcutImpl.launch). "Switching" a swipe lands on just reuses *this* instance's
    // WebView for a different shortcut's URL, which is what makes it feel like a tab strip
    // without the memory cost of keeping every shortcut's WebView alive at once. Only shortcuts
    // using the embedded renderer are included - one configured for Custom Tabs can't be shown
    // here at all.
    val shortcuts by webAppShortcutRepository.search("", false)
        .collectAsStateWithLifecycle(emptyList())
    val embeddedShortcuts = remember(shortcuts) {
        shortcuts.filter { it.rendererPackage == null }.sortedBy { it.order }
    }
    val initialIndex = remember(embeddedShortcuts, shortcutKey) {
        embeddedShortcuts.indexOfFirst { it.key == shortcutKey }.coerceAtLeast(0)
    }
    val activeShortcut = swipeIndex?.let { embeddedShortcuts.getOrNull(it) }
    val editTarget = activeShortcut
        ?: embeddedShortcuts.find { it.key == shortcutKey }

    val displayLabel = activeShortcut?.let { it.labelOverride ?: it.label } ?: label
    val displayCustomCssState = rememberUpdatedState(activeShortcut?.customCss ?: customCss)

    BackHandler(enabled = true) {
        val wv = webView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onClose()
        }
    }

    // Only fires once the user has actually swiped (swipeIndex != null) - the initial shortcut is
    // loaded directly by the WebView factory below, using the intent's own url/customCss, so this
    // doesn't double-load it.
    LaunchedEffect(activeShortcut?.key) {
        val shortcut = activeShortcut ?: return@LaunchedEffect
        val wv = webView ?: return@LaunchedEffect
        val targetUrl = if (trackingParamStrippingEnabled) {
            TrackingParamStripper.strip(shortcut.url)
        } else {
            shortcut.url
        }
        wv.loadUrl(targetUrl)
    }

    val swipeThresholdPx = with(density) { 72.dp.toPx() }

    // A hand-rolled Row instead of CenterAlignedTopAppBar: that composable's navigationIcon slot
    // is sized for exactly one icon, so a second one (the back arrow, alongside close) ended up
    // overlapping it instead of sitting beside it - there's no supported way to widen that slot.
    val topBar: @Composable () -> Unit = {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Order matters: the inset padding must be outside the fixed height, or it
                    // eats into the 64dp instead of adding space above/below it - which squeezed
                    // the bar's own content (icons, title) until it clipped against the WebView.
                    .windowInsetsPadding(if (topBarAtBottom) WindowInsets.navigationBars else WindowInsets.statusBars)
                    .height(64.dp)
                    .pointerInput(embeddedShortcuts, swipeToSwitchEnabled) {
                        if (!swipeToSwitchEnabled || embeddedShortcuts.size <= 1) return@pointerInput
                        var dragAccumulator = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = { dragAccumulator = 0f },
                            onDragCancel = { dragAccumulator = 0f },
                        ) { change, dragAmount ->
                            change.consume()
                            dragAccumulator += dragAmount
                            val base = swipeIndex ?: initialIndex
                            if (dragAccumulator > swipeThresholdPx) {
                                dragAccumulator = 0f
                                swipeIndex = (base - 1 + embeddedShortcuts.size) % embeddedShortcuts.size
                            } else if (dragAccumulator < -swipeThresholdPx) {
                                dragAccumulator = 0f
                                swipeIndex = (base + 1) % embeddedShortcuts.size
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        painterResource(R.drawable.close_24px),
                        contentDescription = stringResource(R.string.menu_back),
                    )
                }
                IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                    Icon(
                        painterResource(R.drawable.arrow_back_24px),
                        contentDescription = stringResource(R.string.web_app_go_back),
                    )
                }
                Text(
                    text = displayLabel,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) {
                    Icon(
                        painterResource(R.drawable.arrow_forward_24px),
                        contentDescription = stringResource(R.string.web_app_go_forward),
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            painterResource(R.drawable.more_vert_24px),
                            contentDescription = null,
                        )
                    }
                    DropdownMenuPopup(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        // DropdownMenuGroup wraps its content in an opaque Surface (unlike a bare
                        // DropdownMenuPopup, which has no background of its own) - without it,
                        // this menu rendered see-through onto whatever the WebView underneath was
                        // showing, so e.g. white page content made the menu's own text unreadable.
                        DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.web_app_open_externally)) },
                                onClick = {
                                    showMenu = false
                                    onOpenExternally(currentUrl)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.web_app_settings)) },
                                onClick = {
                                    showMenu = false
                                    context.startActivity(
                                        Intent(context, SettingsActivity::class.java).apply {
                                            putExtra(
                                                SettingsActivity.EXTRA_ROUTE,
                                                SettingsActivity.ROUTE_WEB_APPS,
                                            )
                                        }
                                    )
                                },
                            )
                            if (editTarget != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.web_app_shortcut_edit)) },
                                    onClick = {
                                        showMenu = false
                                        showEditSheet = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = { if (!topBarAtBottom) topBar() },
        bottomBar = { if (topBarAtBottom) topBar() },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportZoom(zoomControlsEnabled)
                        settings.builtInZoomControls = zoomControlsEnabled
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                                super.onPageFinished(view, loadedUrl)
                                if (loadedUrl != null) currentUrl = loadedUrl
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                                val css = displayCustomCssState.value
                                if (!css.isNullOrBlank()) {
                                    view?.evaluateJavascript(injectCssScript(css), null)
                                }
                            }

                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? {
                                if (adBlockEnabledState.value && adBlocker.shouldBlock(request.url.host)) {
                                    return adBlocker.blockedResponse()
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                if (trackingParamStrippingEnabledState.value && request.isForMainFrame) {
                                    val original = request.url.toString()
                                    val stripped = TrackingParamStripper.strip(original)
                                    if (stripped != original) {
                                        view.loadUrl(stripped)
                                        return true
                                    }
                                }
                                return super.shouldOverrideUrlLoading(view, request)
                            }
                        }
                        setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
                            val fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                            val request = DownloadManager.Request(downloadUrl.toUri())
                                .setMimeType(mimeType)
                                .addRequestHeader("cookie", CookieManager.getInstance().getCookie(downloadUrl))
                                .addRequestHeader("User-Agent", userAgent)
                                .setDescription(fileName)
                                .setTitle(fileName)
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                            context.getSystemService<DownloadManager>()?.enqueue(request)
                            Toast.makeText(
                                context,
                                context.getString(R.string.web_app_download_started, fileName),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        webView = this
                        loadUrl(
                            if (trackingParamStrippingEnabled) TrackingParamStripper.strip(url)
                            else url,
                        )
                    }
                },
            )
        }
    }

    if (editTarget != null) {
        EditWebAppShortcutSheet(
            expanded = showEditSheet,
            existing = editTarget,
            onSave = { newLabel, newUrl, iconUri, faviconUrl, rendererPackage, showInGrid, showInPanel, iconSource, newCustomCss ->
                webAppShortcutRepository.update(
                    editTarget, newLabel, newUrl, iconUri, faviconUrl, rendererPackage,
                    showInGrid, showInPanel, editTarget.order, iconSource, newCustomCss,
                )
                showEditSheet = false
            },
            onDismiss = { showEditSheet = false },
            onImportIcon = { uri, sizePx -> panelManager.importIcon(uri, sizePx) },
            onFindFavicon = { favUrl -> panelManager.findFavicon(favUrl) },
            onExportIconPackIcon = { customIcon, sizePx -> panelManager.exportIconPackIcon(customIcon, sizePx) },
        )
    }
}

/** A `<style>` tag keyed by id, so re-running this on every page load (incl. in-page navigation)
 * replaces the previous content instead of stacking duplicate tags. */
private fun injectCssScript(css: String): String {
    val quotedCss = JSONObject.quote(css)
    return """
        (function() {
            var style = document.getElementById('__kvaesitso_custom_css');
            if (!style) {
                style = document.createElement('style');
                style.id = '__kvaesitso_custom_css';
                document.head.appendChild(style);
            }
            style.textContent = $quotedCss;
        })();
    """.trimIndent()
}
