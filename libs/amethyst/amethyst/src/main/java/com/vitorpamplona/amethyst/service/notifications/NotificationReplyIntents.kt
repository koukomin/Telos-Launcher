/*
 * Copyright (c) 2026 Telos contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vitorpamplona.amethyst.service.notifications

import android.content.Context
import android.content.Intent

/** Copy reply payloads, never a caller's selector, target, URI grants or routing flags. */
internal fun notificationReplyIntent(context: Context, source: Intent): Intent =
    Intent(context, NotificationReplyReceiver::class.java).apply {
        action = source.action
        source.extras?.let { putExtras(it) }
    }
