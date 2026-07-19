package de.mm20.launcher2.ui.webapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideSettings
import de.mm20.launcher2.ui.theme.LauncherTheme
import de.mm20.launcher2.webapp.WebAppLaunchContract

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

        setContent {
            LauncherTheme {
                ProvideSettings {
                    WebAppScreen(
                        url = url,
                        label = label,
                        onOpenExternally = { openExternally(it) },
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    private fun openExternally(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}

@android.annotation.SuppressLint("SetJavaScriptEnabled")
@androidx.compose.runtime.Composable
private fun WebAppScreen(
    url: String,
    label: String,
    onOpenExternally: (String) -> Unit,
    onClose: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(url) }
    var showMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        val wv = webView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onClose()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painterResource(R.drawable.close_24px),
                            contentDescription = stringResource(R.string.menu_back),
                        )
                    }
                },
                actions = {
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
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.web_app_open_externally)) },
                            onClick = {
                                showMenu = false
                                onOpenExternally(currentUrl)
                            },
                        )
                    }
                }
            )
        }
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
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                                super.onPageFinished(view, loadedUrl)
                                if (loadedUrl != null) currentUrl = loadedUrl
                            }
                        }
                        webView = this
                        loadUrl(url)
                    }
                },
            )
        }
    }
}
