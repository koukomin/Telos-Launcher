package com.telos.desktop.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPlacesTest {
    @Test
    fun parsesGtkBookmarks() {
        val file = NetworkPlaces.parseBookmark("file:///home/user/Docs Documents")!!
        assertEquals("Documents", file.label)
        assertTrue(file.uri.startsWith("file:"))
        val smb = NetworkPlaces.parseBookmark("smb://nas/share NAS")!!
        assertEquals("NAS", smb.label)
        assertEquals("smb://nas/share", smb.uri)
    }
}
