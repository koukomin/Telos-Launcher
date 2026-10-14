package de.mm20.launcher2.helper

import org.junit.Assert.*
import org.junit.Test

class BatchNamesTest {
    @Test fun preservesExtensionsAndPadsSequenceNumbers() {
        val entries = listOf(BatchNames.Input("photo.jpg"), BatchNames.Input("report.pdf"))
        assertEquals(listOf("photo_007.jpg", "report_008.pdf"), BatchNames.plan(entries, "{name}_{n:3}{ext}", 7))
    }
    @Test fun directoriesAndHiddenNamesAreNotSplitIntoExtensions() {
        val entries = listOf(BatchNames.Input("folder.v1", true), BatchNames.Input(".nomedia"))
        assertEquals(listOf("folder.v1_1", ".nomedia_2"), BatchNames.plan(entries, "{name}_{n}{ext}"))
    }
    @Test fun literalAndRegexReplacementAreExplicit() {
        val entries = listOf(BatchNames.Input("IMG_2026.jpg"))
        assertEquals(listOf("PHOTO_2026.jpg"), BatchNames.plan(entries, "{name}{ext}", find = "IMG", replacement = "PHOTO"))
        assertEquals(listOf("IMG_year.jpg"), BatchNames.plan(entries, "{name}{ext}", find = "\\d+", replacement = "year", regex = true))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsPathsThatEscapeTheFolder() {
        BatchNames.plan(listOf(BatchNames.Input("photo.jpg")), "../{name}{ext}")
    }
}
