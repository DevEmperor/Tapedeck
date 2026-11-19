package net.devemperor.tapedeck.engine

import net.devemperor.tapedeck.data.MediaKind

enum class Phase { STARTING, PREPARING, DOWNLOADING, PROCESSING }

/** What the progress bar shows for a running download. */
data class LiveProgress(
    val phase: Phase = Phase.STARTING,
    /** 0..1 over the whole download, null while it cannot be known. */
    val fraction: Float? = null,
    val speedBytesPerSec: Long? = null,
    val etaSec: Long? = null,
    val totalBytes: Long? = null,
    /** Which of the separately downloaded streams is coming in (1-based; picture first, then sound). */
    val part: Int = 0,
    val parts: Int = 1,
    /** 0..1 of that stream alone. */
    val partFraction: Float? = null,
)

/** One parsed "[download]  42.0% of ~ 12.34MiB at 1.20MiB/s ETA 00:09" line. */
data class DownloadLine(val percent: Float, val totalBytes: Long?, val speedBytesPerSec: Long?, val etaSec: Long?)

object YtDlpOutput {
    private val percent = Regex("""^\[download]\s+([\d.]+)%""")
    private val total = Regex("""\sof\s+~?\s*([\d.]+)\s*([KMGT]?i?B)\b""")
    private val speed = Regex("""\sat\s+([\d.]+)\s*([KMGT]?i?B)/s""")
    private val eta = Regex("""\sETA\s+(\d+(?::\d+)+)""")
    // "[info] x: Downloading 1 format(s): 137+140" — one format made of two streams (picture + sound).
    private val formats = Regex("""^\[info] .*: Downloading (\d+) format\(s\)(?::\s*(\S+))?""")
    private val postProcessors = listOf(
        "[ExtractAudio]", "[Merger]", "[VideoConvertor]", "[VideoRemuxer]", "[FixupM4a]", "[FixupM3u8]",
        "[FixupDuplicateMoov]", "[FixupTimestamp]", "[SponsorBlock]", "[ModifyChapters]", "[EmbedThumbnail]",
        "[Metadata]", "[ThumbnailsConvertor]",
    )

    fun parseDownload(line: String): DownloadLine? {
        val pct = percent.find(line)?.groupValues?.get(1)?.toFloatOrNull() ?: return null
        return DownloadLine(
            percent = pct,
            totalBytes = total.find(line)?.let { bytes(it.groupValues[1], it.groupValues[2]) },
            speedBytesPerSec = speed.find(line)?.let { bytes(it.groupValues[1], it.groupValues[2]) },
            etaSec = eta.find(line)?.let { seconds(it.groupValues[1]) },
        )
    }

    /** How many streams yt-dlp is about to download one after the other: the parts of "137+140", else the count. */
    fun streamCount(line: String): Int? {
        val match = formats.find(line) ?: return null
        val selector = match.groupValues[2]
        return if (selector.isNotEmpty()) selector.split('+').size else match.groupValues[1].toIntOrNull()
    }

    fun isDestination(line: String) = line.startsWith("[download] Destination:")

    fun isPostProcessing(line: String) = postProcessors.any { line.startsWith(it) }

    fun isPreparing(line: String) = line.startsWith("[youtube") || line.startsWith("[info]") || line.startsWith("[generic]")

    fun isInfoJsonWritten(line: String) = line.startsWith("[info] Writing video metadata as JSON")

    fun bytes(number: String, unit: String): Long? {
        val value = number.toDoubleOrNull() ?: return null
        val factor = when (unit) {
            "B" -> 1.0
            "KiB" -> 1024.0
            "MiB" -> 1024.0 * 1024
            "GiB" -> 1024.0 * 1024 * 1024
            "TiB" -> 1024.0 * 1024 * 1024 * 1024
            "KB" -> 1e3
            "MB" -> 1e6
            "GB" -> 1e9
            "TB" -> 1e12
            else -> return null
        }
        return (value * factor).toLong()
    }

    /** "05", "01:05" or "1:02:05" → seconds. */
    fun seconds(clock: String): Long? =
        clock.split(':').fold(0L) { acc, part -> acc * 60 + (part.toLongOrNull() ?: return null) }
}

/**
 * Turns yt-dlp's output into one steadily rising progress value. A video download fetches the
 * picture first and the sound second, each counting from 0 to 100 %; weighting them by their sizes
 * keeps the bar from jumping back or standing at 100 % while the sound still comes in.
 */
class ProgressTracker(private val kind: MediaKind) {
    private var streams = 1
    private var part = 0
    private var state = LiveProgress()

    /** Sizes of the streams from yt-dlp's info file (written before the download), once known. */
    @Volatile
    private var sizes: List<Long>? = null

    val current: LiveProgress get() = state

    fun setStreamSizes(list: List<Long?>) {
        if (list.size > 1 && list.all { it != null && it > 0 }) sizes = list.map { it!! }
    }

    /** Returns true when the visible state changed. */
    fun onLine(line: String): Boolean {
        val before = state
        YtDlpOutput.streamCount(line)?.let { streams = it.coerceAtLeast(1) }
        when {
            YtDlpOutput.isDestination(line) -> {
                part++
                state = state.copy(
                    phase = Phase.DOWNLOADING, speedBytesPerSec = null, etaSec = null,
                    part = part.coerceAtMost(streams), parts = streams, partFraction = 0f,
                )
            }
            line.startsWith("[download]") -> YtDlpOutput.parseDownload(line)?.let { parsed ->
                val partFraction = parsed.percent / 100f
                state = state.copy(
                    phase = Phase.DOWNLOADING,
                    fraction = maxOf(overall(partFraction), state.fraction ?: 0f),
                    speedBytesPerSec = parsed.speedBytesPerSec ?: state.speedBytesPerSec,
                    etaSec = parsed.etaSec,
                    totalBytes = parsed.totalBytes ?: state.totalBytes,
                    part = part.coerceIn(1, streams),
                    parts = streams,
                    partFraction = partFraction,
                )
            }
            YtDlpOutput.isPostProcessing(line) ->
                state = state.copy(phase = Phase.PROCESSING, fraction = null, speedBytesPerSec = null, etaSec = null)
            YtDlpOutput.isPreparing(line) && state.phase == Phase.STARTING ->
                state = state.copy(phase = Phase.PREPARING)
        }
        return state != before
    }

    private fun overall(partFraction: Float): Float {
        if (streams <= 1) return partFraction
        val index = (part - 1).coerceIn(0, streams - 1)
        val known = sizes?.takeIf { it.size == streams }
        val weights = when {
            known != null -> known.map { it.toFloat() / known.sum() }
            // Without sizes: for video+audio the picture is by far the bigger part.
            kind == MediaKind.VIDEO && streams == 2 -> listOf(0.85f, 0.15f)
            else -> List(streams) { 1f / streams }
        }
        return weights.take(index).sum() + weights[index] * partFraction
    }
}
