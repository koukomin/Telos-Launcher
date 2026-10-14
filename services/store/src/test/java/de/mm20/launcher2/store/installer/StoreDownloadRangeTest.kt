package de.mm20.launcher2.store.installer

import org.junit.Assert.*
import org.junit.Test

class StoreDownloadRangeTest {
    @Test fun resumesAtTheRequestedByteOnly() {
        assertEquals(4096L, StoreDownloadRange.appendOffset(206, "bytes 4096-8191/8192", 4096))
        assertNull(StoreDownloadRange.appendOffset(206, "bytes 0-8191/8192", 4096))
        assertNull(StoreDownloadRange.appendOffset(206, null, 4096))
    }
    @Test fun aServerThatIgnoresRangeRestartsRatherThanAppending() {
        assertEquals(0L, StoreDownloadRange.appendOffset(200, null, 4096))
    }
    @Test fun rejectsBrokenAndOutOfRangeResponses() {
        for (range in listOf("bytes 4096-4000/8192", "bytes 4096-8192/8192", "nonsense", "bytes 999999999999999999999-999999999999999999999/*"))
            assertNull(StoreDownloadRange.appendOffset(206, range, 4096))
        assertNull(StoreDownloadRange.appendOffset(416, "bytes */8192", 4096))
        assertNull(StoreDownloadRange.appendOffset(503, null, 4096))
    }
}
