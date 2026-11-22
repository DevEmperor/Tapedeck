package net.devemperor.tapedeck

import net.devemperor.tapedeck.data.AppSettings
import net.devemperor.tapedeck.data.DownloadOptions
import net.devemperor.tapedeck.data.MediaKind
import net.devemperor.tapedeck.engine.YtDlpArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTest {
    private val settings = AppSettings(subtitleLangs = listOf("de", "en"))

    @Test
    fun aVideoTakesTheSettingsUnlessItChoseItsOwn() {
        val video = DownloadOptions(kind = MediaKind.VIDEO)
        assertEquals(listOf("de", "en"), video.subtitleLanguages(settings))
        assertEquals(listOf("fr"), video.copy(subtitles = listOf("fr")).subtitleLanguages(settings))
        assertTrue(video.copy(subtitles = emptyList()).subtitleLanguages(settings).isEmpty())
        // Audio has no picture to put subtitles on.
        assertTrue(DownloadOptions(kind = MediaKind.AUDIO).subtitleLanguages(settings).isEmpty())
    }

    @Test
    fun aVideosOwnChoiceIsNotKeptAsTheNextDefault() {
        assertEquals(null, DownloadOptions(kind = MediaKind.VIDEO, subtitles = listOf("fr")).asDefaults().subtitles)
    }

    @Test
    fun subtitlesAreEmbeddedWithRegionalVariantsAndWithoutLiveChat() {
        val args = YtDlpArgs.download(DownloadOptions(kind = MediaKind.VIDEO), "/out", sponsorBlock = false, subtitles = listOf("de", "en"), autoSubtitles = true)
        assertTrue("--write-subs" in args)
        assertTrue("--write-auto-subs" in args)
        assertTrue("--embed-subs" in args)
        // Translations of real subtitles are skipped by the extractor, failing ones do not cost the video.
        assertEquals("youtube:skip=translated_subs", args[args.indexOf("--extractor-args") + 1])
        assertTrue("--ignore-errors" in args)
        val takes = chosen(args)
        listOf("de", "de-DE", "en", "en-GB", "en-US").forEach { assertTrue(it, takes(it)) }
        listOf("en-orig", "de-orig", "fr", "live_chat").forEach { assertFalse(it, takes(it)) }
        val regional = chosen(YtDlpArgs.subtitleArgs(listOf("es", "zh", "pt"), automatic = false))
        listOf("es-419", "zh-Hans", "zh-Hant", "pt-BR").forEach { assertTrue(it, regional(it)) }
        val manualOnly = YtDlpArgs.download(DownloadOptions(kind = MediaKind.VIDEO), "/out", false, listOf("de"), autoSubtitles = false)
        assertFalse("--write-auto-subs" in manualOnly)
    }

    /** yt-dlp's orderedSet_from_options: patterns in order, fullmatch ignoring case, "-" removes again. */
    private fun chosen(args: List<String>): (String) -> Boolean {
        val patterns = args[args.indexOf("--sub-langs") + 1].split(",")
        return { name ->
            patterns.fold(false) { taken, pattern ->
                val discard = pattern.startsWith("-")
                val matches = Regex(pattern.removePrefix("-"), RegexOption.IGNORE_CASE).matches(name)
                if (matches) !discard else taken
            }
        }
    }

    @Test
    fun noSubtitleArgumentsForAudioOrWithoutLanguages() {
        val audio = YtDlpArgs.download(DownloadOptions(kind = MediaKind.AUDIO), "/out", false, listOf("de"), true)
        assertFalse(audio.any { it.contains("sub") })
        val none = YtDlpArgs.download(DownloadOptions(kind = MediaKind.VIDEO), "/out", false, emptyList(), true)
        assertFalse(none.any { it.contains("sub") })
    }
}
