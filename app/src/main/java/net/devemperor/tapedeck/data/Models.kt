package net.devemperor.tapedeck.data

import kotlinx.serialization.Serializable

@Serializable
enum class MediaKind { AUDIO, VIDEO }

/** M4A is what YouTube serves natively (no re-encoding), MP3 suits car stereos that play USB sticks. */
@Serializable
enum class AudioFormat { M4A, MP3, ORIGINAL }

@Serializable
enum class AudioQuality { HIGH, SAVER }

/**
 * The container of a video, and with it the preferred codecs: MP4 (H.264 + AAC) plays smoothly on every
 * phone, WebM (VP9 + Opus) is smaller for the same picture, MKV takes the best picture in any codec.
 */
@Serializable
enum class VideoFormat { MP4, WEBM, MKV }

@Serializable
enum class DownloadState { QUEUED, WAITING_NETWORK, RUNNING, DONE, FAILED, CANCELED }

@Serializable
data class DownloadOptions(
    val kind: MediaKind = MediaKind.AUDIO,
    val audioFormat: AudioFormat = AudioFormat.M4A,
    val audioQuality: AudioQuality = AudioQuality.HIGH,
    val videoHeight: Int = 720,
    val videoFormat: VideoFormat = VideoFormat.MP4,
    /** yt-dlp format ids picked by hand from the list ("137+140"), for this one download only. */
    val customFormat: String? = null,
    /** How the hand-picked format reads in the sheet, e.g. "1080p60 · VP9 + Opus". */
    val customLabel: String? = null,
) {
    /** What is kept as the next download's defaults: everything but a hand-picked format. */
    fun asDefaults() = copy(customFormat = null, customLabel = null)
}

@Serializable
data class Chapter(val title: String, val startMs: Long) {
    /** yt-dlp fills gaps between real chapters with "<Untitled Chapter 2>"; those are not shown. */
    val isPlaceholder: Boolean get() = title.isBlank() || PLACEHOLDER.matches(title)

    private companion object {
        val PLACEHOLDER = Regex("""^<Untitled Chapter \d+>$""")
    }
}

@Serializable
data class Entry(
    val id: String,
    val url: String,
    val title: String,
    val uploader: String? = null,
    val durationMs: Long = 0,
    val options: DownloadOptions = DownloadOptions(),
    val state: DownloadState = DownloadState.QUEUED,
    /** [net.devemperor.tapedeck.engine.ErrorKind] name, set while [state] is FAILED. */
    val errorKind: String? = null,
    val errorDetail: String? = null,
    val mediaFile: String? = null,
    val thumbnailFile: String? = null,
    val thumbnailUrl: String? = null,
    val formatLabel: String? = null,
    val sizeBytes: Long = 0,
    val createdAt: Long,
    val completedAt: Long? = null,
    val positionMs: Long = 0,
    val lastPlayedAt: Long? = null,
    val finished: Boolean = false,
    val finishedAt: Long? = null,
    /** Kept entries are never removed by the automatic clean-up. */
    val keep: Boolean = false,
    val chapters: List<Chapter> = emptyList(),
    /** How often the download was retried automatically after a yt-dlp update. */
    val autoRetries: Int = 0,
    /** Size of a video's picture (from yt-dlp's info), so an upright one is shown upright before it plays. */
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    /** May load over mobile data although "Wi-Fi only" is on: the user said so for this download. */
    val mobileData: Boolean = false,
    /** The subscribed mixtape this was downloaded for, by itself: it counts towards that subscription's limit. */
    val subscription: String? = null,
) {
    val isDone get() = state == DownloadState.DONE && mediaFile != null
    val isActive get() = state == DownloadState.QUEUED || state == DownloadState.WAITING_NETWORK || state == DownloadState.RUNNING
    val isVideo get() = options.kind == MediaKind.VIDEO
    /** Held back by "Wi-Fi only" (or by having no connection at all) and not let onto mobile data. */
    val waitsForWifi get() = state == DownloadState.WAITING_NETWORK && !mobileData
    val listenedFraction: Float
        get() = when {
            finished -> 1f
            durationMs <= 0 -> 0f
            else -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        }
    val isNew get() = lastPlayedAt == null && !finished
}

/**
 * A collection of downloads in an order of its own, played one after the other — a playlist, put as
 * a mixtape since this is Tapedeck. A download can be on several mixtapes; deleting a mixtape keeps them.
 */
@Serializable
data class Mixtape(
    val id: String,
    val name: String,
    /** Entry ids in play order. */
    val entryIds: List<String> = emptyList(),
    /** Index into the cassette colours, so every mixtape keeps its look. */
    val color: Int = 0,
    val createdAt: Long,
    /** A channel or playlist that fills this mixtape by itself; null for one put together by hand. */
    val subscription: Subscription? = null,
)

/**
 * A channel or playlist subscribed to: every few hours Tapedeck looks for new videos and downloads them
 * onto the mixtape it belongs to. Once there are more than [keep] finished downloads of it, the oldest
 * make room (not those kept by hand).
 */
@Serializable
data class Subscription(
    /** What is looked at: a YouTube channel's videos (newest first) or a playlist (in its own order). */
    val url: String,
    val playlist: Boolean = false,
    /** Audio or video, format and quality of its downloads. */
    val options: DownloadOptions = DownloadOptions(),
    /** How many of its downloads are kept at most; 0 keeps all. */
    val keep: Int = DEFAULT_KEEP_CHANNEL,
    /** The videos seen already (downloaded or passed over as older), so each one is fetched only once. */
    val known: List<String> = emptyList(),
    val lastCheckAt: Long = 0,
    /** Why the last look failed, if it did. */
    val lastError: String? = null,
) {
    companion object {
        const val DEFAULT_KEEP_CHANNEL = 5
        const val DEFAULT_KEEP_PLAYLIST = 0
        const val MAX_KEEP = 50
    }
}

@Serializable
data class LibraryFile(val version: Int = 1, val entries: List<Entry> = emptyList(), val mixtapes: List<Mixtape> = emptyList())
