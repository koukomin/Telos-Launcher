/*
 * Copyright (c) 2026 Telos contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vitorpamplona.amethyst

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vitorpamplona.amethyst.service.notifications.NotificationReplyReceiver
import com.vitorpamplona.amethyst.service.notifications.notificationReplyIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationReplyIntentTest {
    @Test
    fun replyPayloadCannotRedirectTheBroadcastOrGrantUris() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Intent("fixture.reply").apply {
            component = ComponentName("untrusted.example", "untrusted.example.Receiver")
            data = Uri.parse("content://untrusted.example/private")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra("fixture.reply.text", "Retained reply")
        }
        val intent = notificationReplyIntent(context, source)
        assertEquals(ComponentName(context, NotificationReplyReceiver::class.java), intent.component)
        assertEquals(source.action, intent.action)
        assertEquals("Retained reply", intent.getStringExtra("fixture.reply.text"))
        assertEquals(0, intent.flags)
        assertNull(intent.selector)
        assertNull(intent.data)
    }
}
