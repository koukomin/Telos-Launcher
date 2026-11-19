/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.mm20.launcher2.ui.freeze

import android.content.ComponentName
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.mm20.launcher2.freeze.providers.IslandIntentContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Validates the outgoing protocol without installing Island or freezing any app. */
@RunWith(AndroidJUnit4::class)
class IslandIntentContractTest {
    private val component = ComponentName(IslandIntentContract.PACKAGE, "com.oasisfeng.island.api.ApiActivity")

    @Test
    fun freezeKeepsItsActionAndExplicitDestination() {
        val intent = IslandIntentContract.create(component, listOf("com.example.one", "com.example.two"), true)
        assertNotNull(intent)
        assertEquals(IslandIntentContract.FREEZE, intent!!.action)
        assertEquals(component, intent.component)
        assertEquals(IslandIntentContract.PACKAGE, intent.`package`)
        assertEquals("packages:com.example.one,com.example.two", intent.dataString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun unfreezeUsesItsOwnProtocolAction() {
        val intent = IslandIntentContract.create(component, listOf("com.example.one"), false)
        assertEquals(IslandIntentContract.UNFREEZE, intent!!.action)
        assertEquals(component, intent.component)
    }

    @Test
    fun foreignTargetsAndEmptyRequestsAreRejected() {
        assertNull(IslandIntentContract.create(ComponentName("untrusted.example", "untrusted.example.Activity"), listOf("com.example.one"), true))
        assertNull(IslandIntentContract.create(component, emptyList(), true))
    }
}
