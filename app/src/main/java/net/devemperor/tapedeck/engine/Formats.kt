package net.devemperor.tapedeck.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** One stream yt-dlp offers for a video — a line of `yt-dlp -F`. */
data class MediaFormat(
    val id: String,
    val ext: String,
    /** Null when the stream has no picture ("none" in yt-dlp's JSON). */
    val vcodec: String?,
    /** Null when the stream has no sound. */
    val acodec: String?,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    /** Total, video and audio bitrate in kbit/s. */
    val tbr: Double? = null,
    val vbr: Double? = null,
    val abr: Double? = null,
    val sizeBytes: Long? = null,
    val sizeIsEstimate: Boolean = false,
    val note: String? = null,
    val protocol: String? = null,
    val channels: Int? = null,
    val dynamicRange: String? = null,
    val language: String? = null,
) {
    val hasVideo get() = vcodec != null
    val hasAudio get() = acodec != null
    val isMuxed get() = hasVideo && hasAudio
    val isVideoOnly get() = hasVideo && !hasAudio
    val isAudioOnly get() = hasAudio && !hasVideo

    /** "1080p60 HDR"; the resolution class, so a 1920×800 film reads 1080p and a 720×1280 reel 720p. */
    val resolutionLabel: String?
        get() {
            val h = height ?: return null
            val equivalent = when {
                width != null && h > width -> width
                width != null && width * 9 > h * 16 -> width * 9 / 16
                else -> h
            }
            val standard = STANDARD_HEIGHTS.minBy { abs(it - equivalent) }
            val rate = fps?.roundToInt()?.takeIf { it > 30 }?.toString().orEmpty()
            val hdr = if (dynamicRange != null && !dynamicRange.equals("SDR", ignoreCase = true)) " HDR" else ""
            return "${standard}p$rate$hdr"
        }

    /** Bitrate of the sound in kbit/s (audio streams), else of the whole stream. */
    val bitrate: Int?
        get() = (if (isAudioOnly) abr ?: tbr else tbr ?: listOfNotNull(vbr, abr).sum().takeIf { it > 0 })?.roundToInt()?.takeIf { it > 0 }

    /** The size as yt-dlp knows it, or worked out from the bitrate and the video's length. */
    fun size(durationSec: Double): Long? {
        sizeBytes?.let { return it }
        val kbit = (if (isAudioOnly) abr ?: tbr else tbr ?: listOfNotNull(vbr, abr).sum().takeIf { it > 0 }) ?: return null
        if (durationSec <= 0) return null
        return (kbit * 125 * durationSec).toLong()
    }

    fun isSizeEstimate(): Boolean = sizeBytes == null || sizeIsEstimate

    val videoCodecName: String? get() = vcodec?.let(Formats::videoCodecName)
    val audioCodecName: String? get() = acodec?.let(Formats::audioCodecName)

    private companion object {
        val STANDARD_HEIGHTS = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)
    }
}

/** What the "new download" sheet needs to know about a video before it is downloaded. */
data class MediaDetails(
    val title: String?,
    val uploader: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val formats: List<MediaFormat>,
) {
    val durationSec get() = durationMs / 1000.0

    /** Streams with picture, best first: muxed and picture-only ones together. */
    val videos: List<MediaFormat>
        get() = formats.filter { it.hasVideo }.sortedWith(
            compareByDescending<MediaFormat> { it.height ?: 0 }.thenByDescending { it.fps ?: 0.0 }.thenByDescending { it.tbr ?: 0.0 },
        )

    /** Sound-only streams, best first. */
    val audios: List<MediaFormat>
        get() = formats.filter { it.isAudioOnly }.sortedWith(
            compareByDescending<MediaFormat> { it.abr ?: it.tbr ?: 0.0 }.thenBy { it.id },
        )

    /** The sound that goes with a picture-only stream by default: the best one, AAC for MP4 if there is one. */
    fun bestAudioFor(video: MediaFormat?, preferAac: Boolean): MediaFormat? {
        val candidates = audios.filter { it.channels == null || it.channels <= 2 }.ifEmpty { audios }
        if (video == null) return candidates.firstOrNull()
        return (if (preferAac) candidates.firstOrNull { it.acodec?.startsWith("mp4a") == true } else null) ?: candidates.firstOrNull()
    }
}

