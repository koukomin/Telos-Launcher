package com.telos.desktop.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavClientTest {
    @Test
    fun normalizesNextcloudAndParsesPropfind() {
        assertEquals(
            "https://cloud.example/remote.php/dav/files/anna",
            WebDavClient.normalize("https://cloud.example", "anna"),
        )
        assertEquals(
            "https://cloud.example/remote.php/dav/files/anna",
            WebDavClient.normalize("davs://cloud.example", "anna"),
        )
        val xml = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/anna/</d:href>
                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/anna/Notes/</d:href>
                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/anna/readme.txt</d:href>
                <d:propstat><d:prop><d:getcontentlength>12</d:getcontentlength><d:resourcetype/></d:prop></d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
        val entries = WebDavClient.parsePropfind(xml, "https://cloud.example/remote.php/dav/files/anna")
        assertEquals(setOf("Notes", "readme.txt"), entries.map { it.name }.toSet())
        assertTrue(entries.first { it.name == "Notes" }.directory)
        assertFalse(entries.first { it.name == "readme.txt" }.directory)
        assertEquals(12L, entries.first { it.name == "readme.txt" }.size)
    }
}
