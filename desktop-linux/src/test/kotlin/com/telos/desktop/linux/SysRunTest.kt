package com.telos.desktop.linux

import org.junit.Assert.assertTrue
import org.junit.Test

class SysRunTest {
    @Test
    fun closesStdinAndCapturesStdout() {
        val r = Sys.run("sh", "-c", "echo hi", timeoutSec = 4)
        assertTrue(r.ok)
        assertTrue(r.stdout.contains("hi"))
    }
}
