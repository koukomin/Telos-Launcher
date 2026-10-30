package com.telos.desktop.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudOAuthTest {
    @Test
    fun detectAndPkce() {
        assertEquals(CloudKind.Dropbox, CloudOAuth.detect("dropbox://"))
        assertEquals(CloudKind.GoogleDrive, CloudOAuth.detect("gdrive://"))
        assertEquals(CloudKind.OneDrive, CloudOAuth.detect("onedrive"))
        val v = CloudOAuth.verifier()
        assertTrue(v.length >= 32)
        val c = CloudOAuth.challenge(v)
        assertNotEquals(v, c)
        val url = CloudOAuth.authorizeUrl(CloudKind.Dropbox, "appkey", v, "st")
        assertTrue(url.startsWith("https://www.dropbox.com/oauth2/authorize?"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("redirect_uri="))
        assertTrue(CloudOAuth.provider(CloudKind.GoogleDrive).needsSecret)
        assertFalse(CloudOAuth.provider(CloudKind.Dropbox).needsSecret)
    }

    @Test
    fun parseListJson() {
        val db = """{"entries":[{".tag":"folder","name":"Photos","path_display":"/Photos"},{".tag":"file","name":"a.txt","path_display":"/a.txt","size":12}]}"""
        val rows = CloudApi.entries(db)
        assertEquals(listOf("Photos", "a.txt"), rows.map { it.name })
        assertTrue(rows[0].directory)
        assertFalse(rows[1].directory)
        val drive = """{"files":[{"id":"1","name":"Docs","mimeType":"application/vnd.google-apps.folder"},{"id":"2","name":"x.bin","mimeType":"text/plain","size":"9"}]}"""
        val d = CloudApi.driveEntries(drive)
        assertTrue(d[0].directory)
        assertEquals("x.bin", d[1].name)
        val graph = """{"value":[{"id":"i1","name":"Work","folder":true},{"id":"i2","name":"f.pdf","size":4}]}"""
        val g = CloudApi.graphEntries(graph)
        assertTrue(g[0].directory)
        assertEquals("f.pdf", g[1].name)
    }
}
