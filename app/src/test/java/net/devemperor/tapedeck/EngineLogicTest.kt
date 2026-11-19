package net.devemperor.tapedeck

import net.devemperor.tapedeck.data.AppSettings
import net.devemperor.tapedeck.data.AudioFormat
import net.devemperor.tapedeck.data.AudioQuality
import net.devemperor.tapedeck.data.AutoDelete
import net.devemperor.tapedeck.data.DownloadOptions
import net.devemperor.tapedeck.data.DownloadState
import net.devemperor.tapedeck.data.Entry
import net.devemperor.tapedeck.data.MediaKind
import net.devemperor.tapedeck.engine.ErrorKind
import net.devemperor.tapedeck.engine.Errors
import net.devemperor.tapedeck.engine.InfoJson
import net.devemperor.tapedeck.engine.YtDlpArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class EngineLogicTest {
    @Test
    fun `classifies yt-dlp errors`() {
        fun kind(stderr: String) = Errors.classify(stderr).kind
        assertEquals(ErrorKind.UNAVAILABLE, kind("ERROR: [youtube] abc: Video unavailable. This video has been removed by the uploader"))
        assertEquals(ErrorKind.PRIVATE, kind("ERROR: [youtube] abc: Private video. Sign in if you've been granted access"))
        assertEquals(ErrorKind.AGE_RESTRICTED, kind("ERROR: [youtube] abc: Sign in to confirm your age. This video may be inappropriate"))
        assertEquals(ErrorKind.BOT_CHECK, kind("ERROR: [youtube] abc: Sign in to confirm you’re not a bot."))
        assertEquals(ErrorKind.FORMAT, kind("ERROR: [youtube] abc: Requested format is not available. Use --list-formats"))
        assertEquals(ErrorKind.BLOCKED, kind("ERROR: unable to download video data: HTTP Error 403: Forbidden"))
        assertEquals(ErrorKind.NETWORK, kind("ERROR: [youtube] abc: Unable to download webpage: <urlopen error [Errno 7] No address associated with hostname>"))
        assertEquals(ErrorKind.UNSUPPORTED_URL, kind("ERROR: Unsupported URL: https://example.com/"))
        assertEquals(ErrorKind.EXTRACTOR, kind("ERROR: [youtube] abc: Unable to extract nsig function code; please report this issue"))
        assertEquals(ErrorKind.STORAGE, kind("ERROR: unable to write data: [Errno 28] No space left on device"))
        assertEquals("Video unavailable", Errors.classify("WARNING: x\nERROR: Video unavailable").detail)
        assertTrue(Errors.classify("ERROR: [youtube] abc: Unable to extract player response").mayBeFixedByUpdate)
        assertFalse(Errors.classify("ERROR: Private video").mayBeFixedByUpdate)
    }

    @Test
    fun `reads what Tapedeck needs from the info JSON`() {
        val info = InfoJson.parse(
            """{"id":"abc","title":"Römerbrief – Vorlesung 3","uploader":"FTH Gießen","duration":3725.4,
               "height":null,"abr":129.47,"acodec":"mp4a.40.2","webpage_url":"https://www.youtube.com/watch?v=abc",
               "chapters":[{"start_time":0.0,"end_time":60.0,"title":"Intro"},{"start_time":60.0,"title":"Kapitel 1"}],
               "formats":[{"format_id":"140"}]}""",
        )
        assertEquals("Römerbrief – Vorlesung 3", info.title)
        assertEquals("FTH Gießen", info.uploader)
        assertEquals(3_725_400L, info.durationMs)
        assertNull(info.height)
        assertEquals(129, info.audioBitrate)
        assertEquals(listOf(0L, 60_000L), info.chapters.map { it.startMs })
    }

    @Test
    fun `reads a flat playlist`() {
        val playlist = InfoJson.parsePlaylist(
            """{"_type":"playlist","title":"Römer","entries":[
                {"_type":"url","id":"aaaaaaaaaaa","url":"https://www.youtube.com/watch?v=aaaaaaaaaaa","title":"Teil 1","duration":600,"channel":"FTH"},
                {"_type":"url","id":"bbbbbbbbbbb","title":"Teil 2"}]}""",
        )
        assertEquals("Römer", playlist.title)
        assertEquals(listOf("https://www.youtube.com/watch?v=aaaaaaaaaaa", "https://www.youtube.com/watch?v=bbbbbbbbbbb"), playlist.items.map { it.url })
        assertEquals(600_000L, playlist.items[0].durationMs)
        assertEquals("https://i.ytimg.com/vi/bbbbbbbbbbb/hqdefault.jpg", playlist.items[1].thumbnailUrl)
    }

    @Test
    fun `builds the yt-dlp command line`() {
        val m4a = YtDlpArgs.download(DownloadOptions(MediaKind.AUDIO, AudioFormat.M4A, AudioQuality.HIGH), "/d", sponsorBlock = false)
        assertTrue(m4a[m4a.indexOf("-f") + 1].startsWith("bestaudio[ext=m4a][audio_channels<=2][abr<=160]/"))
        assertEquals("m4a", m4a[m4a.indexOf("--audio-format") + 1])
        assertTrue("/d/media.%(ext)s" in m4a)
        assertFalse("--sponsorblock-remove" in m4a)

        val original = YtDlpArgs.download(DownloadOptions(MediaKind.AUDIO, AudioFormat.ORIGINAL, AudioQuality.HIGH), "/d", sponsorBlock = true)
        assertFalse("--audio-format" in original)
        assertTrue("--sponsorblock-remove" in original)

        val video = YtDlpArgs.download(DownloadOptions(MediaKind.VIDEO, videoHeight = 480), "/d", sponsorBlock = false)
        assertEquals("res:480,vcodec:h264,acodec:m4a", video[video.indexOf("-S") + 1])
        assertFalse("-x" in video)
    }

    @Test
    fun `auto-delete counts from download or from the end of listening`() {
        val day = TimeUnit.DAYS.toMillis(1)
        val now = 100 * day
        val heard = entry("a", completedAt = now - 10 * day, finished = true, finishedAt = now - 4 * day)
        val unheard = entry("b", completedAt = now - 10 * day)
        val kept = entry("c", completedAt = now - 10 * day, finished = true, finishedAt = now - 9 * day, keep = true)
        val playing = entry("d", completedAt = now - 10 * day, finished = true, finishedAt = now - 9 * day)
        val all = listOf(heard, unheard, kept, playing)

        val onlyHeard = AppSettings(autoDeleteDays = 3, autoDeleteOnlyFinished = true)
        assertEquals(listOf("a"), AutoDelete.victims(all, onlyHeard, now, playingId = "d").map { it.id })

        val everything = AppSettings(autoDeleteDays = 7, autoDeleteOnlyFinished = false)
        assertEquals(listOf("a", "b", "d"), AutoDelete.victims(all, everything, now, playingId = null).map { it.id })

        assertNull(AutoDelete.dueAt(heard, AppSettings(autoDeleteDays = 0)))
    }

    private fun entry(id: String, completedAt: Long, finished: Boolean = false, finishedAt: Long? = null, keep: Boolean = false) = Entry(
        id = id, url = "u", title = id, state = DownloadState.DONE, mediaFile = "/m/$id.m4a", createdAt = completedAt,
        completedAt = completedAt, finished = finished, finishedAt = finishedAt, keep = keep,
    )
}
