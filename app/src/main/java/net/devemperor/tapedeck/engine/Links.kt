package net.devemperor.tapedeck.engine

import java.net.URI
import java.net.URLDecoder

/** Picks the link out of shared text and tidies it up. Pure Kotlin, so it is unit-tested. */
object Links {
    private val urlPattern = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)
    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "www.youtu.be", "youtube-nocookie.com", "www.youtube-nocookie.com")
    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")

    // Share links carry tracking parameters (si, pp, feature) that change nothing about the video:
    // YouTube, Instagram (igsh), Facebook (mibextid, rdid), TikTok (_r, _t, is_from_webapp …), Reddit
    // (share_id), X (ref_src) and Bilibili (vd_source, spm_id_from, share_*).
    private val trackingParams = setOf(
        "si", "pp", "feature", "fbclid", "gclid", "ab_channel",
        "igsh", "igshid", "ig_rid", "ig_mid", "mibextid", "rdid",
        "_r", "_t", "is_from_webapp", "sender_device", "is_copy_url", "web_id",
        "share_id", "ref_src", "ref_url",
        "vd_source", "spm_id_from", "share_source", "share_medium", "share_plat", "share_session_id", "share_tag", "unique_k",
    )

    // On X a share link ends in ?s=20&t=…; there "t" is no timestamp but a tracking token.
    private val xHosts = setOf("x.com", "twitter.com", "mobile.twitter.com", "mobile.x.com")

    /**
     * Well-known sites yt-dlp downloads from. Any link can be downloaded; a copied one of these is also
     * offered at the top of the library (everything else would be every link anybody ever copies).
     */
    private val knownSites = setOf(
        "youtube.com", "youtu.be", "youtube-nocookie.com",
        "instagram.com", "tiktok.com", "x.com", "twitter.com", "facebook.com", "fb.watch", "threads.net", "threads.com", "bsky.app",
        "vimeo.com", "dailymotion.com", "dai.ly", "twitch.tv", "reddit.com", "redd.it", "streamable.com", "rumble.com", "kick.com",
        "soundcloud.com", "bandcamp.com", "mixcloud.com", "audiomack.com",
        "ardmediathek.de", "zdf.de", "arte.tv", "3sat.de", "tagesschau.de", "br.de", "wdr.de", "ndr.de", "swr.de", "srf.ch", "orf.at",
        "ted.com", "archive.org", "bilibili.com", "b23.tv", "nicovideo.jp", "odysee.com", "pinterest.com", "pin.it", "tumblr.com",
        "imgur.com", "9gag.com", "linkedin.com", "loom.com",
    )

    /** The first http(s) link in [text], e.g. "Look at this https://youtu.be/xyz?si=abc". */
    fun extractUrl(text: String?): String? {
        val match = urlPattern.find(text ?: return null)?.value ?: return null
        return match.trimEnd('.', ',', ';', ')', ']', '}', '!', '»', '“', '"')
    }

    fun isYoutube(url: String): Boolean = host(url)?.let { it in youtubeHosts } == true

    /** YouTube, Instagram, TikTok, Vimeo, the Mediatheken …, also with subdomains (m., www., vm., on.). */
    fun isKnownSite(url: String): Boolean {
        val host = host(url) ?: return false
        return knownSites.any { host == it || host.endsWith(".$it") }
    }

    /** "instagram.com" for https://www.instagram.com/reel/…, to say where a link leads. */
    fun siteName(url: String): String? = host(url)?.removePrefix("www.")?.removePrefix("m.")

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
        val onX = uri.host?.lowercase() in xHosts
        val kept = uri.rawQuery?.split('&')?.filter { part ->
            val key = part.substringBefore('=')
            key.isNotEmpty() && key !in trackingParams && !key.startsWith("utm_") && !(onX && (key == "s" || key == "t"))
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
