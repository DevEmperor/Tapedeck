package net.devemperor.tapedeck.engine

import java.net.URI
import java.net.URLDecoder

/** Picks the link out of shared text and tidies it up. Pure Kotlin, so it is unit-tested. */
object Links {
    private val urlPattern = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)
    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "www.youtu.be", "youtube-nocookie.com", "www.youtube-nocookie.com")
    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")

    // Share links carry tracking parameters (si, pp, feature) that change nothing about the video.
    private val trackingParams = setOf("si", "pp", "feature", "fbclid", "gclid", "ab_channel")

    /** The first http(s) link in [text], e.g. "Look at this https://youtu.be/xyz?si=abc". */
    fun extractUrl(text: String?): String? {
        val match = urlPattern.find(text ?: return null)?.value ?: return null
        return match.trimEnd('.', ',', ';', ')', ']', '}', '!', '»', '“', '"')
    }

    fun isYoutube(url: String): Boolean = host(url)?.let { it in youtubeHosts } == true

    fun videoId(url: String): String? {
        val uri = parse(url) ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in youtubeHosts) return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val candidate = when {
            host.endsWith("youtu.be") -> segments.firstOrNull()
            segments.firstOrNull() == "watch" -> query(uri)["v"]
            segments.firstOrNull() in setOf("shorts", "live", "embed", "v") -> segments.getOrNull(1)
            else -> query(uri)["v"]
        }
        return candidate?.takeIf { idPattern.matches(it) }
    }

    /**
     * The playlist a link points into, if downloading it as a whole makes sense. Mixes ("RD…") are
     * endless radio lists and "Watch later"/"Liked" are private, so neither counts.
     */
    fun playlistId(url: String): String? {
        val uri = parse(url) ?: return null
        if (uri.host?.lowercase() !in youtubeHosts) return null
        val list = query(uri)["list"] ?: return null
        if (list.startsWith("RD") || list == "WL" || list == "LL" || list.startsWith("UL")) return null
        return list
    }

    fun isPlaylistOnly(url: String): Boolean = playlistId(url) != null && videoId(url) == null

    /** A canonical, tracking-free link: youtu.be and shorts become watch?v=, a timestamp is kept. */
    fun clean(url: String): String {
        val uri = parse(url) ?: return url
        if (uri.host?.lowercase() !in youtubeHosts) return stripTracking(uri) ?: url
        val params = query(uri)
        val id = videoId(url)
        val list = params["list"]
        return when {
            id != null -> buildString {
                append("https://www.youtube.com/watch?v=").append(id)
                params["t"]?.let { append("&t=").append(it) }
                list?.let { append("&list=").append(it) }
            }
            list != null -> "https://www.youtube.com/playlist?list=$list"
            else -> stripTracking(uri) ?: url
        }
    }

    fun videoUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    fun thumbnailFor(url: String): String? = videoId(url)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

    private fun stripTracking(uri: URI): String? {
        val kept = uri.rawQuery?.split('&')?.filter { part ->
            val key = part.substringBefore('=')
            key !in trackingParams && !key.startsWith("utm_")
        }
        return try {
            URI(uri.scheme, uri.rawAuthority, uri.rawPath, null, null).toString()
                .let { base -> if (kept.isNullOrEmpty()) base else base + "?" + kept.joinToString("&") }
                .let { if (uri.rawFragment != null) it + "#" + uri.rawFragment else it }
        } catch (e: Exception) {
            null
        }
    }

    private fun host(url: String) = parse(url)?.host?.lowercase()

    private fun parse(url: String): URI? = try {
        URI(url.trim())
    } catch (e: Exception) {
        null
    }

    private fun query(uri: URI): Map<String, String> =
        uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }
}
