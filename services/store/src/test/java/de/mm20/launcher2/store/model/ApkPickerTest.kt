package de.mm20.launcher2.store.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Only the cases that do not depend on the CPU of the device the test runs on. */
class ApkPickerTest {

    private fun asset(name: String) = ReleaseAsset(name, "https://example.org/$name")

    @Test
    fun onlyApkFilesAreCandidates() {
        val picked = ApkPicker.pick(listOf(asset("notes.txt"), asset("app.apk"), asset("app.apk.sig")), null)
        assertEquals("app.apk", picked?.name)
    }

    @Test
    fun noApkMeansNothingToInstall() {
        assertNull(ApkPicker.pick(listOf(asset("source.zip")), null))
        assertNull(ApkPicker.pick(emptyList(), null))
    }

    @Test
    fun theFilterNarrowsTheCandidates() {
        val assets = listOf(asset("app-play.apk"), asset("app-foss.apk"))
        assertEquals("app-foss.apk", ApkPicker.pick(assets, "foss")?.name)
    }

    @Test
    fun aBrokenFilterIsIgnored() {
        assertEquals("app.apk", ApkPicker.pick(listOf(asset("app.apk")), "(")?.name)
    }
}
