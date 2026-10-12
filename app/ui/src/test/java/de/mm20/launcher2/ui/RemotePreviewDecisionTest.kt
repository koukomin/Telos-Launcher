package de.mm20.launcher2.ui

import de.mm20.launcher2.ui.files.remote.shouldPreview
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePreviewDecisionTest {
    private val mb = 1024L * 1024

    @Test fun offNeverPreviews() = assertFalse(shouldPreview(false, mb, 2 * mb, false, true, "jpg"))
    @Test fun withinLimit() = assertTrue(shouldPreview(true, mb, 2 * mb, false, true, "jpg"))
    @Test fun exactLimit() = assertTrue(shouldPreview(true, 2 * mb, 2 * mb, false, true, "PDF"))
    @Test fun tooBig() = assertFalse(shouldPreview(true, 2 * mb + 1, 2 * mb, false, true, "jpg"))
    @Test fun unknownSize() = assertFalse(shouldPreview(true, -1, 2 * mb, false, true, "jpg"))
    @Test fun meteredWifiOnly() = assertFalse(shouldPreview(true, mb, 2 * mb, true, true, "jpg"))
    @Test fun meteredAllowed() = assertTrue(shouldPreview(true, mb, 2 * mb, true, false, "jpg"))
    @Test fun otherType() = assertFalse(shouldPreview(true, mb, 2 * mb, false, true, "zip"))
}
