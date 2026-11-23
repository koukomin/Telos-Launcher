package de.mm20.launcher2.freeze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeProfileCopyTest {
    @Test
    fun onlyPackagesInstalledOnTheDestinationAreCopied() {
        val applied = FreezeProfileCopy.packagesToApply(
            sourceFrozen = setOf("com.a", "com.b", "com.c"),
            destinationInstalled = setOf("com.b", "com.d"),
        )
        assertEquals(setOf("com.b"), applied)
    }

    @Test
    fun emptySourceOrDestinationYieldsNothing() {
        assertTrue(FreezeProfileCopy.packagesToApply(emptySet(), setOf("com.a")).isEmpty())
        assertTrue(FreezeProfileCopy.packagesToApply(setOf("com.a"), emptySet()).isEmpty())
    }
}
