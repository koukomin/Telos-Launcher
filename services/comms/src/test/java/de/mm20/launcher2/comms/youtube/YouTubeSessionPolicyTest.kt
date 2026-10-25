package de.mm20.launcher2.comms.youtube
import org.junit.Assert.*
import org.junit.Test
class YouTubeSessionPolicyTest {
    @Test fun credentialsAreRestrictedToHttpsYouTubeOrigins() {
        assertTrue(YouTubeSessionPolicy.trusted("https://music.youtube.com/youtubei/v1/browse"))
        assertFalse(YouTubeSessionPolicy.trusted("http://www.youtube.com/"))
        assertFalse(YouTubeSessionPolicy.trusted("https://youtube.com.evil.example/"))
        assertFalse(YouTubeSessionPolicy.trusted("https://evil.example/?next=youtube.com"))
        assertFalse(YouTubeSessionPolicy.trusted("https://youtube.com@evil.example/"))
    }
    @Test fun onlyExplicitAuthenticationFailuresExpireSession() {
        assertTrue(YouTubeSessionPolicy.expired(401)); assertTrue(YouTubeSessionPolicy.expired(403, "UNAUTHENTICATED"))
        assertFalse(YouTubeSessionPolicy.expired(403))
        assertFalse(YouTubeSessionPolicy.expired(400)); assertFalse(YouTubeSessionPolicy.expired(402)); assertFalse(YouTubeSessionPolicy.expired(500))
    }
    @Test fun cookieNameMustMatchExactlyAndValueMayContainEquals() {
        assertNull(YouTubeSessionPolicy.sapisid("NOT_SAPISID=x"))
        assertEquals("a=b", YouTubeSessionPolicy.sapisid("SID=z; SAPISID=a=b"))
    }
}
