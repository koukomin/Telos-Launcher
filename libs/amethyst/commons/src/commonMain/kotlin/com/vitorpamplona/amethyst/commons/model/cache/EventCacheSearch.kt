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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.model.observables.CreatedAtIdHexComparator
import com.vitorpamplona.amethyst.commons.model.observables.EventListMatchingFilter
import com.vitorpamplona.amethyst.commons.model.observables.NewEventMatchingFilter
import com.vitorpamplona.amethyst.commons.model.observables.NoteListMatchingFilter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.isAddressable
import com.vitorpamplona.quartz.nip01Core.core.isRegular
import com.vitorpamplona.quartz.nip01Core.core.isReplaceable
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.channels.Channel as CoroutineChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

fun EventCache.findNotesMatching(
    filters: List<Filter>,
    hidden: LiveHiddenUsers,
): List<Note> = search.findNotesMatching(filters, hidden)

fun EventCache.findNotesStartingWith(
    text: String,
    hidden: LiveHiddenUsers,
): List<Note> = search.findNotesStartingWith(text, hidden)

fun EventCache.findUsersStartingWith(
    prefix: String,
    limit: Int,
): List<User> = search.findUsersStartingWith(prefix, null, limit)

fun EventCache.findPublicChatChannelsStartingWith(text: String): List<PublicChatChannel> =
    search.findPublicChatChannelsStartingWith(text)

fun EventCache.findEphemeralChatChannelsStartingWith(text: String): List<EphemeralChatChannel> =
    search.findEphemeralChatChannelsStartingWith(text)

fun EventCache.findLiveActivityChannelsStartingWith(text: String): List<LiveActivitiesChannel> =
    search.findLiveActivityChannelsStartingWith(text)

fun Filter.match(note: Note): Boolean {
    val event = note.event
    return if (event != null) {
        match(event)
    } else {
        false
    }
}

fun EventCache.filter(filter: Filter): List<Note> = filter(filter) { true }

/**
 * Every note matching [filter]'s NIP-01 fields that also satisfies [predicate].
 *
 * [predicate] is where anything the wire type cannot express belongs — a NIP-50 `search` the
 * matcher does not read, and viewer policy like the mute list, which a relay has no knowledge
 * of and a `Filter` therefore has no field for. Keeping it a separate parameter is what lets
 * search reuse this path instead of hand-rolling its own scan.
 */
fun EventCache.filter(
    filter: Filter,
    predicate: (Note) -> Boolean,
): List<Note> {
    val byKinds = filter.kinds?.filter { it.isAddressable() || it.isReplaceable() }

    val addressableMatches =
        if (!byKinds.isNullOrEmpty()) {
            val byAuthors = filter.authors
            if (!byAuthors.isNullOrEmpty()) {
                // optimized
                byKinds.flatMap { kind ->
                    byAuthors.flatMap { pubkey ->
                        addressables.filter(kind, pubkey) { _, note ->
                            filter.match(note) && predicate(note)
                        }
                    }
                }
            } else {
                // optimized
                byKinds.flatMap { kind ->
                    addressables.filter(kind) { _, note ->
                        filter.match(note) && predicate(note)
                    }
                }
            }
        } else {
            addressables.filter { _, note ->
                filter.match(note) && predicate(note)
            }
        }

    // Regular events live in [notes], and only they match below. A filter that names no regular
    // kind (a repository announcement, a list, a profile) can match none of them, so it skips the
    // walk over every note in memory, which grows with the session: each git card in a feed
    // opened several such lookups on the main thread as it scrolled into view.
    val kinds = filter.kinds
    val noteMatches =
        if (kinds != null && kinds.none { it.isRegular() }) {
            emptyList()
        } else {
            filterRegularNotes(filter, predicate)
        }

    // toSet() before sorting: a filter that repeats a kind scans that kind twice, and the
    // SortedSet this used to return collapsed the repeats. Note declares no equals(), so a
    // plain Set is the same reference-identity de-duplication that comparator gave.
    //
    // The order is the comparator's, newest first, and it is load-bearing: the napplet
    // gateway answers REQs out of this, and NIP-01 has relays return events newest first.
    val all = (addressableMatches + noteMatches).toSet().sortedWith(CreatedAtIdHexComparator)
    val limit = filter.limit ?: return all

    // Sorted first, then cut. Both halves arrive in hash-walk order, so taking before sorting
    // dropped whichever matches the walk happened to reach last — the newest ones as often as
    // not — and a query with 200 addressable matches never showed a single regular note.
    if (all.size <= limit) return all
    return all.take(limit)
}

fun EventCache.filterRegularNotes(
    filter: Filter,
    predicate: (Note) -> Boolean,
): List<Note> =
    notes.filter { _, note ->
        val event = note.event
        if (event != null && event.kind.isRegular()) {
            filter.match(event) && predicate(note)
        } else {
            false
        }
    }

fun EventCache.observeNotes(filter: Filter): Flow<List<Note>> =
    callbackFlow {
        val newFilter =
            NoteListMatchingFilter(filter, this@observeNotes::filter) {
                trySend(it)
            }

        startObserving(filter, newFilter, newFilter::start)

        awaitClose {
            observables.unregister(newFilter)
        }
    }.buffer(CoroutineChannel.CONFLATED).flowOn(observerScanContext)

fun <T : Event> EventCache.observeEvents(filter: Filter): Flow<List<T>> =
    callbackFlow {
        val cachedFilter =
            EventListMatchingFilter<T>(filter, this@observeEvents::filter) {
                trySend(it)
            }

        startObserving(filter, cachedFilter, cachedFilter::start)

        awaitClose {
            observables.unregister(cachedFilter)
        }
    }.buffer(CoroutineChannel.CONFLATED).flowOn(observerScanContext)

/**
 * Emits each new event for which [predicate] returns true, one at a time,
 * as it is inserted into the cache. Unlike [observeEvents], this does not
 * accumulate a list — useful for per-event reactive pipelines like
 * notifications.
 *
 * The predicate runs on every insertion, so keep it cheap. Callers with a
 * Nostr [Filter] can pass `filter::match`; compose additional local checks
 * (rolling windows, derived fields the Filter grammar can't express) with
 * `&&`.
 */
fun <T : Event> EventCache.observeNewEvents(predicate: (Event) -> Boolean): Flow<T> =
    callbackFlow {
        val newFilter =
            NewEventMatchingFilter<T>(predicate) {
                trySend(it)
            }

        // Unindexed: predicate is opaque, the index can't narrow.
        // Caller is delivered every event and runs the predicate.
        observables.registerUnindexed(newFilter)

        awaitClose {
            observables.unregister(newFilter)
        }
    }

fun <T : Event> EventCache.observeNewEvents(filter: Filter): Flow<T> =
    callbackFlow {
        val newFilter =
            NewEventMatchingFilter<T>(filter::match) {
                trySend(it)
            }

        observables.register(filter, newFilter)

        awaitClose {
            observables.unregister(newFilter)
        }
    }

@Suppress("UNCHECKED_CAST")
fun <T : Event> EventCache.observeLatestEvent(filter: Filter) = observeEvents<T>(filter).map { it.firstOrNull() }

fun EventCache.observeLatestNote(filter: Filter) = observeNotes(filter).map { it.firstOrNull() }
