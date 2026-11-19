package net.devemperor.tapedeck.engine

import net.devemperor.tapedeck.data.AudioFormat
import net.devemperor.tapedeck.data.AudioQuality
import net.devemperor.tapedeck.data.DownloadOptions
import net.devemperor.tapedeck.data.MediaKind

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
        when (options.kind) {
            MediaKind.AUDIO -> addAll(audio(options.audioFormat, options.audioQuality))
            MediaKind.VIDEO -> addAll(video(options.videoHeight))
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

    fun video(height: Int): List<String> = listOf(
        "-f", "bv*+ba/b",
        // Closest to the wanted height, then H.264 + AAC: every phone decodes those in hardware.
        "-S", "res:$height,vcodec:h264,acodec:m4a",
        "--merge-output-format", "mp4",
    )

    /** Lists a playlist's videos without resolving any of them (fast, no JavaScript challenge). */
    fun playlist(): List<String> = listOf("--flat-playlist", "--yes-playlist", "-J")
}
