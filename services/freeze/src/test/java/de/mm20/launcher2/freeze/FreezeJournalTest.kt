package de.mm20.launcher2.freeze

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FreezeJournalTest {
    @Test fun persistsOriginalBeforeChangingOs() = runBlocking {
        val events = mutableListOf<String>()
        assertTrue(saveThenThaw("original-disabled", { events += "save:$it" }, { events += "thaw"; true }))
        assertEquals(listOf("save:original-disabled", "thaw"), events)
    }
    @Test fun persistenceFailureNeverThaws() = runBlocking {
        var touched = false
        try { saveThenThaw("original", { error("disk full") }, { touched = true; true }); fail() }
        catch (_: IllegalStateException) { }
        assertFalse(touched)
    }
    @Test fun interruptionLeavesRecoveryRecordAvailableToNextInstance() = runBlocking {
        val disk = mutableMapOf<String, String>()
        try { saveThenThaw("disabled-user", { disk["app"] = it }, { error("process interrupted") }); fail() }
        catch (_: IllegalStateException) { }
        val reopened = disk.toMap()
        assertEquals("disabled-user", reopened["app"])
        assertTrue(restoreThenForget(reopened.getValue("app"), { it == "disabled-user" }, { disk.remove("app") }))
        assertTrue(disk.isEmpty())
    }
    @Test fun failedOrThrowingRestorationNeverDiscardsHistory() = runBlocking {
        var forgotten = false
        assertFalse(restoreThenForget("original", { false }, { forgotten = true }))
        try { restoreThenForget("original", { error("permission lost") }, { forgotten = true }); fail() }
        catch (_: IllegalStateException) { }
        assertFalse(forgotten)
    }
    @Test fun staleInstallerCallbackCannotOwnNewLease() {
        val first = "00000000-1111-2222-3333-444444444444"
        val second = "00000000-1111-2222-3333-555555555555"
        assertTrue(ownsFrozenUpdate(first, first))
        assertFalse(ownsFrozenUpdate(second, first))
        assertFalse(ownsFrozenUpdate("", ""))
        assertFalse(ownsFrozenUpdate("corrupt", "corrupt"))
    }
    @Test fun launcherAvailabilityCanArriveAfterTheOldRowDisappears() = runBlocking {
        var lookups = 0; var waits = 0; val launches = mutableListOf<String>()
        assertTrue(launchWhenReady(find = { if (++lookups < 3) null else "new-launcher-component" },
            launch = { launches += it; true }, wait = { waits++ }))
        assertEquals(3, lookups); assertEquals(2, waits)
        assertEquals(listOf("new-launcher-component"), launches)
    }
    @Test fun rejectedLaunchIsRetriedWithoutASecondTap() = runBlocking {
        var launches = 0
        assertTrue(launchWhenReady(find = { "ready" }, launch = { ++launches == 2 }, wait = {}))
        assertEquals(2, launches)
    }
    @Test fun missingActivityHasBoundedWaits() = runBlocking {
        var reads = 0; var waits = 0
        assertFalse(launchWhenReady<String>(attempts = 4, find = { reads++; null }, launch = { error("called") }, wait = { waits++ }))
        assertEquals(4, reads); assertEquals(3, waits)
    }
}
