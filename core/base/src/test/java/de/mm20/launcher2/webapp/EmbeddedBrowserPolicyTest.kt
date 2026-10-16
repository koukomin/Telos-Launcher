package de.mm20.launcher2.webapp

import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddedBrowserPolicyTest {
    @Test fun bundledEngineIsTheDefault() {
        assertEquals(EmbeddedBrowserPolicy.Ironewolf,
            EmbeddedBrowserPolicy.select(EmbeddedBrowserPolicy.Default, true))
    }

    @Test fun explicitCompatibilityChoiceIsRetained() {
        assertEquals(EmbeddedBrowserPolicy.WebView,
            EmbeddedBrowserPolicy.select(EmbeddedBrowserPolicy.WebView, true))
    }

    @Test fun absentRuntimeCannotBreakExistingWebApps() {
        assertEquals(EmbeddedBrowserPolicy.WebView,
            EmbeddedBrowserPolicy.select(EmbeddedBrowserPolicy.Default, false))
    }

    @Test fun unknownPersistedValuesUseTheAvailableDefault() {
        assertEquals(EmbeddedBrowserPolicy.Ironewolf,
            EmbeddedBrowserPolicy.select("old-value", true))
        assertEquals(EmbeddedBrowserPolicy.WebView,
            EmbeddedBrowserPolicy.select("old-value", false))
    }
}
