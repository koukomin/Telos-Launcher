package de.mm20.launcher2.freeze

import org.junit.Assert.*
import org.junit.Test

class ShizukuIdentityTest {
    @Test fun rootUsesReleaseApplicationId() {
        assertEquals("com.dimitris.telos.release", shizukuCallingPackage(true, "com.dimitris.telos.release"))
    }
    @Test fun adbUsesShellRegardlessOfBuildId() {
        assertEquals("com.android.shell", shizukuCallingPackage(false, "com.dimitris.telos.release"))
    }
}
