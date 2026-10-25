package de.mm20.launcher2.freeze

import org.junit.Assert.*
import org.junit.Test

class ComponentEditPolicyTest {
    @Test fun rejectsShellTokensBeforePrivilegedExecution() {
        for (name in listOf("a;b", "a'", "a\nb", "a/b", "a b", "a`id`", "a$(id)")) {
            assertFalse(validComponentEdit("com.example", name, 2, emptySet()))
            assertFalse(validComponentEdit(name, "com.example.Service", 2, emptySet()))
        }
    }
    @Test fun criticalPackagesCannotBeEditedAndOnlyRestorableStatesAreAllowed() {
        assertFalse(validComponentEdit("android", "android.Service", 2, setOf("android")))
        for (state in listOf(-1, 3, 4, Int.MAX_VALUE))
            assertFalse(validComponentEdit("com.example", "com.example.Service", state, emptySet()))
        for (state in 0..2)
            assertTrue(validComponentEdit("com.example", "com.example.Outer\$Nested", state, emptySet()))
    }
}
