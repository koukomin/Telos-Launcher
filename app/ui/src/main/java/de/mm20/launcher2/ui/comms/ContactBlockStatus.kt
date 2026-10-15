package de.mm20.launcher2.ui.comms

import kotlinx.coroutines.CancellationException

internal enum class ContactBlockStatus { Loading, Blocked, Unblocked, Unavailable }

/** Optional block-list data must not take down the contact page or masquerade as unblocked. */
internal suspend fun readContactBlockStatus(read: suspend () -> Boolean, onFailure: (Throwable) -> Unit): ContactBlockStatus =
    try {
        if (read()) ContactBlockStatus.Blocked else ContactBlockStatus.Unblocked
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: LinkageError) {
        onFailure(error)
        ContactBlockStatus.Unavailable
    } catch (error: Exception) {
        onFailure(error)
        ContactBlockStatus.Unavailable
    }
