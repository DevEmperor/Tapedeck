package net.devemperor.tapedeck.data

import net.devemperor.tapedeck.engine.Links
import net.devemperor.tapedeck.engine.PlaylistItem

/** The decisions of a subscription — what is new, what makes room — in plain Kotlin, so they are unit-tested. */
object SubscriptionPlan {
    /** How many of a channel's newest videos a look reads: enough for everything new since the last one. */
    const val CHANNEL_LOOK = 20

    /** Videos remembered as seen; older ones have long left a channel's first page. */
    const val KNOWN_LIMIT = 500

    /** A video's mark in [Subscription.known]: its YouTube id where it has one, else its clean link. */
    fun key(url: String): String = Links.videoId(url) ?: Links.clean(url)

    /** What a look brought: the videos to download, in the order they go on the mixtape, and all seen since. */
    data class Look(val download: List<PlaylistItem>, val known: List<String>)

    /**
     * The videos in [items] not seen before — at most [take] of them, the newest: by default as many as
     * the subscription keeps (all for 0), when subscribing as many as asked for now (0 for none). A channel
     * lists newest first and its new videos go on top of the mixtape; a playlist keeps its order and its new
     * videos usually come at the end. What is passed over as older counts as seen; upcoming premieres and
     * live streams (no length yet) are left for a later look.
     */
    fun look(
        subscription: Subscription,
        items: List<PlaylistItem>,
        take: Int = subscription.keep.takeIf { it > 0 } ?: Int.MAX_VALUE,
    ): Look {
        val ready = items.filter { it.durationMs > 0 }
        val seen = subscription.known.toHashSet()
        val fresh = ready.filter { key(it.url) !in seen }.distinctBy { key(it.url) }
        val download = when {
            take <= 0 -> emptyList()
            subscription.playlist -> fresh.takeLast(take)
            else -> fresh.take(take)
        }
        val known = (subscription.known + ready.map { key(it.url) }).distinct().takeLast(KNOWN_LIMIT)
        return Look(download, known)
    }

    /**
     * The finished downloads of the subscribed mixtape [tape] beyond its limit, oldest first: what goes to
     * make room for new ones. Only those still on the mixtape count; kept-by-hand ones and the one playing stay.
     */
    fun surplus(tape: Mixtape, entries: List<Entry>, playing: String?): List<Entry> {
        val keep = tape.subscription?.keep ?: return emptyList()
        if (keep <= 0) return emptyList()
        val onTape = tape.entryIds.toHashSet()
        return entries
            .filter { it.subscription == tape.id && it.id in onTape && it.isDone && !it.keep }
            .sortedByDescending { it.createdAt }
            .drop(keep)
            .filter { it.id != playing }
    }
}
