package com.telos.desktop.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class DesktopUpdateRestartHelperTest {
    @Test
    fun writesRestartMarkerAndAppliesOnNextStart() {
        val root = createTempDirectory("telos-update-restart").toFile()
        val prevTmp = System.getProperty("java.io.tmpdir")
        try {
            System.setProperty("java.io.tmpdir", root.absolutePath)
            val staged = File(root, "staged-telos.jar").apply {
                writeBytes("new-uber-jar".toByteArray())
            }
            val target = File(root, "running-telos.jar").apply {
                writeBytes("old-uber-jar".toByteArray())
            }
            val marker = DesktopUpdateRestartHelper.writeUpdateMarker(
                stagedJar = staged,
                targetJar = target,
                version = "1.2.3",
                dir = root,
            )
            assertTrue(marker.isFile)
            assertTrue(marker.readText().contains("staged="))
            assertTrue(marker.readText().contains("target="))

            val pending = DesktopUpdateRestartHelper.readMarker(root)!!
            assertEquals(staged.absolutePath, pending.stagedJar.absolutePath)
            assertEquals("1.2.3", pending.version)

            assertTrue(DesktopUpdateRestartHelper.applyPendingMarkerIfPresent(root))
            assertEquals("new-uber-jar", target.readText())
            assertFalse(DesktopUpdateRestartHelper.markerFile(root).exists())
        } finally {
            if (prevTmp != null) System.setProperty("java.io.tmpdir", prevTmp) else System.clearProperty("java.io.tmpdir")
            root.deleteRecursively()
        }
    }

    @Test
    fun prepareRestartWritesScriptWithoutExitingWhenDisabled() {
        val root = createTempDirectory("telos-update-restart2").toFile()
        val prevTmp = System.getProperty("java.io.tmpdir")
        try {
            System.setProperty("java.io.tmpdir", root.absolutePath)
            val staged = File(root, "staged.jar").apply { writeText("payload") }
            val target = File(root, "target.jar").apply { writeText("old") }
            var spawned: File? = null
            var exited = false
            val plan = DesktopUpdateRestartHelper.prepareRestartAfterUpdate(
                stagedJar = staged,
                targetJar = target,
                version = "9.0",
                dir = root,
                exitAfterSpawn = false,
                spawnHelper = { spawned = it },
                exitFn = { exited = true },
            ).getOrThrow()
            assertFalse(exited)
            assertTrue(plan.marker.isFile)
            assertTrue(plan.script!!.isFile)
            assertEquals(spawned!!.absolutePath, plan.script!!.absolutePath)
            assertTrue(plan.script!!.readText().contains("telos-desktop-update-pending") ||
                plan.script!!.readText().contains("cp -f"))
        } finally {
            if (prevTmp != null) System.setProperty("java.io.tmpdir", prevTmp) else System.clearProperty("java.io.tmpdir")
            root.deleteRecursively()
        }
    }
}
