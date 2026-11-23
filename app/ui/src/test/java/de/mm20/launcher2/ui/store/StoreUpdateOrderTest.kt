package de.mm20.launcher2.ui.store

import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.StoreItem
import org.junit.Assert.assertEquals
import org.junit.Test

class StoreUpdateOrderTest {
    @Test
    fun bulkUpdateRunsLauncherAfterOtherApps() {
        val launcher = StoreItem(
            id = "telos-self",
            packageName = "de.mm20.launcher2.release",
            displayName = "Telos",
            source = AppSource.GitHub("koukomin", "Telos-Launcher"),
        )
        val other = StoreItem(
            id = "other",
            packageName = "org.example.app",
            displayName = "Other app",
            source = AppSource.DirectApk("https://example.org/app.apk"),
        )

        assertEquals(
            listOf("other", "telos-self"),
            listOf(launcher, other).sortedWith(storeBulkUpdateOrder(launcher.packageName)).map { it.id },
        )
    }

    @Test
    fun pendingUpdateTapsAccumulateWithoutDroppingTheFirst() {
        val first = StoreItem(
            id = "one",
            packageName = "org.example.one",
            displayName = "One",
            source = AppSource.DirectApk("https://example.org/one.apk"),
        )
        val second = StoreItem(
            id = "two",
            packageName = "org.example.two",
            displayName = "Two",
            source = AppSource.DirectApk("https://example.org/two.apk"),
        )
        assertEquals(
            listOf("one", "two"),
            mergePendingStoreUpdates(listOf(first), listOf(second)).map { it.id },
        )
        assertEquals(
            listOf("one"),
            mergePendingStoreUpdates(listOf(first), listOf(first)).map { it.id },
        )
    }
}
