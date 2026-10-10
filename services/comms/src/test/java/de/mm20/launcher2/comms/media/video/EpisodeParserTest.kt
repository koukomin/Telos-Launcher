package de.mm20.launcher2.comms.media.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeParserTest {

    private fun notRecognised(name: String, folder: String = "") {
        val p = EpisodeParser.parse(name, folder)
        assertNull("$name $folder", p.year)
        assertFalse("$name $folder", p.isEpisode)
    }

    @Test fun movieWithParenthesisedYear() {
        val p = EpisodeParser.parse("Inception (2010).mkv")
        assertEquals("Inception", p.title)
        assertEquals(2010, p.year)
        assertFalse(p.isEpisode)
    }

    @Test fun movieWithDottedName() {
        val p = EpisodeParser.parse("The.Matrix.1999.1080p.BluRay.mkv")
        assertEquals("The Matrix", p.title)
        assertEquals(1999, p.year)
    }

    @Test fun movieWithBracketsAndSpaces() {
        assertEquals(2014, EpisodeParser.parse("Interstellar [2014].mp4").year)
        assertEquals(1968, EpisodeParser.parse("2001 A Space Odyssey 1968.mkv").year)
    }

    @Test fun messengerAndCameraNamesAreNotMovies() {
        notRecognised("video_2024-03-05_12-30-11.mp4")
        notRecognised("VID-20240305-WA0001.mp4")
        notRecognised("20240305_123011.mp4")
        notRecognised("VID_20240305_123011.mp4")
        notRecognised("PXL_20240305_123011234.mp4")
        notRecognised("Screen_Recording_20240305-123011.mp4")
        notRecognised("Screenrecorder-2024-03-05-12-30-11-123.mp4")
        notRecognised("Holiday 05-03-2024.mp4")
        notRecognised("Holiday 2024-03-05.mp4")
        notRecognised("video 2024.mp4")
    }

    @Test fun yearMustBeStandalone() {
        notRecognised("clip20101.mp4")
        notRecognised("Movie 3201010.mp4")
    }

    @Test fun episodePattern() {
        val p = EpisodeParser.parse("Show.S01E02.mkv")
        assertTrue(p.isEpisode)
        assertEquals("Show", p.title)
        assertEquals(1, p.season)
        assertEquals(2, p.episode)
    }

    @Test fun blockedFoldersReject() {
        notRecognised("Inception (2010).mp4", "Android/media/com.viber.voip/Viber/Viber Videos")
        notRecognised("Show.S01E02.mkv", "Movies/WhatsApp Video/")
        notRecognised("Inception (2010).mp4", "DCIM/Camera/")
        notRecognised("Inception (2010).mp4", "Movies/Telos/")
        notRecognised("Inception (2010).mp4", "Download/Telegram/")
        assertTrue(EpisodeParser.isBlockedFolder("Viber"))
        assertFalse(EpisodeParser.isBlockedFolder("Movies/Films/"))
    }

    @Test fun normalFoldersKeepWorking() {
        assertEquals(2010, EpisodeParser.parse("Inception (2010).mkv", "Movies/Films/").year)
        assertTrue(EpisodeParser.parse("Show.S01E02.mkv", "Download/").isEpisode)
    }
}
