package net.devemperor.tapedeck

import net.devemperor.tapedeck.data.MediaKind
import net.devemperor.tapedeck.engine.Phase
import net.devemperor.tapedeck.engine.ProgressTracker
import net.devemperor.tapedeck.engine.YtDlpOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {
    @Test
    fun `parses a regular progress line`() {
        val line = YtDlpOutput.parseDownload("[download]  42.3% of   12.50MiB at    1.25MiB/s ETA 00:09")
        assertNotNull(line)
        line!!
        assertEquals(42.3f, line.percent, 0.001f)
        assertEquals((12.5 * 1024 * 1024).toLong(), line.totalBytes)
        assertEquals((1.25 * 1024 * 1024).toLong(), line.speedBytesPerSec)
        assertEquals(9L, line.etaSec)
    }

    @Test
    fun `copes with estimates, unknown speed and hours`() {
        val estimate = YtDlpOutput.parseDownload("[download]   3.0% of ~  1.20GiB at  Unknown B/s ETA Unknown (frag 2/80)")!!
        assertEquals(3f, estimate.percent, 0.001f)
        assertEquals((1.2 * 1024 * 1024 * 1024).toLong(), estimate.totalBytes)
        assertNull(estimate.speedBytesPerSec)
        assertNull(estimate.etaSec)
        assertEquals(3723L, YtDlpOutput.parseDownload("[download]  10.0% of 900.00MiB at 100.00KiB/s ETA 01:02:03")!!.etaSec)
        assertNull(YtDlpOutput.parseDownload("[download] Destination: /x/media.m4a"))
    }

    @Test
    fun `audio download runs from preparing over downloading to converting`() {
        val tracker = ProgressTracker(MediaKind.AUDIO)
        tracker.onLine("[youtube] Extracting URL: https://www.youtube.com/watch?v=x")
        assertEquals(Phase.PREPARING, tracker.current.phase)
        tracker.onLine("[info] x: Downloading 1 format(s): 140")
        tracker.onLine("[download] Destination: /data/media.m4a")
        tracker.onLine("[download]  50.0% of 10.00MiB at 1.00MiB/s ETA 00:05")
        assertEquals(Phase.DOWNLOADING, tracker.current.phase)
        assertEquals(0.5f, tracker.current.fraction!!, 0.001f)
        tracker.onLine("[ExtractAudio] Not converting audio /data/media.m4a; file is already in target format m4a")
        assertEquals(Phase.PROCESSING, tracker.current.phase)
        assertNull(tracker.current.fraction)
    }

    @Test
    fun `video progress never jumps back when the sound track starts`() {
        val tracker = ProgressTracker(MediaKind.VIDEO)
        tracker.onLine("[info] x: Downloading 2 format(s): 136+140")
        tracker.onLine("[download] Destination: /data/media.f136.mp4")
        tracker.onLine("[download] 100.0% of 50.00MiB at 5.00MiB/s ETA 00:00")
        val afterVideo = tracker.current.fraction!!
        assertEquals(0.85f, afterVideo, 0.001f)
        tracker.onLine("[download] Destination: /data/media.f140.m4a")
        tracker.onLine("[download]  10.0% of 5.00MiB at 5.00MiB/s ETA 00:01")
        val duringAudio = tracker.current.fraction!!
        assertTrue(duringAudio >= afterVideo)
        tracker.onLine("[download] 100.0% of 5.00MiB at 5.00MiB/s ETA 00:00")
        assertEquals(1f, tracker.current.fraction!!, 0.001f)
        tracker.onLine("[Merger] Merging formats into \"/data/media.mp4\"")
        assertEquals(Phase.PROCESSING, tracker.current.phase)
    }
}
