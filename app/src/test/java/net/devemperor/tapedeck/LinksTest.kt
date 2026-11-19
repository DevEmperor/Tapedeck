package net.devemperor.tapedeck

import net.devemperor.tapedeck.engine.Links
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    @Test
    fun `finds the link in YouTube's share text`() {
        assertEquals("https://youtu.be/dQw4w9WgXcQ?si=AbCdEf123", Links.extractUrl("Schau mal: https://youtu.be/dQw4w9WgXcQ?si=AbCdEf123"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", Links.extractUrl("(https://www.youtube.com/watch?v=dQw4w9WgXcQ)."))
        assertNull(Links.extractUrl("no link here"))
        assertNull(Links.extractUrl(null))
    }

    @Test
    fun `reads the video id from every YouTube link shape`() {
        val id = "dQw4w9WgXcQ"
        listOf(
            "https://youtu.be/$id?si=xyz",
            "https://www.youtube.com/watch?v=$id&t=42",
            "https://m.youtube.com/watch?v=$id",
            "https://music.youtube.com/watch?v=$id&list=OLAK5uy_abc",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/live/$id?feature=share",
            "https://www.youtube.com/embed/$id",
        ).forEach { assertEquals(it, id, Links.videoId(it)) }
        assertNull(Links.videoId("https://www.youtube.com/@channel"))
        assertNull(Links.videoId("https://vimeo.com/22439234"))
    }

    @Test
    fun `cleans tracking parameters and keeps time and playlist`() {
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", Links.clean("https://youtu.be/dQw4w9WgXcQ?si=AbCdEf123"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42", Links.clean("https://youtu.be/dQw4w9WgXcQ?si=x&t=42"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123", Links.clean("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123&pp=abc"))
        assertEquals("https://www.youtube.com/playlist?list=PL123", Links.clean("https://youtube.com/playlist?list=PL123&si=x"))
        assertEquals("https://vimeo.com/22439234?a=1", Links.clean("https://vimeo.com/22439234?a=1&utm_source=x&fbclid=y"))
    }

    @Test
    fun `offers whole playlists but not endless mixes`() {
        assertEquals("PL123", Links.playlistId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123"))
        assertNull(Links.playlistId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ"))
        assertNull(Links.playlistId("https://www.youtube.com/playlist?list=WL"))
        assertTrue(Links.isPlaylistOnly("https://www.youtube.com/playlist?list=PL123"))
        assertFalse(Links.isPlaylistOnly("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123"))
    }

    @Test
    fun `builds the thumbnail address from the id`() {
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", Links.thumbnailFor("https://youtu.be/dQw4w9WgXcQ"))
        assertNull(Links.thumbnailFor("https://vimeo.com/22439234"))
    }
}
