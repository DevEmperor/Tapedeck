package net.devemperor.tapedeck

import net.devemperor.tapedeck.data.AppSettings
import net.devemperor.tapedeck.data.AutoDelete
import net.devemperor.tapedeck.data.DownloadState
import net.devemperor.tapedeck.data.Entry
import net.devemperor.tapedeck.data.Mixtape
import net.devemperor.tapedeck.player.MediaItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MixtapeTest {
    private fun entry(id: String, done: Boolean = true, finishedAt: Long? = null) = Entry(
        id = id,
        url = "https://youtu.be/$id",
        title = id,
        state = if (done) DownloadState.DONE else DownloadState.RUNNING,
        mediaFile = if (done) "/x/$id.m4a" else null,
        createdAt = 0,
        finished = finishedAt != null,
        finishedAt = finishedAt,
    )

    @Test
    fun `plays a mixtape in its own order, skipping what is not there yet`() {
        val entries = listOf(entry("a"), entry("b"), entry("c", done = false), entry("d"))
        val tape = Mixtape(id = "m", name = "Roadtrip", entryIds = listOf("d", "c", "gone", "a"), createdAt = 0)
        assertEquals(listOf("d", "a"), MediaItems.mixtapeQueue(tape, entries).map { it.id })
    }

    @Test
    fun `finds the song behind a car browse id`() {
        assertEquals("abc", MediaItems.entryId("abc"))
        assertEquals("abc", MediaItems.entryId("tapedeck:in:m1/abc"))
    }

    @Test
    fun `never cleans up songs on a mixtape`() {
        val day = 24 * 60 * 60 * 1000L
        val heard = entry("a", finishedAt = 0)
        val settings = AppSettings(autoDeleteEnabled = true, autoDeleteDays = 1)
        assertNotNull(AutoDelete.dueAt(heard, settings))
        assertNull(AutoDelete.dueAt(heard, settings, onMixtapes = setOf("a")))
        assertEquals(emptyList<Entry>(), AutoDelete.victims(listOf(heard), settings, now = 10 * day, playingId = null, onMixtapes = setOf("a")))
    }
}
