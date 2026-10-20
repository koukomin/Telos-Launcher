package de.mm20.launcher2.ui.ermis

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.util.Locale

/** A passive local document, never a signed-in webmail page. No resource request is permitted. */
@Composable
internal fun ErmisHtmlBody(html: String, plain: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val openLink by rememberUpdatedState<(String) -> Unit> { url -> runCatching { uriHandler.openUri(url) }; Unit }
    val foreground = MaterialTheme.colorScheme.onSurface.toArgb() and 0xFFFFFF
    val background = MaterialTheme.colorScheme.surface.toArgb() and 0xFFFFFF
    val fontScale = LocalDensity.current.fontScale
    AndroidView(modifier = modifier, factory = { context ->
        FrameLayout(context).apply {
          try {
            val browser = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = false
                domStorageEnabled = false
                databaseEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                blockNetworkLoads = true
                blockNetworkImage = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_NO_CACHE
                setSupportMultipleWindows(false)
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = true
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse =
                    WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url
                    if (request?.isForMainFrame == true && request.hasGesture() && uri?.scheme in setOf("https", "http") && uri.toString().length <= 2048) {
                        openLink(uri.toString())
                    }
                    return true
                }
            }
            }
            addView(browser, FrameLayout.LayoutParams(-1, -1))
          } catch (_: Exception) {
            addView(TextView(context), FrameLayout.LayoutParams(-1, -1))
          }
        }
    }, update = { container ->
        val child = container.getChildAt(0)
        if (child is TextView) {
            child.text = plain
            child.setTextColor(0xFF000000.toInt() or foreground)
            child.setBackgroundColor(0xFF000000.toInt() or background)
            child.textSize = 16f
        }
        val view = child as? WebView ?: return@AndroidView
        val key = listOf(html, foreground, background, fontScale)
        if (view.tag != key) {
            view.tag = key
            view.settings.textZoom = (fontScale * 100).toInt().coerceIn(80, 300)
            val ink = String.format(Locale.ROOT, "#%06x", foreground)
            val paper = String.format(Locale.ROOT, "#%06x", background)
            val page = """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; form-action 'none'; base-uri 'none'"><style>body{margin:8px;color:$ink;background:$paper;font-family:sans-serif;overflow-wrap:anywhere}table{max-width:100%;border-collapse:collapse}td,th{padding:4px}a{color:$ink;text-decoration:underline}pre{white-space:pre-wrap}</style></head><body dir="auto">$html</body></html>"""
            view.loadDataWithBaseURL(null, page, "text/html", "UTF-8", null)
        }
    }, onReset = null, onRelease = { container ->
        (container.getChildAt(0) as? WebView)?.let { view -> view.stopLoading(); view.removeAllViews(); view.destroy() }
        container.removeAllViews()
    })
}
