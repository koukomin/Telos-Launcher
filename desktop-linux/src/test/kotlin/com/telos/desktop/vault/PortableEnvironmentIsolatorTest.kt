package com.telos.desktop.vault

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PortableEnvironmentIsolatorTest {
    private lateinit var root: File
    private val oldUserHome: String = System.getProperty("user.home")
    private val oldTmp: String = System.getProperty("java.io.tmpdir")

    @Before
    fun setUp() {
        root = File.createTempFile("telos-portable-test", "").apply {
            delete()
            mkdirs()
        }
        PortableEnvironmentIsolator.clear()
    }

    @After
    fun tearDown() {
        PortableEnvironmentIsolator.clear()
        System.setProperty("user.home", oldUserHome)
        System.setProperty("java.io.tmpdir", oldTmp)
        root.deleteRecursively()
    }

    @Test
    fun applyRedirectsHomeTmpAndXdg() {
        PortableEnvironmentIsolator.apply(root, fullSessionHome = false)
        val home = PortableEnvironmentIsolator.vaultHome!!
        assertTrue(home.absolutePath.contains("home"))
        assertEquals(home.absolutePath, System.getProperty("user.home"))
        val tmp = PortableEnvironmentIsolator.tmpDir!!
        assertEquals(tmp.absolutePath, System.getProperty("java.io.tmpdir"))
        val pb = ProcessBuilder("true")
        PortableEnvironmentIsolator.inject(pb)
        val env = pb.environment()
        assertEquals(home.absolutePath, env["HOME"])
        assertEquals(tmp.absolutePath, env["TMPDIR"])
        assertEquals(tmp.absolutePath, env["TEMP"])
        assertEquals(tmp.absolutePath, env["TMP"])
        assertTrue(env["XDG_RUNTIME_DIR"]!!.contains("xdg-runtime"))
        assertTrue(env["XDG_STATE_HOME"]!!.contains(".local${File.separator}state"))
        assertTrue(env["XDG_CONFIG_HOME"]!!.endsWith(".config"))
    }
}
