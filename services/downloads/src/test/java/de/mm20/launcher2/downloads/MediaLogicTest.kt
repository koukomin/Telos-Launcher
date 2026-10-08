package de.mm20.launcher2.downloads

import de.mm20.launcher2.downloads.logic.*
import org.junit.Assert.*
import org.junit.Test

class MediaLogicTest {
    // ---- progress lines

    @Test fun parsesProgressLine() {
        val l = YtDlpOutput.parse("[tlsprog] 1048576 NA 4194304 262144.5 12")
        assertEquals(MediaLine.Progress(1048576, 4194304, 262144, 12), l)
    }

    @Test fun progressWithUnknownValues() {
        val l = YtDlpOutput.parse("[tlsprog] 500 NA NA NA NA") as MediaLine.Progress
        assertEquals(-1, l.total)
        assertEquals(0, l.speedBps)
        assertEquals(-1, l.etaSeconds)
    }

    @Test fun parsesStages() {
        assertEquals(MediaLine.Stage(de.mm20.launcher2.downloads.MediaStage.Merging), YtDlpOutput.parse("[Merger] Merging formats into \"a.mp4\""))
        assertEquals(MediaLine.Stage(de.mm20.launcher2.downloads.MediaStage.ExtractingAudio), YtDlpOutput.parse("[ExtractAudio] Destination: a.mp3"))
        assertEquals(MediaLine.Destination("/x/a.f137.mp4"), YtDlpOutput.parse("[download] Destination: /x/a.f137.mp4"))
        assertEquals(MediaLine.Other, YtDlpOutput.parse("[youtube] Extracting URL"))
        assertEquals(MediaLine.Other, YtDlpOutput.parse("[tlsprog] garbage"))
    }

    @Test fun trackerAddsUpStreams() {
        val t = MediaProgressTracker(expectedTotal = 1500)
        t.feed("[download] Destination: v.mp4")
        assertEquals(1000L, t.feed("[tlsprog] 1000 1000 NA 100 1")!!.downloaded)
        t.feed("[download] Destination: a.m4a")
        val s = t.feed("[tlsprog] 200 500 NA 100 3")!!
        assertEquals(1200L, s.downloaded)
        assertEquals(1500L, s.total)
    }

    @Test fun trackerWithoutEstimateUsesStreamTotals() {
        val t = MediaProgressTracker()
        t.feed("[download] Destination: v.mp4")
        t.feed("[tlsprog] 1000 1000 NA 100 1")
        t.feed("[download] Destination: a.m4a")
        val s = t.feed("[tlsprog] 100 400 NA 100 3")!!
        assertEquals(1100L, s.downloaded)
        assertEquals(1400L, s.total)
    }

    // ---- format selection and arguments

    @Test fun selectors() {
        assertEquals("bv*+ba/b", MediaFormats.selector(MediaData()))
        assertEquals("bv*[height<=720]+ba/b[height<=720]/bv*+ba/b", MediaFormats.selector(MediaData(heightCap = 720)))
        assertEquals("ba/b", MediaFormats.selector(MediaData(audioOnly = true, heightCap = 720)))
    }

    private val env = MediaFormats.Env(stagingDir = "/data/s/")

    @Test fun audioArgs() {
        val a = MediaFormats.downloadArgs("https://y/x", MediaData(audioOnly = true, audioFormat = "opus", container = "mkv"), env)
        assertTrue(a.containsAll(listOf("-x", "--audio-format", "opus")))
        assertFalse(a.contains("--merge-output-format"))
        assertEquals("https://y/x", a.last())
        assertEquals("--", a[a.size - 2])
    }

    @Test fun badAudioFormatFallsBackToMp3() {
        val a = MediaFormats.downloadArgs("u", MediaData(audioOnly = true, audioFormat = "evil;rm"), env)
        assertEquals("mp3", a[a.indexOf("--audio-format") + 1])
    }