object Formats {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Reads `yt-dlp -J` output. Storyboards (picture strips for the seek bar) are left out. */
    fun parse(text: String): MediaDetails {
        val root = json.parseToJsonElement(text).jsonObject
        val formats = (root["formats"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::format) }
        return MediaDetails(
            title = root.string("title"),
            uploader = root.string("uploader") ?: root.string("channel") ?: root.string("artist") ?: root.string("uploader_id"),
            thumbnailUrl = root.string("thumbnail"),
            durationMs = ((root.double("duration") ?: 0.0) * 1000).toLong(),
            formats = formats,
        )
    }

    private fun format(o: JsonObject): MediaFormat? {
        val id = o.string("format_id") ?: return null
        val ext = o.string("ext") ?: "?"
        val note = o.string("format_note")
        val protocol = o.string("protocol")
        if (ext == "mhtml" || protocol == "mhtml" || note?.contains("storyboard", ignoreCase = true) == true) return null
        val rawV = o.string("vcodec")
        val rawA = o.string("acodec")
        val width = o.double("width")?.toInt()
        val height = o.double("height")?.toInt()
        // A missing codec means "unknown", not "none". yt-dlp still says what a stream holds in video_ext,
        // audio_ext and resolution ("audio only"; YouTube's 773 has no codecs at all). Without those, a
        // stream with a size is picture, usually with sound.
        val videoExt = o.string("video_ext")
        val audioExt = o.string("audio_ext")
        val video = when {
            rawV == "none" -> false
            rawV != null -> true
            videoExt == "none" || o.string("resolution") == "audio only" -> false
            videoExt != null -> true
            else -> width != null || height != null || rawA == null
        }
        val audio = when {
            rawA == "none" -> false
            rawA != null -> true
            audioExt == "none" -> false
            audioExt != null -> true
            else -> !video || rawV == null
        }
        if (!video && !audio) return null
        val size = o.long("filesize")
        val approx = o.long("filesize_approx")
        return MediaFormat(
            id = id,
            ext = ext,
            vcodec = if (video) rawV ?: "" else null,
            acodec = if (audio) rawA ?: "" else null,
            width = width,
            height = height,
            fps = o.double("fps"),
            tbr = o.double("tbr"),
            vbr = o.double("vbr"),
            abr = o.double("abr"),
            sizeBytes = size ?: approx,
            sizeIsEstimate = size == null && approx != null,
            note = note,
            protocol = protocol,
            channels = o.double("audio_channels")?.toInt(),
            dynamicRange = o.string("dynamic_range"),
            language = o.string("language"),
        )
    }

    fun videoCodecName(codec: String): String? {
        val c = codec.lowercase()
        return when {
            c.isEmpty() -> null
            c.startsWith("avc") || c.startsWith("h264") -> "H.264"
            c.startsWith("hev") || c.startsWith("hvc") || c.startsWith("h265") -> "H.265"
            c.startsWith("vp09") || c.startsWith("vp9") -> "VP9"
            c.startsWith("vp8") -> "VP8"
            c.startsWith("av01") || c.startsWith("av1") -> "AV1"
            else -> codec.substringBefore('.').uppercase()
        }
    }

    fun audioCodecName(codec: String): String? {
        val c = codec.lowercase()
        return when {
            c.isEmpty() -> null
            c.startsWith("mp4a") || c == "aac" -> "AAC"
            c.startsWith("opus") -> "Opus"
            c.startsWith("vorbis") -> "Vorbis"
            c.startsWith("mp3") -> "MP3"
            c.startsWith("ec-3") || c == "eac3" -> "E-AC-3"
            c.startsWith("ac-3") || c == "ac3" -> "AC-3"
            c.startsWith("flac") -> "FLAC"
            else -> codec.substringBefore('.').uppercase()
        }
    }

    /** yt-dlp's `-f` for the pick: one stream, or picture + sound to be merged. */
    fun selector(video: MediaFormat?, audio: MediaFormat?): String? = when {
        video != null && audio != null && !video.isMuxed -> "${video.id}+${audio.id}"
        video != null -> video.id
        audio != null -> audio.id
        else -> null
    }

    /** "1080p60 · VP9 + Opus" or "Opus · 129 kbit/s", for the sheet. */
    fun label(video: MediaFormat?, audio: MediaFormat?, kbit: (Int) -> String): String? {
        if (video != null) {
            val codecs = listOfNotNull(
                video.videoCodecName,
                (if (video.isMuxed) video.audioCodecName else audio?.audioCodecName),
            ).joinToString(" + ")
            return listOfNotNull(video.resolutionLabel ?: video.note, codecs.ifEmpty { null }).joinToString(" · ")
        }
        audio ?: return null
        return listOfNotNull(audio.audioCodecName, audio.bitrate?.let(kbit)).joinToString(" · ")
    }

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    private fun JsonObject.double(key: String) = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
    private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
}
