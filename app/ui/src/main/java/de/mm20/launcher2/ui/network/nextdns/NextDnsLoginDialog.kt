package de.mm20.launcher2.ui.network.nextdns

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NextDnsLoginDialog(
    onDismiss: () -> Unit,
    onSuccess: (String, String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(de.mm20.launcher2.ui.R.string.au7_trakt_sign_in) + " · NextDNS") },
        text = {
            AndroidView(
                onRelease = { it.stopLoading(); it.webViewClient = WebViewClient(); it.destroy() },
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                return request.url.scheme != "https" || request.url.host !in setOf("my.nextdns.io", "apple.nextdns.io", "nextdns.io")
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                if (android.net.Uri.parse(url).let { it.scheme == "https" && it.host in setOf("my.nextdns.io", "apple.nextdns.io") }) {
                                    // Inject script to extract the apikey and profile ID from localStorage
                                    view.evaluateJavascript(
                                        "(function() { return JSON.stringify(localStorage); })();"
                                    ) { result ->
                                        runCatching {
                                            // Result is a JSON string containing stringified JSON, need to parse carefully
                                            val unescaped = org.json.JSONTokener(result).nextValue() as? String ?: return@runCatching
                                            val storage = JSONObject(unescaped)
                                            // The key is usually stored in 'vuex' state or similar for NextDNS
                                            val vuexStr = storage.optString("vuex")
                                            if (vuexStr.isNotBlank()) {
                                                val vuex = JSONObject(vuexStr)
                                                val account = vuex.optJSONObject("account")
                                                val apiKey = account?.optString("apiKey")
                                                val config = vuex.optJSONObject("config")
                                                val profileId = config?.optString("id")
                                                if (!apiKey.isNullOrBlank() && !profileId.isNullOrBlank()) {
                                                    onSuccess(profileId, apiKey)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        loadUrl("https://my.nextdns.io/login")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(de.mm20.launcher2.ui.R.string.hc_close)) }
        }
    )
}