    @Test fun videoOptions() {
        val m = MediaData(container = "mp4", subtitles = SubtitleMode.Embed, subLangs = "en.*,el", embedThumbnail = true, embedMetadata = true, sponsorBlock = true)
        val a = MediaFormats.downloadArgs("u", m, MediaFormats.Env("/s", cookiesFile = "/s/cookies.txt", rateLimitBps = 512 * 1024, proxy = "socks5://h:1"))
        assertEquals("mp4", a[a.indexOf("--merge-output-format") + 1])
        assertTrue(a.containsAll(listOf("--write-subs", "--embed-subs", "--embed-thumbnail", "--embed-metadata", "--sponsorblock-remove")))
        assertEquals("en.*,el", a[a.indexOf("--sub-langs") + 1])
        assertEquals("/s/cookies.txt", a[a.indexOf("--cookies") + 1])
        assertEquals("512K", a[a.indexOf("--limit-rate") + 1])
        assertEquals("socks5://h:1", a[a.indexOf("--proxy") + 1])
        assertEquals("/s/%(title).100s [%(id)s].%(ext)s", a[a.indexOf("-o") + 1])
    }

    @Test fun subtitleFilesAreNotEmbedded() {
        val a = MediaFormats.downloadArgs("u", MediaData(subtitles = SubtitleMode.Files), env)
        assertTrue(a.contains("--write-subs"))
        assertFalse(a.contains("--embed-subs"))
    }

    @Test fun urlCanNotBeTakenForAnOption() {
        val a = MediaFormats.downloadArgs("--exec=evil", MediaData(), env)
        assertEquals(listOf("--", "--exec=evil"), a.takeLast(2))
    }

    // ---- analysis

    private val video = """{"_type":"video","title":"T","uploader":"U","duration":125.5,"thumbnail":"https://i/t.jpg","webpage_url":"https://y/w",
        "extractor_key":"Youtube","formats":[
        {"format_id":"140","ext":"m4a","vcodec":"none","acodec":"mp4a","filesize":1000,"abr":128},
        {"format_id":"137","ext":"mp4","vcodec":"avc1","acodec":"none","height":1080,"filesize":9000},
        {"format_id":"136","ext":"mp4","vcodec":"avc1","acodec":"none","height":720,"filesize_approx":5000.0},
        {"format_id":"sb0","ext":"mhtml","vcodec":"none","acodec":"none"}]}"""

    @Test fun parsesVideo() {
        val i = MediaInfoParser.parse(video)!!
        assertEquals("T", i.title); assertEquals("U", i.uploader); assertEquals(125L, i.durationSec)
        assertFalse(i.isPlaylist)
        assertEquals(listOf(1080, 720), i.heights)
        assertEquals(3, i.formats.size)
    }

    @Test fun estimatesSize() {
        val i = MediaInfoParser.parse(video)!!
        assertEquals(10000L, MediaFormats.estimateBytes(i, MediaData()))
        assertEquals(6000L, MediaFormats.estimateBytes(i, MediaData(heightCap = 720)))
        assertEquals(1000L, MediaFormats.estimateBytes(i, MediaData(audioOnly = true)))
    }

    @Test fun parsesPlaylist() {
        val json = """WARNING: x
            {"_type":"playlist","title":"P","entries":[
            {"url":"https://y/1","title":"One","duration":10,"thumbnails":[{"url":"https://i/a"},{"url":"https://i/b"}]},
            {"url":"abc","title":"no link"},{"webpage_url":"https://y/2","title":"Two"}]}"""
        val i = MediaInfoParser.parse(json)!!
        assertTrue(i.isPlaylist)
        assertEquals(listOf("https://y/1", "https://y/2"), i.entries.map { it.url })
        assertEquals("https://i/b", i.entries[0].thumbnail)
    }

    @Test fun notJson() {
        assertNull(MediaInfoParser.parse("ERROR: Unsupported URL"))
        assertNull(MediaInfoParser.parse("{broken"))
    }

    // ---- URL classification

