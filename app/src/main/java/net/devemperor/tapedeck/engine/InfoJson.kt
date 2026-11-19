package net.devemperor.tapedeck.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import net.devemperor.tapedeck.data.Chapter

/** What Tapedeck keeps from yt-dlp's info JSON (which itself is often half a megabyte). */
data class MediaInfo(
    val title: String?,
    val uploader: String?,
    val durationMs: Long,
    val chapters: List<Chapter>,
    val webpageUrl: String?,
    val height: Int?,
    val width: Int? = null,
    val audioBitrate: Int?,
    val audioCodec: String?,
    /** Sizes of the streams downloaded one after the other (picture, sound), where yt-dlp knows them. */
    val streamSizes: List<Long?> = emptyList(),
) {
    /**
     * The resolution class people know ("1080p"), not the bare height: a 2.24:1 film in full HD is
     * 1920×858 and would otherwise show as "858p".
     */
    val resolutionClass: Int?
        get() {
            val h = height ?: return null
            val w = width
            val equivalent = when {
                w != null && h > w -> w // portrait (Shorts): the short side, as for landscape
                w != null && w * 9 > h * 16 -> w * 9 / 16 // wider than 16:9
                else -> h
            }
            return STANDARD_HEIGHTS.minByOrNull { kotlin.math.abs(it - equivalent) }
        }

    private companion object {
        val STANDARD_HEIGHTS = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)
    }
}

data class PlaylistItem(
    val url: String,
    val title: String?,
    val uploader: String?,
    val durationMs: Long,
    val thumbnailUrl: String?,
)

data class Playlist(val title: String?, val items: List<PlaylistItem>)

object InfoJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val untitledChapter = Regex("""^<Untitled Chapter \d+>$""")

    fun parse(text: String): MediaInfo {
        val root = json.parseToJsonElement(text).jsonObject
        val chapters = (root["chapters"] as? JsonArray).orEmpty().mapNotNull { element ->
            val chapter = element as? JsonObject ?: return@mapNotNull null
            val start = chapter.double("start_time") ?: return@mapNotNull null
            Chapter(title = chapter.string("title").orEmpty(), startMs = (start * 1000).toLong())
        }.takeUnless { list ->
            // yt-dlp fills gaps with "<Untitled Chapter 1>"; a list of those, or a single chapter, helps nobody.
            list.size < 2 || list.all { untitledChapter.matches(it.title) || it.title.isBlank() }
        }.orEmpty()
        return MediaInfo(
            title = root.string("title"),
            uploader = root.string("uploader") ?: root.string("channel") ?: root.string("artist"),
            durationMs = ((root.double("duration") ?: 0.0) * 1000).toLong(),
            chapters = chapters,
            webpageUrl = root.string("webpage_url"),
            height = root.int("height"),
            width = root.int("width"),
            audioBitrate = root.double("abr")?.toInt(),
            audioCodec = root.string("acodec")?.takeIf { it != "none" },
            streamSizes = (root["requested_formats"] as? JsonArray).orEmpty().map { format ->
                (format as? JsonObject)?.let { (it.double("filesize") ?: it.double("filesize_approx"))?.toLong() }
            },
        )
    }

    fun parsePlaylist(text: String): Playlist {
        // -J prints a single JSON document; skip anything a plugin may have printed before it.
        val start = text.indexOf('{').takeIf { it >= 0 } ?: throw IllegalArgumentException("no JSON in yt-dlp output")
        val root = json.parseToJsonElement(text.substring(start)).jsonObject
        val entries = (root["entries"] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.string("id")
            val url = item.string("url")?.takeIf { it.startsWith("http") }
                ?: id?.let { Links.videoUrl(it) }
                ?: return@mapNotNull null
            val thumbnail = (item["thumbnails"] as? JsonArray)?.lastOrNull()?.let { (it as? JsonObject)?.string("url") }
            PlaylistItem(
                url = url,
                title = item.string("title"),
                uploader = item.string("channel") ?: item.string("uploader"),
                durationMs = ((item.double("duration") ?: 0.0) * 1000).toLong(),
                thumbnailUrl = thumbnail ?: Links.thumbnailFor(url),
            )
        }
        return Playlist(title = root.string("title"), items = entries)
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
    private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    private fun JsonObject.double(key: String): Double? = primitive(key)?.doubleOrNull
    private fun JsonObject.int(key: String): Int? = primitive(key)?.intOrNull ?: primitive(key)?.doubleOrNull?.toInt()
}
