package de.mm20.launcher2.data.store.icons

import org.junit.Assert.*
import org.junit.Test

class StoreIconUrlsTest {
    private val base = "https://raw.githubusercontent.com/owner/app/HEAD/README.md"
    @Test fun resolvesMarkdownAppIconWithoutBadges() {
        val text = "![Build](https://img.shields.io/build/icon.svg)\n![App icon](fastlane/icon.png)"
        assertEquals(listOf("https://raw.githubusercontent.com/owner/app/HEAD/fastlane/icon.png"), publishedIcons(text, base))
    }
    @Test fun supportsHtmlAttributeOrderAndSingleQuotes() {
        assertEquals(listOf("https://raw.githubusercontent.com/owner/app/HEAD/art/logo.svg"),
            publishedIcons("<img width='80' src='art/logo.svg' alt='Application logo'>", base))
    }
    @Test fun convertsGitHubBlobLinksToImageBytes() {
        assertEquals("https://raw.githubusercontent.com/owner/app/main/icon.png",
            imageUrl(base, "https://github.com/owner/app/blob/main/icon.png"))
    }
    @Test fun resolvesHashedRepositoryIconsAndIgnoresSiteFavicons() {
        assertEquals(listOf("https://f-droid.org/repo/icons-640/app.hash.png"), publishedIcons(
            "<link rel='icon' href='/favicon.png'><img class='package-icon' src='/repo/icons-640/app.hash.png'>",
            "https://f-droid.org/en/packages/app/"))
    }
    @Test fun excludesScreenshotsAvatarsAndUnsafeSchemes() {
        assertTrue(publishedIcons("<img alt='logo avatar' src='avatar.png'><img alt='icon screenshot' src='screenshot.png'><img alt='logo' src='data:image/png;base64,AAAA'>", base).isEmpty())
        assertNull(imageUrl(base, "http://example.org/icon.png"))
        assertNull(imageUrl(base, "https://user:password@example.org/icon.png"))
    }
}
