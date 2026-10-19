package de.mm20.launcher2.ui.files

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class StorageExclusionsTest {
    @Test fun folderNamesExcludeDescendantsOnly() {
        val rules = duplicateExclusions("Android, .thumbnails")
        assertTrue(isExcluded(File("/storage/Android/data/app/cache.bin"), "/storage", rules))
        assertFalse(isExcluded(File("/storage/MyAndroid/cache.bin"), "/storage", rules))
        assertFalse(isExcluded(File("/storage/Android"), "/storage", rules))
    }
    @Test fun relativePathsDoNotExcludeSimilarlyNamedFolders() {
        val rules = duplicateExclusions("DCIM/Camera")
        assertTrue(isExcluded(File("/storage/DCIM/Camera/image.jpg"), "/storage/", rules))
        assertFalse(isExcluded(File("/storage/DCIM/CameraBackup/image.jpg"), "/storage", rules))
        assertFalse(isExcluded(File("/storage/Other/DCIM/Camera/image.jpg"), "/storage", rules))
    }
    @Test fun inputIsBoundedAndAcceptsNewlines() {
        assertEquals(listOf("Android", "DCIM/Camera"), duplicateExclusions(" /Android/ ,\n /DCIM/Camera/ "))
        assertEquals(64, duplicateExclusions((1..100).joinToString(",") { "folder$it" }).size)
    }
}
