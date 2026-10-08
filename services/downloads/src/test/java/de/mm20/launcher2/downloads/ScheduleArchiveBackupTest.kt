package de.mm20.launcher2.downloads

import de.mm20.launcher2.downloads.logic.*
import org.junit.Assert.*
import org.junit.Test

class ScheduleArchiveBackupTest {
    private val allDays = 0b1111111
    private val night = ScheduleWindow(true, startMinute = 22 * 60, endMinute = 7 * 60, days = allDays)

    @Test fun disabledIsAlwaysOpen() {
        assertTrue(Schedule.isOpen(ScheduleWindow(false), ClockTime(3, 12 * 60)))
        assertNull(Schedule.minutesToChange(ScheduleWindow(false), ClockTime(3, 0)))
    }

    @Test fun windowOverMidnight() {
        assertTrue(Schedule.isOpen(night, ClockTime(1, 23 * 60)))
        assertTrue(Schedule.isOpen(night, ClockTime(2, 6 * 60 + 59)))
        assertFalse(Schedule.isOpen(night, ClockTime(2, 7 * 60)))
        assertFalse(Schedule.isOpen(night, ClockTime(2, 12 * 60)))
        assertTrue(Schedule.isOpen(night, ClockTime(2, 22 * 60)))
    }

    @Test fun sameDayWindow() {
        val w = ScheduleWindow(true, 9 * 60, 17 * 60, allDays)
        assertFalse(Schedule.isOpen(w, ClockTime(1, 8 * 60 + 59)))
        assertTrue(Schedule.isOpen(w, ClockTime(1, 9 * 60)))
        assertFalse(Schedule.isOpen(w, ClockTime(1, 17 * 60)))
    }

    @Test fun daysAreTheDaysTheWindowStartsOn() {
        // Friday (bit 4) night only: Friday 23:00 and Saturday 03:00 are open, Saturday 23:00 is not
        val w = ScheduleWindow(true, 22 * 60, 7 * 60, days = 1 shl 4)
        assertTrue(Schedule.isOpen(w, ClockTime(5, 23 * 60)))
        assertTrue(Schedule.isOpen(w, ClockTime(6, 3 * 60)))
        assertFalse(Schedule.isOpen(w, ClockTime(6, 23 * 60)))
        assertFalse(Schedule.isOpen(w, ClockTime(5, 3 * 60)))
        // Sunday night wraps to Monday
        val sun = ScheduleWindow(true, 22 * 60, 7 * 60, days = 1 shl 6)
        assertTrue(Schedule.isOpen(sun, ClockTime(1, 2 * 60)))
    }

    @Test fun minutesToChange() {
        assertEquals(60, Schedule.minutesToChange(night, ClockTime(1, 21 * 60)))
        assertEquals(30, Schedule.minutesToChange(night, ClockTime(2, 6 * 60 + 30)))
        // Sunday 00:30 -> window closes at 07:00 (the Saturday night one runs into Sunday only if Saturday is on)
        assertEquals(6 * 60 + 30, Schedule.minutesToChange(night, ClockTime(7, 30)))
        assertNull(Schedule.minutesToChange(ScheduleWindow(true, 0, 0, allDays), ClockTime(1, 5)))
        assertNull(Schedule.minutesToChange(ScheduleWindow(true, 0, 0, 0), ClockTime(1, 5)))
    }

    @Test fun queueWaitsOutsideTheWindow() {
        val s = QueueSettings(schedule = night)
        val t = DownloadTask(id = "a", url = "https://x")
        assertEquals(BlockReason.Schedule, QueueRules.blockReason(Conditions(), s, ClockTime(1, 12 * 60)))
        assertNull(QueueRules.blockReason(Conditions(), s, ClockTime(1, 23 * 60)))
        assertNull(QueueRules.blockReason(Conditions(), s, null))
        assertTrue(QueueRules.pickNext(listOf(t), 0, Conditions(), s, ClockTime(1, 12 * 60)).isEmpty())
        assertEquals(1, QueueRules.pickNext(listOf(t), 0, Conditions(), s, ClockTime(1, 23 * 60)).size)
        assertEquals(BlockReason.Offline, QueueRules.blockReason(Conditions(online = false), s, ClockTime(1, 12 * 60)))
    }

    @Test fun archives() {
        assertTrue(ArchiveLogic.isZip("a.ZIP")); assertFalse(ArchiveLogic.isZip("a.tar.gz"))
        assertEquals("My Files", ArchiveLogic.folderName("My Files.zip"))
        assertEquals(listOf("a", "b.txt"), ArchiveLogic.targetSegments("a/b.txt", false))
        assertEquals(listOf("etc", "passwd"), ArchiveLogic.targetSegments("../../etc/passwd", false))
        assertEquals(listOf("x"), ArchiveLogic.targetSegments("/abs/./../x", false).orEmpty().takeLast(1))
        assertNull(ArchiveLogic.targetSegments("dir/", true))
        assertNull(ArchiveLogic.targetSegments("..", false))
    }

    // ---- backup

    @Test fun backupDropsSecrets() {
        val t = DownloadTask(id = "a", url = "https://x", cookies = "s=1", headers = mapOf("Authorization" to "Bearer x", "Cookie" to "c", "X-A" to "1"), speedBps = 5)
        val b = DownloadsBackup.sanitizeForBackup(t)
        assertNull(b.cookies)
        assertEquals(mapOf("X-A" to "1"), b.headers)
        assertEquals(0L, b.speedBps)
    }

    @Test fun restoredCompletedTaskWithMissingFile() {
        val done = DownloadTask(id = "a", url = "https://x", state = DownloadState.Completed, fileUri = "content://f/1", totalBytes = 10)
        assertFalse(DownloadsBackup.restoreTask(done) { true }.fileMissing)
        assertTrue(DownloadsBackup.restoreTask(done) { false }.fileMissing)
        assertTrue(DownloadsBackup.restoreTask(done.copy(fileUri = null)) { true }.fileMissing)
    }

    @Test fun restoredUnfinishedTaskStartsOverPaused() {
        val t = DownloadTask(id = "a", url = "https://x", state = DownloadState.Downloading, downloadedBytes = 5, fileUri = "content://f/1",
            segments = listOf(SegmentState(0, 0, 9, 5)), etag = "e")
        val r = DownloadsBackup.restoreTask(t) { true }
        assertEquals(DownloadState.Paused, r.state)
        assertEquals(0L, r.downloadedBytes)
        assertNull(r.fileUri); assertTrue(r.segments.isEmpty()); assertNull(r.etag)
    }

    @Test fun restoredSeedingTorrentIsCompleted() {
        val files = listOf(TorrentFile(0, "a.bin", 10, uri = "content://f/2"))
        val t = DownloadTask(id = "a", url = "magnet:?x", type = DownloadType.Torrent, state = DownloadState.Seeding, torrent = TorrentData(files = files))
        val r = DownloadsBackup.restoreTask(t) { true }
        assertEquals(DownloadState.Completed, r.state)
        assertFalse(r.fileMissing)
        assertTrue(DownloadsBackup.restoreTask(t) { false }.fileMissing)
    }
}
