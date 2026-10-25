package de.mm20.launcher2.freeze

import org.junit.Assert.*
import org.junit.Test

class AudioAppOpsTest {
    @Test fun parsesRealAndroidShellRecordsWithoutConfusingHistoryWithPolicy() {
        assertEquals("default", parseAudioMode("No operations.\nDefault mode: allow\n", AudioOperation.PLAY_AUDIO))
        assertEquals("ignore", parseAudioMode("PLAY_AUDIO: ignore; rejectTime=+2s ago", AudioOperation.PLAY_AUDIO))
        assertEquals("allow", parseAudioMode("TAKE_AUDIO_FOCUS: allow; time=+2s ago (running)", AudioOperation.TAKE_AUDIO_FOCUS))
        assertEquals("default", parseAudioMode("TAKE_AUDIO_FOCUS: default", AudioOperation.TAKE_AUDIO_FOCUS))
    }
    @Test fun refusesUidOverridesUnsupportedOutputAndWrongOperations() {
        for (text in listOf("", "Error: Unknown operation", "Uid mode: PLAY_AUDIO: ignore\nPLAY_AUDIO: allow", "Uid mode: PLAY_AUDIO: allow", "PLAY_AUDIO: allow\nPLAY_AUDIO: ignore", "TAKE_AUDIO_FOCUS: allow", "x".repeat(8193)))
            assertNull(text, parseAudioMode(text, AudioOperation.PLAY_AUDIO))
    }
    @Test fun commandsAreUserScopedAndCannotResetUnrelatedAppOps() {
        assertEquals(listOf("cmd", "appops", "set", "--user", "10", "com.example", "PLAY_AUDIO", "ignore"),
            audioCommand("com.example", 10, AudioOperation.PLAY_AUDIO, "ignore"))
        for (pkg in listOf("a;b", "a$(id)", "a b", "a\nb", "--uid")) {
            assertThrows(IllegalArgumentException::class.java) { audioCommand(pkg, 0, AudioOperation.PLAY_AUDIO) }
        }
        assertThrows(IllegalArgumentException::class.java) { audioCommand("com.example", 0, AudioOperation.PLAY_AUDIO, "reset") }
    }
}
