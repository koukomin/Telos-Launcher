package com.telos.desktop.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopGitHubReleasesChannelTest {
    @Test
    fun buildsDefaultLatestDownloadUrl() {
        val url = DesktopGitHubReleasesChannel.latestDownloadUrl()
        assertEquals(
            "https://github.com/koukomin/Telos-Launcher/releases/latest/download/telos-desktop-update.manifest",
            url,
        )
        assertTrue(url.startsWith("https://"))
    }

    @Test
    fun resolvesManifestUrlFromMockReleasesJson() {
        val json = """
            {
              "tag_name": "desktop-9.9.9",
              "assets": [
                {
                  "name": "README.md",
                  "browser_download_url": "https://github.com/koukomin/Telos-Launcher/releases/download/desktop-9.9.9/README.md"
                },
                {
                  "name": "telos-desktop-update.manifest",
                  "browser_download_url": "https://github.com/koukomin/Telos-Launcher/releases/download/desktop-9.9.9/telos-desktop-update.manifest"
                }
              ]
            }
        """.trimIndent()
        val resolved = DesktopGitHubReleasesChannel.resolveManifestUrlFromReleasesJson(json)
        assertEquals(
            "https://github.com/koukomin/Telos-Launcher/releases/download/desktop-9.9.9/telos-desktop-update.manifest",
            resolved,
        )
    }

    @Test
    fun lookupUsesInjectableFetcherWithoutNetwork() {
        val payload = """
            {"assets":[{"name":"telos-desktop-update.manifest",
            "browser_download_url":"https://example.test/telos-desktop-update.manifest"}]}
        """.trimIndent()
        var fetchedApi: String? = null
        val url = DesktopGitHubReleasesChannel.lookupLatestManifestUrl(
            fetchJson = { api ->
                fetchedApi = api
                payload
            },
        )
        assertEquals(
            "https://api.github.com/repos/koukomin/Telos-Launcher/releases/latest",
            fetchedApi,
        )
        assertEquals("https://example.test/telos-desktop-update.manifest", url)
    }

    @Test
    fun returnsNullWhenManifestAssetMissing() {
        val json = """{"assets":[{"name":"other.bin","browser_download_url":"https://example.test/other.bin"}]}"""
        assertNull(DesktopGitHubReleasesChannel.resolveManifestUrlFromReleasesJson(json))
    }

    @Test
    fun preferredManifestUrlFallsBackToReleasesChannel() {
        val prevProp = System.getProperty("telos.desktop.update.manifest")
        try {
            System.clearProperty("telos.desktop.update.manifest")
            val url = DesktopSignedUpdate.preferredManifestUrl(null)
            assertEquals(DesktopGitHubReleasesChannel.latestDownloadUrl(), url)
        } finally {
            if (prevProp != null) {
                System.setProperty("telos.desktop.update.manifest", prevProp)
            } else {
                System.clearProperty("telos.desktop.update.manifest")
            }
        }
    }
}
