package de.mm20.launcher2.ui.webapp

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.search.WebAppShortcut
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.koin.core.component.get
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

        createNotificationChannel()

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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_web_apps)
            val descriptionText = getString(R.string.notification_channel_web_apps_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    inner class WebAppNotificationBridge(
        private val label: String,
        private val shortcutKey: String?
    ) {
        @JavascriptInterface
        fun showNotification(title: String, body: String, tag: String?) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val browsingSettings: WebAppBrowsingSettings = org.koin.java.KoinJavaComponent.getKoin().get()

            val isGroupEnabled = runBlocking { browsingSettings.groupsEnabled.first() }
            if (isGroupEnabled && shortcutKey != null) {
                val groups = runBlocking { browsingSettings.groups.first() }
                val group = groups.find { it.appKeys.contains(shortcutKey) }
                if (group != null && !group.notificationsEnabled) {
                    return
                }
            }

            val launchIntent = Intent(this@WebAppActivity, WebAppActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtras(intent)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            
            val pendingIntent = PendingIntent.getActivity(
                this@WebAppActivity,
                shortcutKey?.hashCode() ?: 0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this@WebAppActivity, CHANNEL_ID)
                .setSmallIcon(R.drawable.language_24px)
                .setContentTitle(title)
                .setContentText(body)
                .setSubText(label)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            nm.notify(tag?.hashCode() ?: (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "web_app_notifications"
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
    val groupsEnabled by browsingSettings.groupsEnabled.collectAsStateWithLifecycle(false)
    val groups by browsingSettings.groups.collectAsStateWithLifecycle(emptyList())

    val activity = LocalActivity.current as WebAppActivity

    // Each shortcut - including the one this screen was launched with - gets its own WebView, so
    // its back/forward history is never contaminated by a different web app's navigation. Swiping
    // only changes which already-created WebView is attached to the container below; it's never a
    // shared loadUrl() the way it used to be. Only shortcuts using the embedded renderer are
    // included - one configured for Custom Tabs can't be shown here at all.
    val shortcuts by webAppShortcutRepository.search("", false)
        .collectAsStateWithLifecycle(emptyList())
    val embeddedShortcuts = remember(shortcuts, groups, groupsEnabled, shortcutKey) {
        val allEmbedded = shortcuts.filter { it.rendererPackage == null }.sortedBy { it.order }
        if (!groupsEnabled || shortcutKey == null) return@remember allEmbedded

        val currentGroup = groups.find { it.appKeys.contains(shortcutKey) }
        if (currentGroup == null) {
            // Only show ungrouped apps
            allEmbedded.filter { pkg -> groups.none { it.appKeys.contains(pkg.key) } }
        } else {
            // Only show apps in the same group
            allEmbedded.filter { currentGroup.appKeys.contains(it.key) }
        }
    }
    val initialIndex = remember(embeddedShortcuts, shortcutKey) {
        embeddedShortcuts.indexOfFirst { it.key == shortcutKey }.coerceAtLeast(0)
    }
    val activeShortcut = swipeIndex?.let { embeddedShortcuts.getOrNull(it) }
    val editTarget = activeShortcut
        ?: embeddedShortcuts.find { it.key == shortcutKey }

    // Stable identity for the screen's own initial content - falls back to the url itself if this
    // screen wasn't launched with a shortcut key at all (defensive; shouldn't happen in practice).
    val initialKey = remember(shortcutKey, url) { shortcutKey ?: "url:$url" }
    val activeKey = activeShortcut?.key ?: initialKey

    // Per-key metadata lookups, kept live via rememberUpdatedState so an edit to a shortcut (CSS,
    // notifications, label) takes effect on that web app's next page load without needing to
    // recreate its WebView. Resolved against the FULL shortcuts list (not the group-filtered
    // [embeddedShortcuts]) so the initial shortcut always resolves even if group filtering would
    // otherwise exclude it from the swipe list.
    val shortcutsState = rememberUpdatedState(shortcuts)
    fun shortcutForKey(key: String) =
        shortcutsState.value.find { it.key == (if (key == initialKey) shortcutKey else key) }
    fun cssForKey(key: String): String? =
        shortcutForKey(key)?.customCss ?: customCss.takeIf { key == initialKey }
    /** Per web app ad blocker mode wins over the global setting unless it is set to Global. */
    fun adBlockEnabledForKey(key: String): Boolean =
        when (shortcutForKey(key)?.adBlockMode) {
            WebAppShortcut.AdBlockMode.On -> true
            WebAppShortcut.AdBlockMode.Off -> false
            else -> adBlockEnabledState.value
        }
    fun notificationsEnabledForKey(key: String): Boolean =
        shortcutForKey(key)?.notificationsEnabled ?: false
    fun labelForKey(key: String): String =
        shortcutForKey(key)?.let { it.labelOverride ?: it.label } ?: label

    val displayLabel = labelForKey(activeKey)
    val activeNotificationsEnabled = notificationsEnabledForKey(activeKey)

    if (activeNotificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ -> }

        LaunchedEffect(activeKey) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val webViewEntries = remember { mutableMapOf<String, WebView>() }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    val activeKeyState = rememberUpdatedState(activeKey)

    DisposableEffect(Unit) {
        onDispose {
            webViewEntries.values.forEach {
                (it.parent as? ViewGroup)?.removeView(it)
                it.destroy()
            }
            webViewEntries.clear()
        }
    }

    BackHandler(enabled = true) {
        val wv = activeWebView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onClose()
        }
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
                IconButton(onClick = { activeWebView?.goBack() }, enabled = canGoBack) {
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
                IconButton(onClick = { activeWebView?.goForward() }, enabled = canGoForward) {
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
                                text = { Text(stringResource(R.string.dl_p3_download_with_telos)) },
                                onClick = {
                                    showMenu = false
                                    de.mm20.launcher2.ui.downloads.openInTelosDownloads(context, currentUrl)
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
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { container ->
                    val key = activeKey
                    val isNewWebView = key !in webViewEntries
                    val wv = webViewEntries.getOrPut(key) {
                        val keyNotificationsEnabled = notificationsEnabledForKey(key)
                        WebView(container.context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.setSupportZoom(zoomControlsEnabled)
                            settings.builtInZoomControls = zoomControlsEnabled
                            settings.displayZoomControls = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false

                            if (keyNotificationsEnabled) {
                                val bridgeKey = if (key == initialKey) shortcutKey else key
                                addJavascriptInterface(
                                    activity.WebAppNotificationBridge(labelForKey(key), bridgeKey),
                                    "WebAppNotificationBridge"
                                )
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                                    super.onPageFinished(view, loadedUrl)
                                    // Background WebViews (of a web app the user swiped away
                                    // from) can still finish loading in-flight requests; only
                                    // the currently active one should update the toolbar state.
                                    if (key != activeKeyState.value) return
                                    if (loadedUrl != null) currentUrl = loadedUrl
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                    val css = cssForKey(key)
                                    if (!css.isNullOrBlank()) {
                                        view?.evaluateJavascript(injectCssScript(css), null)
                                    }
                                    if (keyNotificationsEnabled) {
                                        view?.evaluateJavascript(notificationPolyfillScript(), null)
                                    }
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): WebResourceResponse? {
                                    if (adBlockEnabledForKey(key) && adBlocker.shouldBlock(request.url.host)) {
                                        return adBlocker.blockedResponse()
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    if (trackingParamStrippingEnabledState.value && request.isForMainFrame && request.method == "GET") {
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
                                try {
                                    val fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                                    val request = DownloadManager.Request(downloadUrl.toUri())
                                        .setMimeType(mimeType)
                                        .addRequestHeader("User-Agent", userAgent)
                                        .setDescription(fileName)
                                        .setTitle(fileName)
                                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                                    // getCookie() is null without cookies and addRequestHeader() throws on null
                                    CookieManager.getInstance().getCookie(downloadUrl)?.let { request.addRequestHeader("cookie", it) }
                                    context.getSystemService<DownloadManager>()?.enqueue(request)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.web_app_download_started, fileName),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } catch (e: Exception) {
                                    // blob:/data: URLs and other non-HTTP schemes cannot be handled by DownloadManager
                                    android.util.Log.w("WebAppActivity", "Download failed", e)
                                }
                            }
                            val targetUrl = if (key == initialKey) {
                                url
                            } else {
                                shortcutForKey(key)?.url ?: url
                            }
                            loadUrl(
                                if (trackingParamStrippingEnabled) TrackingParamStripper.strip(targetUrl)
                                else targetUrl,
                            )
                        }
                    }
                    if (container.getChildAt(0) !== wv) {
                        activeWebView?.let {
                            it.onPause()
                            it.pauseTimers()
                        }
                        container.removeAllViews()
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        container.addView(wv)
                        wv.onResume()
                        wv.resumeTimers()
                        activeWebView = wv
                        // A WebView we're switching back to already finished loading in the
                        // past - nothing will re-fire onPageFinished, so pull its current nav
                        // state directly instead of waiting for an event that isn't coming.
                        if (!isNewWebView) {
                            currentUrl = wv.url ?: currentUrl
                            canGoBack = wv.canGoBack()
                            canGoForward = wv.canGoForward()
                        }
                    }
                },
            )
        }
    }

    if (editTarget != null) {
        EditWebAppShortcutSheet(
            expanded = showEditSheet,
            existing = editTarget,
            onSave = { newLabel, newUrl, iconUri, faviconUrl, rendererPackage, showInGrid, showInPanel, iconSource, newCustomCss, newNotificationsEnabled, newGroupId, newAdBlockMode ->
                webAppShortcutRepository.update(
                    editTarget, newLabel, newUrl, iconUri, faviconUrl, rendererPackage,
                    showInGrid, showInPanel, editTarget.order, iconSource, newCustomCss,
                    newNotificationsEnabled, newAdBlockMode,
                )
                val browsingSettings: de.mm20.launcher2.preferences.ui.WebAppBrowsingSettings = org.koin.java.KoinJavaComponent.getKoin().get()
                kotlinx.coroutines.MainScope().launch {
                    val groups: List<de.mm20.launcher2.preferences.WebAppGroup> = browsingSettings.groups.first()
                    val currentGroups = groups.map { g ->
                        if (g.id == newGroupId) {
                            if (!g.appKeys.contains(editTarget.key)) g.copy(appKeys = g.appKeys + editTarget.key) else g
                        } else {
                            g.copy(appKeys = g.appKeys - editTarget.key)
                        }
                    }
                    browsingSettings.setGroups(currentGroups)
                }
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

private fun notificationPolyfillScript(): String {
    return """
        (function() {
            if (window.Notification && window.Notification.__kvaesitso_polyfill) return;

            var NativeNotification = window.Notification;

            window.Notification = function(title, options) {
                this.title = title;
                this.body = options ? options.body : '';
                this.tag = options ? options.tag : '';
                
                if (typeof WebAppNotificationBridge !== 'undefined') {
                    WebAppNotificationBridge.showNotification(this.title, this.body, this.tag);
                } else if (NativeNotification) {
                    new NativeNotification(title, options);
                }
            };

            window.Notification.__kvaesitso_polyfill = true;
            window.Notification.permission = 'granted';
            window.Notification.requestPermission = function(callback) {
                var promise = Promise.resolve('granted');
                if (callback) promise.then(callback);
                return promise;
            };
            
            // Handle Notification.permission read
            Object.defineProperty(window.Notification, 'permission', {
                get: function() { return 'granted'; }
            });

            // For older apps using navigator.serviceWorker.ready.then(reg => reg.showNotification(...))
            if (navigator.serviceWorker) {
                var originalRegister = navigator.serviceWorker.register;
                navigator.serviceWorker.register = function() {
                    return originalRegister.apply(this, arguments).then(function(reg) {
                        var originalShow = reg.showNotification;
                        reg.showNotification = function(title, options) {
                            window.Notification(title, options);
                        };
                        return reg;
                    });
                };
            }
        })();
    """.trimIndent()
}
