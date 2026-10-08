package de.mm20.launcher2.downloads

import de.mm20.launcher2.downloads.logic.*
import org.junit.Assert.*
import org.junit.Test

class TorrentLogicTest {
    private fun files() = listOf(
        TorrentFile(0, "Show/a.mkv", 1000),
        TorrentFile(1, "Show/b.mkv", 3000),
        TorrentFile(3, "Show/info.txt", 10),
    )

    // ---- sources

    @Test fun classifiesSources() {
        assertEquals(TorrentSourceKind.Magnet, TorrentSources.classify("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=x"))
        assertEquals(TorrentSourceKind.TorrentUrl, TorrentSources.classify("https://example.org/a/file.torrent?key=1"))
        assertEquals(TorrentSourceKind.None, TorrentSources.classify("https://example.org/file.zip"))
        assertEquals(TorrentSourceKind.TorrentFile, TorrentSources.classify("file:///data/x.torrent"))
        assertEquals(TorrentSourceKind.None, TorrentSources.classify("magnet:?dn=nohash"))
    }

    @Test fun extractsLinksFromText() {
        val text = "see magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=A%20B, and https://x.org/t.torrent. also https://x.org/page"
        val found = TorrentSources.extract(text)
        assertEquals(2, found.size)
        assertTrue(found[0].startsWith("magnet:?xt="))
        assertEquals("https://x.org/t.torrent", found[1])
        assertTrue(TorrentSources.extract("nothing here").isEmpty())
    }

    @Test fun magnetParts() {
        val m = "magnet:?xt=urn:btih:0123456789ABCDEF0123456789ABCDEF01234567&dn=My%20File&tr=udp%3A%2F%2Ft.example%3A80"
        assertEquals("0123456789abcdef0123456789abcdef01234567", TorrentSources.magnetInfoHash(m))
        assertEquals("My File", TorrentSources.magnetName(m))
        assertEquals(listOf("udp://t.example:80"), TorrentSources.magnetTrackers(m))
        assertNull(TorrentSources.magnetInfoHash("magnet:?xt=urn:btih:short"))
    }

    @Test fun base32InfoHash() {
        // 20 bytes of 0x00 are 32 times 'A' in base32
        assertEquals("0".repeat(40), TorrentSources.magnetInfoHash("magnet:?xt=urn:btih:" + "A".repeat(32)))
    }

    // ---- file selection

    @Test fun prioritiesFollowTheFileIndex() {
        val p = FileSelection.priorities(files().map { if (it.index == 1) it.copy(priority = 7) else it })
        assertEquals(listOf(4, 7, 0, 4), p.toList()) // index 2 is not in the list (a pad file): skipped
    }

    @Test fun selectKeepsOnlyChosenFiles() {
        val s = FileSelection.select(files(), setOf(1))
        assertEquals(listOf(0, 4, 0), s.map { it.priority })
        assertEquals(3000, FileSelection.selectedBytes(s))
        assertEquals(1, FileSelection.selectedCount(s))
    }

    @Test fun applyChangesOnlyTouchesNamedFiles() {
        val s = FileSelection.apply(files(), mapOf(0 to 0, 3 to 99))
        assertEquals(listOf(0, 4, 7), s.map { it.priority })
    }

    @Test fun atLeastOneWantedFile() {
        assertTrue(FileSelection.isValid(files()))
        assertFalse(FileSelection.isValid(FileSelection.select(files(), emptySet())))
        assertTrue(FileSelection.isValid(emptyList())) // not known yet (magnet link)
    }

    @Test fun remainingBytesCountOnlyWantedFiles() {
        val f = listOf(TorrentFile(0, "a", 100, 4, done = 30), TorrentFile(1, "b", 100, 0, done = 0), TorrentFile(2, "c", 50, 4, done = 80))
        assertEquals(70, FileSelection.remainingBytes(f))
    }

    // ---- seeding

    @Test fun seedingStopsAtRatio() {
        assertFalse(SeedRules.shouldStop(false, 150, 0, 1.49, 99999))
        assertTrue(SeedRules.shouldStop(false, 150, 0, 1.5, 0))
    }

    @Test fun seedingStopsAfterTime() {
        assertFalse(SeedRules.shouldStop(false, 0, 60, 99.0, 3599))
        assertTrue(SeedRules.shouldStop(false, 0, 60, 0.0, 3600))
    }

    @Test fun eitherLimitEnds() {
        assertTrue(SeedRules.shouldStop(false, 200, 10, 0.1, 600))
        assertTrue(SeedRules.shouldStop(false, 200, 10, 2.0, 1))
    }

    @Test fun noLimitsMeansSeedUntilStopped() {
        assertFalse(SeedRules.shouldStop(false, 0, 0, 1000.0, 10_000_000))
    }

    @Test fun stopAtDoneEndsImmediately() {
        assertTrue(SeedRules.shouldStop(true, 0, 0, 0.0, 0))
    }

    @Test fun ratioUsesReceivedBytesOrSize() {
        assertEquals(0.5, SeedRules.ratio(50, 100, 999), 0.0001)
        assertEquals(2.0, SeedRules.ratio(200, 0, 100), 0.0001) // complete when added
        assertEquals(0.0, SeedRules.ratio(10, 0, 0), 0.0001)
        assertEquals("1.25", SeedRules.formatRatio(1.249))
    }

    @Test fun taskRatioFromStoredNumbers() {
        val d = TorrentData(uploadedBytes = 300, receivedBytes = 200)
        assertEquals(1.5, d.ratio, 0.0001)
        assertEquals(0.0, TorrentData().ratio, 0.0001)
    }

    // ---- piece map

    @Test fun pieceMapCellsAverageThePieces() {
        val have = setOf(0, 1, 2, 3, 8)
        val cells = PieceMap.cells(10, 2) { it in have }
        assertEquals(listOf(0.8f, 0.2f), cells.toList())
    }

    @Test fun pieceMapWithFewPieces() {
        val cells = PieceMap.cells(3, 120) { it == 1 }
        assertEquals(listOf(0f, 1f, 0f), cells.toList())
        assertEquals(0, PieceMap.cells(0, 10) { true }.size)
    }

    // ---- peers and paths

    @Test fun peerFlagLetters() {
        assertEquals("DUIE", PeerFlagText.describe(true, false, true, false, false, false, true, true, false))
        assertEquals("duOSCP", PeerFlagText.describe(true, true, true, true, true, true, false, false, true))
    }

    @Test fun torrentPathsAreMadeSafe() {
        assertEquals(listOf("a", "b.txt"), TorrentPaths.safeSegments("../a/./b.txt"))
        assertEquals(listOf("a", "b_c"), TorrentPaths.safeSegments("a\\b:c"))
        assertEquals("Show", TorrentPaths.rootFolder(listOf("Show/a.mkv", "Show/sub/b.mkv")))
        assertEquals("", TorrentPaths.rootFolder(listOf("a.mkv")))
        assertEquals("", TorrentPaths.rootFolder(listOf("A/a.mkv", "B/b.mkv")))
    }

    // ---- queue

    @Test fun seedingTorrentsDoNotTakeDownloadSlots() {
        fun t(id: String, st: DownloadState) = DownloadTask(id = id, url = "u$id", state = st, createdAt = id.hashCode().toLong())
        val tasks = listOf(t("s1", DownloadState.Seeding), t("s2", DownloadState.Seeding), t("q", DownloadState.Queued))
        val next = QueueRules.pickNext(tasks, 0, Conditions(), QueueSettings(maxParallel = 1))
        assertEquals(listOf("q"), next.map { it.id })
        assertTrue(DownloadState.Seeding.isActive)
    }
}
