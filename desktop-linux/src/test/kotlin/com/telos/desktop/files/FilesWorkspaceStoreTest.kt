package com.telos.desktop.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class FilesWorkspaceStoreTest {
    @Test
    fun roundTripTabs() {
        val file = Files.createTempFile("ws", ".properties").toFile()
        val state = FilesWorkspaceStore.single("/home/user").copy(
            tabs = listOf(
                FilesTabState("a", "/home/user"),
                FilesTabState("b", "/home/user/Downloads"),
            ),
            left = "b",
            right = "a",
            dual = true,
        )
        FilesWorkspaceStore.save(file, state)
        val loaded = FilesWorkspaceStore.load(file, "/tmp")
        assertEquals(listOf("/home/user", "/home/user/Downloads"), loaded.tabs.map { it.path })
        assertTrue(loaded.dual)
        assertEquals("/home/user/Downloads", loaded.tabs.first { it.id == loaded.left }.path)
    }

    @Test
    fun cloudUriAllowlist() {
        assertTrue(CloudPlacesStore.allowedUri("sftp://host/path"))
        assertTrue(CloudPlacesStore.allowedUri("smb://nas/share"))
        assertTrue(CloudPlacesStore.allowedUri("https://cloud.example"))
        assertTrue(CloudPlacesStore.allowedUri("https://cloud.example/remote.php/dav/files/a"))
        assertTrue(CloudPlacesStore.allowedUri("dropbox://"))
        assertTrue(CloudPlacesStore.allowedUri("gdrive://"))
        assertTrue(CloudPlacesStore.allowedUri("onedrive://"))
        assertFalse(CloudPlacesStore.allowedUri("javascript:alert(1)"))
        assertFalse(CloudPlacesStore.allowedUri("http://example"))
        val file = Files.createTempFile("cloud", ".properties").toFile()
        CloudPlacesStore.save(file, listOf(NetworkPlace("NAS", "smb://nas/share", null), NetworkPlace("bad", "javascript:x", null)))
        val loaded = CloudPlacesStore.load(file)
        assertEquals(listOf("smb://nas/share"), loaded.map { it.uri })
    }
}
