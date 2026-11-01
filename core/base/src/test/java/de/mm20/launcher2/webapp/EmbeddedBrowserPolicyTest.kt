package de.mm20.launcher2.webapp

import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddedBrowserPolicyTest {
    @Test fun webViewIsTheOnlyEngine() {
        assertEquals(EmbeddedBrowserPolicy.WebView, EmbeddedBrowserPolicy.select(EmbeddedBrowserPolicy.Default))
        assertEquals(EmbeddedBrowserPolicy.WebView, EmbeddedBrowserPolicy.select(EmbeddedBrowserPolicy.WebView))
        assertEquals(EmbeddedBrowserPolicy.WebView, EmbeddedBrowserPolicy.select("old-value"))
        assertEquals(EmbeddedBrowserPolicy.WebView, EmbeddedBrowserPolicy.select("ironewolf"))
    }
}
