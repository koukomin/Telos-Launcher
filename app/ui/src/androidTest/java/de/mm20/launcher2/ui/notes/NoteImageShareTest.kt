package de.mm20.launcher2.ui.notes

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NoteImageShareTest {
    @Test fun greekAndRtlLongNoteExportsEveryPageWithinBitmapBudget() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = renderNoteImages(context, Note(title = "Σημείωση — ملاحظة", body = (1..130).joinToString("\n") { "Γραμμή $it — سطر $it" }))!!
        try {
            assertTrue(files.size > 1)
            files.forEach { file ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, options)
                assertEquals(1080, options.outWidth)
                assertTrue(options.outHeight in 145..1600)
            }
        } finally { files.forEach { it.delete() }; files.first().parentFile?.delete() }
    }
    @Test fun oversizedNoteFailsInsteadOfSilentlyTruncating() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNull(renderNoteImages(context, Note(body = "x".repeat(100_001))))
    }
}
