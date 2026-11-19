package net.devemperor.tapedeck.engine

import net.devemperor.tapedeck.data.AudioFormat
import net.devemperor.tapedeck.data.AudioQuality
import net.devemperor.tapedeck.data.DownloadOptions
import net.devemperor.tapedeck.data.MediaKind
import net.devemperor.tapedeck.data.VideoFormat

/**
 * The yt-dlp command line for one download. Every entry gets its own folder with fixed file names
 * (media.*, cover.jpg, info.info.json), so titles with odd characters never reach the file system.
 */
object YtDlpArgs {
    const val MEDIA_BASENAME = "media"
    const val COVER_BASENAME = "cover"
    const val INFO_BASENAME = "info"

    fun download(options: DownloadOptions, outDir: String, sponsorBlock: Boolean): List<String> = buildList {
        add("--no-playlist")
        add("--no-mtime")
        add("--newline")
        add("--progress-delta"); add("0.5")
        add("--retries"); add("10")
        add("--fragment-retries"); add("10")
        add("--concurrent-fragments"); add("4")
        add("-o"); add("$outDir/$MEDIA_BASENAME.%(ext)s")
        add("-o"); add("thumbnail:$outDir/$COVER_BASENAME.%(ext)s")
        add("-o"); add("infojson:$outDir/$INFO_BASENAME")
        add("--write-thumbnail")
        add("--convert-thumbnails"); add("jpg")
        add("--write-info-json")
        add("--embed-metadata")
        val custom = options.customFormat
        when (options.kind) {
            MediaKind.AUDIO -> addAll(if (custom != null) customAudio(custom) else audio(options.audioFormat, options.audioQuality))
            MediaKind.VIDEO -> addAll(if (custom != null) customVideo(custom, options.videoFormat) else video(options.videoHeight, options.videoFormat))
        }
        if (sponsorBlock) {
            add("--sponsorblock-remove"); add("sponsor,selfpromo,interaction")
        }
    }

    fun audio(format: AudioFormat, quality: AudioQuality): List<String> = buildList {
        val selector = when (quality) {
            // Stereo up to 160 kbit/s: some videos also offer AAC 5.1 at 384 kbit/s (format 258), three
            // times the size for nothing a car stereo can play. 140 (AAC 128 kbit/s) needs no re-encoding.
            AudioQuality.HIGH -> if (format == AudioFormat.M4A) {
                "bestaudio[ext=m4a][audio_channels<=2][abr<=160]/bestaudio[ext=m4a][audio_channels<=2]/bestaudio[ext=m4a]/bestaudio/best"
            } else {
                "bestaudio[audio_channels<=2][abr<=170]/bestaudio[audio_channels<=2]/bestaudio/best"
            }
            // Around 50–70 kbit/s: plenty for speech and fine for music in a car, ~30 MB per hour.
            AudioQuality.SAVER -> "bestaudio[abr<=72]/worstaudio/bestaudio/best"
        }
        add("-f"); add(selector)
        add("-x")
        when (format) {
            AudioFormat.M4A -> {
                add("--audio-format"); add("m4a")
                add("--audio-quality"); add(if (quality == AudioQuality.SAVER) "64K" else "128K")
            }
            AudioFormat.MP3 -> {
                add("--audio-format"); add("mp3")
                add("--audio-quality"); add(if (quality == AudioQuality.SAVER) "64K" else "160K")
            }
            AudioFormat.ORIGINAL -> Unit
        }
    }

    fun video(height: Int, format: VideoFormat = VideoFormat.MP4): List<String> = listOf(
        "-f", "bv*+ba/b",
        // Closest to the wanted height first, then the codecs that suit the container: H.264 + AAC for MP4
        // (every phone decodes those in hardware), VP9 + Opus for WebM, and for MKV simply the best.
        "-S", when (format) {
            VideoFormat.MP4 -> "res:$height,vcodec:h264,acodec:m4a"
            VideoFormat.WEBM -> "res:$height,vcodec:vp9,acodec:opus"
            VideoFormat.MKV -> "res:$height"
        },
        "--merge-output-format", containers(format),
    )

    /** Exactly the streams picked from the list, put together in the chosen container. */
    fun customVideo(formatId: String, format: VideoFormat): List<String> =
        listOf("-f", formatId, "--merge-output-format", containers(format))

    /** Exactly the stream picked from the list, kept as it is (no re-encoding). */
    fun customAudio(formatId: String): List<String> = listOf("-f", formatId, "-x")

    /**
     * Where picture and sound may be merged. WebM only holds VP8/VP9/AV1 with Opus/Vorbis, so H.264 from
     * other sites falls back to MKV instead of failing; MP4 and MKV take everything YouTube offers.
     */
    fun containers(format: VideoFormat): String = when (format) {
        VideoFormat.MP4 -> "mp4"
        VideoFormat.WEBM -> "webm/mkv"
        VideoFormat.MKV -> "mkv"
    }

    /** Lists a playlist's videos without resolving any of them (fast, no JavaScript challenge). */
    fun playlist(): List<String> = listOf("--flat-playlist", "--yes-playlist", "-J")

    /** Everything yt-dlp knows about one video, formats included, without downloading it. */
    fun inspect(): List<String> = listOf("--no-playlist", "-J")
}
