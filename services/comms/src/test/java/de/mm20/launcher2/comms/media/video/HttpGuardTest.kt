package de.mm20.launcher2.comms.media.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpGuardTest {
    @Test fun publicAddressesPass() {
        assertTrue(Http.isPublicWeb("https://rest.opensubtitles.org/search/query-x"))
        assertTrue(Http.isPublicWeb("http://dl.opensubtitles.org/en/download/x.gz"))
        assertTrue(Http.isPublicWeb("https://8.8.8.8/x"))
        assertTrue(Http.isPublicWeb("https://172.32.0.1/x"))
    }

    @Test fun localAddressesAreRefused() {
        for (u in listOf(
            "http://127.0.0.1:8080/x", "http://localhost/x", "https://192.168.1.2/x", "http://10.0.0.5/x",
            "http://172.16.0.1/x", "http://169.254.169.254/latest", "http://[::1]/x", "http://printer.local/x",
            "file:///etc/passwd", "ftp://example.com/x", "javascript:alert(1)", "not a url", "http://100.64.0.1/x",
        )) assertFalse(u, Http.isPublicWeb(u))
    }
}
