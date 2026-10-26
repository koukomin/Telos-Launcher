package de.mm20.launcher2.ui.comms

import org.junit.Assert.*
import org.junit.Test

class CallTouchPolicyTest {
    @Test fun terminationGestureIsNotSwallowedByStalePocketProximity() {
        assertFalse(blockPocketTouch(true, true, true, true))
        assertTrue(blockPocketTouch(true, true, true, false))
        assertFalse(blockPocketTouch(true, true, false, false))
    }
}
