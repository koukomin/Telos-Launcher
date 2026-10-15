package de.mm20.launcher2.music

/** Built-in playback is eligible by default, while an explicit user exclusion still wins. */
internal fun eligibleMusicPackages(discovered: Set<String>, ownPackage: String,
    allow: Set<String>, deny: Set<String>): Set<String> = (discovered + ownPackage + allow) - deny

/** Paused sessions may post a late notification when another player acquires audio focus. */
internal fun <T> selectPlaybackCandidate(candidates: List<T>, isPlaying: (T) -> Boolean,
    postTime: (T) -> Long): T? = candidates.maxWithOrNull(compareBy<T> { isPlaying(it) }.thenBy { postTime(it) })
