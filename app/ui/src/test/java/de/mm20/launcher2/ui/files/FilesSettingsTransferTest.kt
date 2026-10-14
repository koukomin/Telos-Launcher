package de.mm20.launcher2.ui.files

import android.content.SharedPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.lang.reflect.Proxy

class FilesSettingsTransferTest {
    private fun valid() = JSONObject("""{"version":1,"viewMode":0,"hidden":false,"sortKey":"Name","sortAsc":true,"foldersFirst":true,"bookmarks":[],"folders":{},"workspace":{"paths":[null,"/storage/emulated/0"],"left":0,"right":1,"dual":true,"activeRight":true}}""")
    @Test fun importsPortableWorkspace() {
        val settings = FilesSettingsTransfer.parse(valid().toString())
        assertEquals(listOf(null, "/storage/emulated/0"), settings.workspace.paths)
        assertTrue(settings.workspace.dual)
        assertTrue(settings.workspace.activeRight)
    }
    @Test fun invalidImportNeverTouchesPreferences() {
        var edits = 0
        val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, _ ->
            if (method.name == "edit") edits++
            error("Preferences must not be touched")
        } as SharedPreferences
        val invalid = valid().put("viewMode", 9).toString()
        assertTrue(runCatching { FilesSettingsTransfer.apply(prefs, FilesSettingsTransfer.parse(invalid)) }.isFailure)
        assertEquals(0, edits)
    }
    @Test fun rejectsInvalidPathAndSameDualPane() {
        val invalidPath = valid().put("bookmarks", org.json.JSONArray().put("relative.txt"))
        assertTrue(runCatching { FilesSettingsTransfer.parse(invalidPath.toString()) }.isFailure)
        val samePane = valid().also { it.getJSONObject("workspace").put("right", 0) }
        assertTrue(runCatching { FilesSettingsTransfer.parse(samePane.toString()) }.isFailure)
    }
    @Test fun enforcesByteLimitAndUtf8Validity() {
        assertTrue(runCatching { FilesSettingsTransfer.read(ByteArrayInputStream(ByteArray(524289) { 65 })) }.isFailure)
        assertTrue(runCatching { FilesSettingsTransfer.read(ByteArrayInputStream(byteArrayOf(0xc3.toByte()))) }.isFailure)
        assertEquals("Ελληνικά", FilesSettingsTransfer.read(ByteArrayInputStream("Ελληνικά".toByteArray())))
    }
    @Test fun sanitizesOptionalToolbarPreferences() {
        val input = valid().put("toolbarOrder", "Hidden,Invalid,View,Hidden")
            .put("toolbarVisible", "Hidden,Sort,Refresh,Invalid")
        val settings = FilesSettingsTransfer.parse(input.toString())
        assertEquals(listOf(FilesToolbarAction.Hidden, FilesToolbarAction.Sort), settings.toolbar.actions)
    }
    @Test fun ignoresUnknownSecretFields() {
        val input = valid().put("connections", JSONObject().put("password", "not imported"))
        assertEquals(emptySet<String>(), FilesSettingsTransfer.parse(input.toString()).bookmarks)
    }
}
