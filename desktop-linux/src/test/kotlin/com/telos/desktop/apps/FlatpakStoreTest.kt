package com.telos.desktop.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class FlatpakStoreTest {
    @Test
    fun parsesTabRows() {
        val rows = FlatpakStore.parseRows(
            """
            Application	Name	Origin
            org.mozilla.firefox	Firefox	flathub
            not-an-id
            """.trimIndent(),
            installed = true,
        )
        assertEquals(1, rows.size)
        assertEquals("org.mozilla.firefox", rows[0].id)
        assertEquals("Firefox", rows[0].name)
        assertEquals("flathub", rows[0].origin)
    }
}
