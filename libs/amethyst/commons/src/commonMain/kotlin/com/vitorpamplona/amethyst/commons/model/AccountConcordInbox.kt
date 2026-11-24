/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.crypto.aes.AES_GCM_NAME
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordDisappearing
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordTimerNoticeEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Concord ingest, expiry, and channel-index work that used to live on [Account].
 * Callers on Account stay the same one-line delegates.
 */
class AccountConcordInbox(
    private val account: Account,
) {
    /** The banlist each community's loaded notes were last swept against. */
    private val concordBannedSwept = HashMap<String, Set<HexKey>>()

    fun consumeConcordRumorGated(
        communityId: String,
        channelIdHex: String,
        rumor: Event,
        seenOnRelays: Set<NormalizedRelayUrl>,
    ) {
        val authority =
            account.concordSessions
                .sessionFor(communityId)
                ?.state
                ?.value
                ?.authority
        if (authority?.isBanned(rumor.pubKey) == true) return
        if (ConcordDisappearing.isExpired(rumor)) return
        registerConcordEncryptedImages(rumor)
        account.cache.consumeConcordRumor(communityId, channelIdHex, rumor, seenOnRelays)
    }

    fun sweepExpiredConcordMessages(now: Long = TimeUtils.now()) {
        val expired = account.concordSessions.sweepExpired(now)
        for (rumors in expired.values) {
            for (gone in rumors) {
                gone.attachmentUrls.forEach { account.encryptionKeyCache.remove(it) }
                account.cache.getNoteIfExists(gone.rumorId)?.let { note ->
                    note.detachFromChildren()
                    account.cache.pruner.unlinkAndRemove(note)
                }
                account.cache.getNoteIfExists(gone.wrapId)?.let { account.cache.pruner.unlinkAndRemove(it) }
            }
        }
    }

    fun isConcordExpired(note: Note): Boolean {
        val event = note.event ?: return false
        if (note.inGatherers?.any { it is ConcordChannel } != true) return false
        return ConcordDisappearing.isExpired(event)
    }

    fun isUnbelievedConcordTimerNotice(note: Note): Boolean {
        val event = note.event as? ConcordTimerNoticeEvent ?: return false
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return true
        val authority =
            account.concordSessions
                .sessionFor(channel.channelId.communityId)
                ?.state
                ?.value
                ?.authority ?: return true
        return !ConcordDisappearing.isBelievedNotice(event, authority)
    }

    fun registerConcordEncryptedImages(rumor: Event) {
        val images = ChannelChat.encryptedImagesOf(rumor)
        if (images.isEmpty()) return
        images.forEach { img ->
            if (img.algo == AES_GCM_NAME) {
                account.encryptionKeyCache.add(img.url, AESGCM(img.key, img.nonce), img.mimeType)
            }
        }
    }

    fun refreshConcordChannelIndex() {
        val myPubKey = account.signer.pubKey
        val relaysByCommunity =
            account.concordChannelList.liveCommunities.value.associate { entry ->
                entry.id to entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
            }
        for (session in account.concordSessions.sessions()) {
            val state = session.state.value ?: continue
            val communityId = session.entry.id
            val relays = relaysByCommunity[communityId] ?: emptySet()
            for (channelIdHex in state.channels.keys) {
                val channel = account.cache.getOrCreateConcordChannel(ConcordChannelId(communityId, channelIdHex))
                val keyHeld = ConcordActions.canAccessChannel(session.entry, state, channelIdHex)
                if (channel.updateFrom(state, relays, myPubKey, keyHeld)) channel.updateChannelInfo()
            }
            val banned = state.authority.bannedMembers()
            if (banned.isNotEmpty() && concordBannedSwept[communityId] != banned) {
                for (channelIdHex in state.channels.keys) {
                    val channel = account.cache.getOrCreateConcordChannel(ConcordChannelId(communityId, channelIdHex))
                    channel.notes
                        .filter { _, note -> note.event?.pubKey?.let { state.authority.isBanned(it) } == true }
                        .forEach { channel.removeNote(it) }
                }
            }
            concordBannedSwept[communityId] = banned
        }
    }

    fun isConcordBanned(note: Note): Boolean {
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return false
        val author = note.author?.pubkeyHex ?: note.event?.pubKey ?: return false
        val authority =
            account.concordSessions
                .sessionFor(channel.channelId.communityId)
                ?.state
                ?.value
                ?.authority ?: return false
        return authority.isBanned(author)
    }
}
