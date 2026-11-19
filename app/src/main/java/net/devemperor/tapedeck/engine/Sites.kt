package net.devemperor.tapedeck.engine

import java.net.URI

/**
 * The big sites a download can come from, recognised by its link — for the logo next to it, so YouTube,
 * Instagram & Co. can be told apart at a glance. Every other site simply gets no logo.
 */
enum class Site(val label: String, private val domains: List<String>) {
    YOUTUBE("YouTube", listOf("youtube.com", "youtu.be", "youtube-nocookie.com")),
    INSTAGRAM("Instagram", listOf("instagram.com")),
    TIKTOK("TikTok", listOf("tiktok.com")),
    X("X", listOf("x.com", "twitter.com")),
    FACEBOOK("Facebook", listOf("facebook.com", "fb.watch", "fb.com")),
    THREADS("Threads", listOf("threads.net", "threads.com")),
    BLUESKY("Bluesky", listOf("bsky.app")),
    REDDIT("Reddit", listOf("reddit.com", "redd.it")),
    PINTEREST("Pinterest", listOf("pinterest.com", "pinterest.de", "pin.it")),
    TUMBLR("Tumblr", listOf("tumblr.com")),
    VIMEO("Vimeo", listOf("vimeo.com")),
    DAILYMOTION("Dailymotion", listOf("dailymotion.com", "dai.ly")),
    TWITCH("Twitch", listOf("twitch.tv")),
    BILIBILI("Bilibili", listOf("bilibili.com", "b23.tv")),
    SOUNDCLOUD("SoundCloud", listOf("soundcloud.com")),
    BANDCAMP("Bandcamp", listOf("bandcamp.com")),
    MIXCLOUD("Mixcloud", listOf("mixcloud.com")),
    ZDF("ZDF", listOf("zdf.de")),
    ;

    companion object {
        /** The site of [url], subdomains included (m., www., vm., on., music. …); null for any other. */
        fun of(url: String?): Site? {
            val host = try {
                URI(url?.trim() ?: return null).host?.lowercase()
            } catch (e: Exception) {
                null
            } ?: return null
            return entries.firstOrNull { site -> site.domains.any { host == it || host.endsWith(".$it") } }
        }
    }
}
