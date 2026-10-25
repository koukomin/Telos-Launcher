package de.mm20.launcher2.ui.media.youtube

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import de.mm20.launcher2.comms.youtube.YouTubeAccountManager

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    var loginCompleted = remember { false }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.95f),
        title = { Text("Sign in to YouTube") },
        text = {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                            // Strip WebView markers ("; wv" and "Version/x.x") from native Chromium UA
                            userAgentString = userAgentString
                                .replace("; wv", "")
                                .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                return false
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                if (loginCompleted) return

                                if (url.contains("youtube.com")) {
                                    val ytBaseUrl = "https://" + "www.youtube.com"
                                    val ytMusicUrl = "https://" + "music.youtube.com"
                                    val ytCookies = cookieManager.getCookie(ytBaseUrl).orEmpty()
                                    val musicCookies = cookieManager.getCookie(ytMusicUrl).orEmpty()
                                    val mergedCookies = mergeCookieStrings(ytCookies, musicCookies)

                                    if (mergedCookies.contains("SAPISID")) {
                                        loginCompleted = true
                                        cookieManager.flush()
                                        YouTubeAccountManager.setCookie(context, mergedCookies)
                                        onSuccess()
                                    }
                                }
                            }
                        }

                        val loginUrl = "https://" + "accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm%26hl%3Del%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"
                        loadUrl(loginUrl)
                    }
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun mergeCookieStrings(vararg cookieStrings: String): String {
    val map = LinkedHashMap<String, String>()
    for (cookieStr in cookieStrings) {
        if (cookieStr.isBlank()) continue
        cookieStr.split(";").forEach { part ->
            val trimmed = part.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val key = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim()
                map[key] = value
            }
        }
    }
    return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
}
