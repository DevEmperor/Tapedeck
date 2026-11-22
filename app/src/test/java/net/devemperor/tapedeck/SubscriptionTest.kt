package net.devemperor.tapedeck

import net.devemperor.tapedeck.data.DownloadState
import net.devemperor.tapedeck.data.Entry
import net.devemperor.tapedeck.data.Mixtape
import net.devemperor.tapedeck.data.Subscription
import net.devemperor.tapedeck.data.SubscriptionPlan
import net.devemperor.tapedeck.engine.Links
import net.devemperor.tapedeck.engine.PlaylistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionTest {
    private fun item(id: String, duration: Long = 60_000) = PlaylistItem(Links.videoUrl(id), id, "Channel", duration, null)
    private val ids = listOf("aaaaaaaaaaa", "bbbbbbbbbbb", "ccccccccccc", "ddddddddddd", "eeeeeeeeeee")

    @Test
    fun channelLinksPointToTheirVideos() {
        assertEquals("https://www.youtube.com/@blender/videos", Links.channelVideosUrl("https://www.youtube.com/@blender"))
        assertEquals("https://www.youtube.com/@blender/videos", Links.channelVideosUrl("https://m.youtube.com/@blender/streams?si=x"))
        assertEquals("https://www.youtube.com/channel/UC1234/videos", Links.channelVideosUrl("https://www.youtube.com/channel/UC1234/featured"))
        assertEquals("https://www.youtube.com/c/Name/videos", Links.channelVideosUrl("https://youtube.com/c/Name"))
        assertNull(Links.channelVideosUrl("https://www.youtube.com/watch?v=aaaaaaaaaaa"))
        assertNull(Links.channelVideosUrl("https://www.youtube.com/playlist?list=PL123"))
        assertNull(Links.channelVideosUrl("https://youtu.be/aaaaaaaaaaa"))
        assertNull(Links.channelVideosUrl("https://vimeo.com/@someone"))
    }

    @Test
    fun aChannelTakesItsNewestUnseenVideosUpToTheLimit() {
        // Newest first, as yt-dlp lists a channel; the first two are known already.
        val subscription = Subscription(url = "c", keep = 2, known = listOf(ids[3], ids[4]))
        val look = SubscriptionPlan.look(subscription, listOf(ids[0], ids[1], ids[2], ids[3], ids[4]).map { item(it) })
        assertEquals(listOf(ids[0], ids[1]), look.download.map { Links.videoId(it.url) })
        // The third new one is passed over as older, but counts as seen.
        assertTrue(ids.all { it in look.known })
    }

    @Test
    fun upcomingVideosWaitForALaterLook() {
        val subscription = Subscription(url = "c", keep = 5)
        val look = SubscriptionPlan.look(subscription, listOf(item(ids[0], duration = 0), item(ids[1])))
        assertEquals(listOf(ids[1]), look.download.map { Links.videoId(it.url) })
        assertTrue(ids[0] !in look.known)
    }

    @Test
    fun aPlaylistTakesItsNewVideosFromTheEndAndKeepsAllByDefault() {
        val all = Subscription(url = "p", playlist = true, keep = 0, known = listOf(ids[0]))
        assertEquals(ids.drop(1), SubscriptionPlan.look(all, ids.map { item(it) }).download.map { Links.videoId(it.url) })
        val two = all.copy(keep = 2)
        assertEquals(listOf(ids[3], ids[4]), SubscriptionPlan.look(two, ids.map { item(it) }).download.map { Links.videoId(it.url) })
    }

    @Test
    fun subscribingCanLoadNothingNowAndStillRemembersEverything() {
        val look = SubscriptionPlan.look(Subscription(url = "c"), ids.map { item(it) }, take = 0)
        assertTrue(look.download.isEmpty())
        assertEquals(ids, look.known)
    }

    @Test
    fun theOldestFinishedDownloadsMakeRoomButKeptAndPlayingOnesStay() {
        fun entry(id: String, created: Long, keep: Boolean = false, done: Boolean = true) = Entry(
            id = id, url = "u$id", title = id, createdAt = created, subscription = "tape", keep = keep,
            state = if (done) DownloadState.DONE else DownloadState.RUNNING, mediaFile = if (done) "/m/$id" else null,
        )
        val entries = listOf(
            entry("new", 5), entry("running", 6, done = false), entry("mid", 4), entry("kept", 1, keep = true),
            entry("old", 2), entry("oldest", 0), entry("playing", 3),
        )
        val tape = Mixtape(
            id = "tape", name = "Channel", createdAt = 0, entryIds = entries.map { it.id } - "oldest",
            subscription = Subscription(url = "c", keep = 2),
        )
        // Finished and on the tape, newest first: new, mid, playing, old → beyond 2: playing (stays), old.
        // "oldest" was taken off the mixtape by hand, "kept" is kept, "running" is not finished yet.
        assertEquals(listOf("old"), SubscriptionPlan.surplus(tape, entries, playing = "playing").map { it.id })
        assertTrue(SubscriptionPlan.surplus(tape.copy(subscription = tape.subscription!!.copy(keep = 0)), entries, null).isEmpty())
    }
}