    @Test fun classifiesLinks() {
        assertEquals(LinkKind.Media, MediaUrls.classify("https://www.youtube.com/watch?v=abc"))
        assertEquals(LinkKind.Media, MediaUrls.classify("https://m.youtube.com/watch?v=abc"))
        assertEquals(LinkKind.Media, MediaUrls.classify("https://youtu.be/abc"))
        assertEquals(LinkKind.Media, MediaUrls.classify("https://artist.bandcamp.com/track/x"))
        assertEquals(LinkKind.Magnet, MediaUrls.classify("magnet:?xt=urn:btih:abc"))
        assertEquals(LinkKind.File, MediaUrls.classify("https://example.com/files/a.zip?x=1"))
        assertEquals(LinkKind.Web, MediaUrls.classify("https://example.com/page"))
        assertEquals(LinkKind.NotALink, MediaUrls.classify("hello world"))
        assertEquals(LinkKind.NotALink, MediaUrls.classify("https://a.com b"))
        assertEquals(LinkKind.Web, MediaUrls.classify("https://notyoutube.com/x"))
    }

    // ---- errors

    @Test fun mapsErrors() {
        assertEquals(MediaError.Unsupported, YtDlpErrors.classify("ERROR: Unsupported URL: https://x"))
        assertEquals(MediaError.LoginRequired, YtDlpErrors.classify("ERROR: [youtube] x: Sign in to confirm you're not a bot"))
        assertEquals(MediaError.RateLimited, YtDlpErrors.classify("ERROR: HTTP Error 429: Too Many Requests"))
        assertEquals(MediaError.GeoBlocked, YtDlpErrors.classify("ERROR: This video is not available in your country"))
        assertEquals(MediaError.Private, YtDlpErrors.classify("ERROR: Private video. Sign in if you've been granted access"))
        assertEquals(MediaError.Unavailable, YtDlpErrors.classify("ERROR: Video unavailable"))
        assertEquals(MediaError.Network, YtDlpErrors.classify("ERROR: Unable to download webpage: <urlopen error timed out>"))
        assertEquals(MediaError.DiskFull, YtDlpErrors.classify("OSError: [Errno 28] No space left on device"))
        assertEquals(MediaError.FormatUnavailable, YtDlpErrors.classify("ERROR: Requested format is not available"))
        assertEquals(MediaError.Other, YtDlpErrors.classify("something else"))
        assertTrue(MediaError.Network.retryable); assertFalse(MediaError.Unsupported.retryable)
        assertEquals("ERROR: b", YtDlpErrors.detail("WARNING: a\nERROR: b\nlast"))
    }

    // ---- cookies

    @Test fun cookieFiles() {
        val text = "# Netscape HTTP Cookie File\n.example.com\tTRUE\t/\tTRUE\t0\tsid\tabc\n#HttpOnly_.example.com\tTRUE\t/\tTRUE\t0\ta\tb\n# comment\nbad line\n"
        assertEquals(2, CookieFiles.countCookies(text))
        assertEquals(0, CookieFiles.countCookies("a=b"))
        val built = CookieFiles.fromHeader("https://www.sub.example.com/x", "sid=abc; theme=dark; =x; nothing")
        assertEquals(2, CookieFiles.countCookies(built))
        assertTrue(built.contains(".example.com\tTRUE\t/\tTRUE\t0\tsid\tabc"))
    }

    // ---- staging files

    @Test fun finishedOutputs() {
        val names = listOf("A [x].mp4", "A [x].mp4.part", "A [x].f137.mp4.part", "A [x].en.vtt", "cookies.txt", "A [x].ytdl", "A.f251.webm")
        assertEquals(listOf("A [x].mp4", "A [x].en.vtt"), MediaFiles.finishedOutputs(names))
        assertEquals("A [x].mp4", MediaFiles.mainFile(listOf("A [x].en.vtt" to 5L, "A [x].mp4" to 10L)))
    }
}
