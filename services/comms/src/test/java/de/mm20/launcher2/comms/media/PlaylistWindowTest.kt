package de.mm20.launcher2.comms.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistWindowTest {
    @Test fun smallListIsKept() {
        val (l, i) = (0 until 10).toList().windowAround(4)
        assertEquals(10, l.size)
        assertEquals(4, i)
    }

    @Test fun emptyList() {
        val (l, i) = emptyList<Int>().windowAround(3)
        assertTrue(l.isEmpty())
        assertEquals(0, i)
    }

    @Test fun bigListKeepsTheChosenItem() {
        val all = (0 until 10_000).toList()
        for (index in listOf(0, 1, 199, 200, 5000, 9800, 9999)) {
            val (l, i) = all.windowAround(index, 400)
            assertEquals(400, l.size)
            assertEquals(index, l[i])
        }
    }

    @Test fun outOfRangeIndexIsClamped() {
        val all = (0 until 1000).toList()
        val (l, i) = all.windowAround(5000, 100)
        assertEquals(999, l[i])
        val (l2, i2) = all.windowAround(-4, 100)
        assertEquals(0, l2[i2])
    }
}
