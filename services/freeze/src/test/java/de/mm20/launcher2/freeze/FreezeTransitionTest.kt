package de.mm20.launcher2.freeze

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FreezeTransitionTest {
    private class Backend(var enabled: Boolean = true, var suspended: Boolean = false) {
        val calls = mutableListOf<String>()
        var failSuspend = false
        var failEnable = false
        suspend fun suspendApps(packages: List<String>, value: Boolean): Set<String> {
            calls += "suspend:$value"
            if (failSuspend) return emptySet()
            suspended = value
            return packages.toSet()
        }
        suspend fun enableApps(packages: List<String>, value: Boolean): Set<String> {
            calls += "enable:$value"
            if (failEnable) return emptySet()
            enabled = value
            return packages.toSet()
        }
        suspend fun apply(frozen: Boolean, disable: Boolean = false) = transitionFreezeState(
            listOf("app"), frozen, disable, ::suspendApps, ::enableApps)
    }

    @Test fun disabledToSuspendedClearsDisableOnlyAfterSuspending() = runBlocking {
        val backend = Backend(enabled = false)
        assertEquals(setOf("app"), backend.apply(true))
        assertEquals(listOf("suspend:true", "enable:true"), backend.calls)
        assertTrue(backend.enabled); assertTrue(backend.suspended)
    }
    @Test fun suspendedToDisabledClearsSuspensionOnlyAfterDisabling() = runBlocking {
        val backend = Backend(suspended = true)
        assertEquals(setOf("app"), backend.apply(true, disable = true))
        assertEquals(listOf("enable:false", "suspend:false"), backend.calls)
        assertFalse(backend.enabled); assertFalse(backend.suspended)
    }
    @Test fun failedSuspendNeverEnablesDisabledApp() = runBlocking {
        val backend = Backend(enabled = false).apply { failSuspend = true }
        assertTrue(backend.apply(true).isEmpty())
        assertEquals(listOf("suspend:true"), backend.calls); assertFalse(backend.enabled)
    }
    @Test fun failedDisableNeverUnsuspendsApp() = runBlocking {
        val backend = Backend(suspended = true).apply { failEnable = true }
        assertTrue(backend.apply(true, disable = true).isEmpty())
        assertEquals(listOf("enable:false"), backend.calls); assertTrue(backend.suspended)
    }
    @Test fun failedEnableDoesNotReportCompletedConversion() = runBlocking {
        val backend = Backend(enabled = false).apply { failEnable = true }
        assertTrue(backend.apply(true).isEmpty())
        assertFalse(backend.enabled); assertTrue(backend.suspended)
    }
    @Test fun failedUnsuspendDoesNotReportCompletedConversion() = runBlocking {
        val backend = Backend(suspended = true).apply { failSuspend = true }
        assertTrue(backend.apply(true, disable = true).isEmpty())
        assertFalse(backend.enabled); assertTrue(backend.suspended)
    }
    @Test fun thawRequiresBothRestrictionsToBeCleared() = runBlocking {
        val backend = Backend(enabled = false, suspended = true)
        assertEquals(setOf("app"), backend.apply(false))
        assertTrue(backend.enabled); assertFalse(backend.suspended)
    }
    @Test fun thawDoesNotTreatOnlyOneSuccessfulOperationAsSuccess() = runBlocking {
        for (failSuspend in listOf(true, false)) {
            val backend = Backend(enabled = false, suspended = true).apply {
                this.failSuspend = failSuspend; failEnable = !failSuspend
            }
            assertTrue(backend.apply(false).isEmpty())
        }
    }
    @Test fun partialBatchesOnlyClearOldStateForProtectedPackages() = runBlocking {
        val passed = mutableListOf<String>()
        val result = transitionFreezeState(listOf("a", "b"), true,
            suspendPackages = { _, _ -> setOf("a", "unrequested") },
            enablePackages = { names, _ -> passed += names; names.toSet() })
        assertEquals(listOf("a"), passed); assertEquals(setOf("a"), result)
    }
    @Test fun emptyBatchNeverCallsBackend() = runBlocking {
        assertTrue(transitionFreezeState(emptyList(), true,
            suspendPackages = { _, _ -> error("called") }, enablePackages = { _, _ -> error("called") }).isEmpty())
    }
    @Test fun repeatedSuspendAndDisableAreIdempotent() = runBlocking {
        val backend = Backend()
        repeat(2) { assertEquals(setOf("app"), backend.apply(true)) }
        repeat(2) { assertEquals(setOf("app"), backend.apply(true, disable = true)) }
        assertFalse(backend.enabled); assertFalse(backend.suspended)
    }
}
