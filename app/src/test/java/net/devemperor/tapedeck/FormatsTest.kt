package net.devemperor.tapedeck

import net.devemperor.tapedeck.data.DownloadOptions
import net.devemperor.tapedeck.data.MediaKind
import net.devemperor.tapedeck.data.VideoFormat
import net.devemperor.tapedeck.engine.ErrorKind
import net.devemperor.tapedeck.engine.Errors
import net.devemperor.tapedeck.engine.Formats
import net.devemperor.tapedeck.engine.Links
import net.devemperor.tapedeck.engine.Site
import net.devemperor.tapedeck.engine.YtDlpArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsTest {
    // Shortened from `yt-dlp -J` of a YouTube video (formats as yt-dlp 2026.11 lists them).
    private val youtube = """
        {
          "title": "Big Buck Bunny", "uploader": "Blender", "duration": 596, "thumbnail": "https://i.ytimg.com/vi/x/maxresdefault.jpg",
          "formats": [
            {"format_id": "sb0", "ext": "mhtml", "vcodec": "none", "acodec": "none", "format_note": "storyboard", "protocol": "mhtml"},
            {"format_id": "139", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.5", "abr": 48.8, "filesize": 3640000, "audio_channels": 2, "language": "en"},
            {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "abr": 129.5, "filesize": 9650000, "audio_channels": 2, "language": "en"},
            {"format_id": "140-drc", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "abr": 129.5, "filesize": 9650000, "audio_channels": 2, "language": "en"},
            {"format_id": "251", "ext": "webm", "vcodec": "none", "acodec": "opus", "abr": 135.2, "filesize_approx": 10100000, "audio_channels": 2, "language": "en"},
            {"format_id": "258", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "abr": 384.0, "audio_channels": 6, "language": "en"},
            {"format_id": "18", "ext": "mp4", "vcodec": "avc1.42001E", "acodec": "mp4a.40.2", "width": 640, "height": 360, "fps": 24, "tbr": 503.1},
            {"format_id": "137", "ext": "mp4", "vcodec": "avc1.640028", "acodec": "none", "width": 1920, "height": 1080, "fps": 24, "vbr": 2400.0, "tbr": 2400.0, "filesize": 178800000},
            {"format_id": "248", "ext": "webm", "vcodec": "vp9", "acodec": "none", "width": 1920, "height": 1080, "fps": 24, "vbr": 1500.0, "tbr": 1500.0},
            {"format_id": "299", "ext": "mp4", "vcodec": "avc1.64002a", "acodec": "none", "width": 1920, "height": 1080, "fps": 60, "tbr": 4000.0, "dynamic_range": "SDR"},
            {"format_id": "337", "ext": "webm", "vcodec": "vp09.02.51.10.01.09.16.09.00", "acodec": "none", "width": 3840, "height": 2160, "fps": 60, "tbr": 18000.0, "dynamic_range": "HDR10"}
          ]
        }
    """.trimIndent()

    @Test
    fun `parses formats and leaves storyboards out`() {
        val details = Formats.parse(youtube)
        assertEquals("Big Buck Bunny", details.title)
        assertEquals("Blender", details.uploader)
        assertEquals(596_000L, details.durationMs)
        assertEquals(10, details.formats.size)
        assertNull(details.formats.firstOrNull { it.id == "sb0" })
        val muxed = details.formats.single { it.isMuxed }
        assertEquals("18", muxed.id)
    }

    @Test
    fun `sorts pictures and sounds best first`() {
        val details = Formats.parse(youtube)
        assertEquals(listOf("337", "299", "137", "248", "18"), details.videos.map { it.id })
        assertEquals("258", details.audios.first().id)
        assertEquals("139", details.audios.last().id)
    }

    @Test
    fun `names resolutions and codecs the way people know them`() {
        val details = Formats.parse(youtube)
        fun f(id: String) = details.formats.single { it.id == id }
        assertEquals("1080p", f("137").resolutionLabel)
        assertEquals("1080p60", f("299").resolutionLabel)
        assertEquals("2160p60 HDR", f("337").resolutionLabel)
        assertEquals("360p", f("18").resolutionLabel)
        assertEquals("H.264", f("137").videoCodecName)
        assertEquals("VP9", f("337").videoCodecName)
        assertEquals("AAC", f("140").audioCodecName)
        assertEquals("Opus", f("251").audioCodecName)
        assertEquals(130, f("140").bitrate)
        // A portrait reel and a cinemascope film keep their resolution class.
        assertEquals("720p", f("18").copy(width = 720, height = 1280).resolutionLabel)
        assertEquals("1080p", f("137").copy(height = 800).resolutionLabel)
    }

    @Test
    fun `knows or estimates sizes`() {
        val details = Formats.parse(youtube)
        fun f(id: String) = details.formats.single { it.id == id }
        assertEquals(178_800_000L, f("137").size(details.durationSec))
        assertFalse(f("137").isSizeEstimate())
        assertTrue(f("251").isSizeEstimate())
        // 1500 kbit/s × 596 s = 111.75 MB, worked out from the bitrate.
        assertEquals(111_750_000L, f("248").size(details.durationSec))
        assertTrue(f("248").isSizeEstimate())
    }

    @Test
    fun `picks the sound for a picture-only stream`() {
        val details = Formats.parse(youtube)
        val video = details.formats.single { it.id == "248" }
        // Stereo only (258 is 5.1), AAC for MP4, the best otherwise.
        assertEquals("140", details.bestAudioFor(video, preferAac = true)?.id)
        assertEquals("251", details.bestAudioFor(video, preferAac = false)?.id)
    }

    @Test
    fun `builds selector and label of a pick`() {
        val details = Formats.parse(youtube)
        fun f(id: String) = details.formats.single { it.id == id }
        val kbit = { n: Int -> "$n kbit/s" }
        assertEquals("299+251", Formats.selector(f("299"), f("251")))
        assertEquals("18", Formats.selector(f("18"), f("140")))
        assertEquals("140", Formats.selector(null, f("140")))
        assertNull(Formats.selector(null, null))
        assertEquals("1080p60 · H.264 + Opus", Formats.label(f("299"), f("251"), kbit))
        assertEquals("360p · H.264 + AAC", Formats.label(f("18"), null, kbit))
        assertEquals("Opus · 135 kbit/s", Formats.label(null, f("251"), kbit))
    }

    @Test
    fun `takes formats without codec details as picture with sound`() {
        // Instagram-like: progressive files without codecs, plus DASH streams with them.
        val details = Formats.parse(
            """
            {"title": "Reel", "duration": 12.5, "formats": [
              {"format_id": "1", "ext": "mp4", "width": 720, "height": 1280},
              {"format_id": "dash-v", "ext": "mp4", "vcodec": "avc1.4d401f", "acodec": "none", "width": 720, "height": 1280, "tbr": 1200},
              {"format_id": "dash-a", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "abr": 96}
            ]}
            """.trimIndent(),
        )
        val progressive = details.formats.single { it.id == "1" }
        assertTrue(progressive.isMuxed)
        assertNull(progressive.videoCodecName)
        assertEquals("720p", progressive.resolutionLabel)
        assertTrue(details.formats.single { it.id == "dash-v" }.isVideoOnly)
        assertTrue(details.formats.single { it.id == "dash-a" }.isAudioOnly)
    }

    @Test
    fun `reads sound-only streams without codecs from yt-dlp's own hints`() {
        // YouTube's 773 (5.1 sound) comes without vcodec and acodec.
        val details = Formats.parse(
            """
            {"title": "x", "duration": 10, "formats": [
              {"format_id": "773", "ext": "m4a", "resolution": "audio only", "audio_ext": "m4a", "video_ext": "none", "audio_channels": 6, "format_note": "medium"}
            ]}
            """.trimIndent(),
        )
        assertTrue(details.formats.single().isAudioOnly)
    }

    @Test
    fun `builds yt-dlp arguments for containers and picked streams`() {
        val webm = YtDlpArgs.download(DownloadOptions(kind = MediaKind.VIDEO, videoHeight = 1080, videoFormat = VideoFormat.WEBM), "/x", false)
        assertEquals("res:1080,vcodec:vp9,acodec:opus", webm[webm.indexOf("-S") + 1])
        assertEquals("webm/mkv", webm[webm.indexOf("--merge-output-format") + 1])
        val mkv = YtDlpArgs.download(DownloadOptions(kind = MediaKind.VIDEO, videoHeight = 720, videoFormat = VideoFormat.MKV), "/x", false)
        assertEquals("res:720", mkv[mkv.indexOf("-S") + 1])

        val pickedVideo = YtDlpArgs.download(DownloadOptions(kind = MediaKind.VIDEO, customFormat = "299+251"), "/x", false)
        assertEquals("299+251", pickedVideo[pickedVideo.indexOf("-f") + 1])
        assertFalse("-S" in pickedVideo)
        assertEquals("mp4", pickedVideo[pickedVideo.indexOf("--merge-output-format") + 1])

        val pickedAudio = YtDlpArgs.download(DownloadOptions(kind = MediaKind.AUDIO, customFormat = "251"), "/x", false)
        assertEquals("251", pickedAudio[pickedAudio.indexOf("-f") + 1])
        assertTrue("-x" in pickedAudio)
        assertFalse("--audio-format" in pickedAudio)

        // A picked stream never becomes the next download's default.
        assertNull(DownloadOptions(customFormat = "140", customLabel = "AAC").asDefaults().customFormat)
    }

    @Test
    fun `recognises other video sites and their share links`() {
        assertTrue(Links.isKnownSite("https://www.instagram.com/reel/C1a2B3c4D5e/?igsh=MWQ1ZGUxMzBkMA=="))
        assertTrue(Links.isKnownSite("https://vm.tiktok.com/ZMabcdef/"))
        assertTrue(Links.isKnownSite("https://vimeo.com/76979871"))
        assertTrue(Links.isKnownSite("https://on.soundcloud.com/abc"))
        assertTrue(Links.isKnownSite("https://www.ardmediathek.de/video/xyz"))
        assertTrue(Links.isKnownSite("https://youtu.be/jNQXAC9IVRw"))
        assertFalse(Links.isKnownSite("https://example.com/video.mp4"))
        assertFalse(Links.isKnownSite("https://notinstagram.com/reel/x"))
        assertEquals("instagram.com", Links.siteName("https://www.instagram.com/reel/x/"))

        assertEquals("https://www.instagram.com/reel/C1a2B3c4D5e/", Links.clean("https://www.instagram.com/reel/C1a2B3c4D5e/?igsh=MWQ1ZGUxMzBkMA=="))
        assertEquals("https://www.tiktok.com/@user/video/123", Links.clean("https://www.tiktok.com/@user/video/123?is_from_webapp=1&sender_device=pc&_r=1&_t=8abc"))
        assertEquals("https://x.com/user/status/123", Links.clean("https://x.com/user/status/123?s=20&t=AbCdEf"))
        assertEquals("https://www.facebook.com/watch/?v=123", Links.clean("https://www.facebook.com/watch/?v=123&mibextid=abc&rdid=xyz"))
        // Elsewhere "t" may be a start time and stays.
        assertEquals("https://vimeo.com/76979871?t=30", Links.clean("https://vimeo.com/76979871?t=30&utm_source=share"))
    }

    @Test
    fun `knows the big sites by their links, short links and subdomains included`() {
        assertEquals(Site.YOUTUBE, Site.of("https://www.youtube.com/watch?v=jNQXAC9IVRw"))
        assertEquals(Site.YOUTUBE, Site.of("https://youtu.be/jNQXAC9IVRw"))
        assertEquals(Site.YOUTUBE, Site.of("https://music.youtube.com/watch?v=x"))
        assertEquals(Site.INSTAGRAM, Site.of("https://www.instagram.com/reel/C1a2B3c4D5e/"))
        assertEquals(Site.TIKTOK, Site.of("https://vm.tiktok.com/ZMabcdef/"))
        assertEquals(Site.X, Site.of("https://twitter.com/user/status/1"))
        assertEquals(Site.X, Site.of("https://x.com/user/status/1"))
        assertEquals(Site.FACEBOOK, Site.of("https://fb.watch/abc/"))
        assertEquals(Site.REDDIT, Site.of("https://v.redd.it/abc"))
        assertEquals(Site.SOUNDCLOUD, Site.of("https://on.soundcloud.com/abc"))
        assertEquals(Site.BANDCAMP, Site.of("https://artist.bandcamp.com/track/song"))
        assertEquals(Site.DAILYMOTION, Site.of("https://dai.ly/x5kesuj"))
        assertNull(Site.of("https://www.ardmediathek.de/video/x"))
        assertNull(Site.of("https://notyoutube.com/watch?v=x"))
        assertNull(Site.of("not a link"))
        assertNull(Site.of(null))
    }

    @Test
    fun `tells a login wall from YouTube's own sign-in messages`() {
        fun kind(stderr: String) = Errors.classify(stderr).kind
        assertEquals(
            ErrorKind.LOGIN_REQUIRED,
            kind("ERROR: [Instagram] C1a2B3c4D5e: Requested content is not available, rate-limit reached or login required. Use --cookies, --cookies-from-browser, --username and --password, --netrc-cmd, or --netrc (instagram) to provide account credentials"),
        )
        assertEquals(ErrorKind.LOGIN_REQUIRED, kind("ERROR: [facebook] 123: This video is only available for registered users. Use --cookies for the authentication."))
        assertEquals(ErrorKind.BOT_CHECK, kind("ERROR: [youtube] abc: Sign in to confirm you’re not a bot. Use --cookies-from-browser or --cookies for the authentication."))
        assertEquals(ErrorKind.AGE_RESTRICTED, kind("ERROR: [youtube] abc: Sign in to confirm your age. This video may be inappropriate for some users. Use --cookies-from-browser or --cookies for the authentication."))
    }
}
