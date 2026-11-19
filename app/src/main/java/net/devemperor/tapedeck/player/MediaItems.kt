package net.devemperor.tapedeck.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.data.AppSettings
import net.devemperor.tapedeck.data.Entry
import net.devemperor.tapedeck.data.LibrarySort
import net.devemperor.tapedeck.data.Mixtape
import java.io.File

/** The id of whatever the player holds right now, so the clean-up never deletes it mid-drive. */
object NowPlaying {
    @Volatile
    var entryId: String? = null
}

/** Media items for the player, and the browse tree that Android Auto and car head units show. */
object MediaItems {
    const val ROOT = "tapedeck:root"
    const val CONTINUE = "tapedeck:continue"
    const val NEW = "tapedeck:new"
    const val ALL = "tapedeck:all"
    const val MIXTAPES = "tapedeck:mixtapes"

    /** A mixtape's folder in the car's browse tree ("tapedeck:mixtape:<id>"). */
    private const val MIXTAPE_PREFIX = "tapedeck:mixtape:"

    /** A song inside a mixtape folder ("tapedeck:in:<mixtape id>/<entry id>"), so tapping it plays that mixtape. */
    private const val IN_MIXTAPE_PREFIX = "tapedeck:in:"

    /** Extra on a queued item: the mixtape it is played from, for the player's "From mixtape …" line. */
    const val EXTRA_MIXTAPE = "net.devemperor.tapedeck.mixtape"

    fun from(entry: Entry, withArtwork: Boolean = true, mixtapeId: String? = null, browseId: String? = null): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(entry.title)
            .setArtist(entry.uploader)
            .setDurationMs(entry.durationMs.takeIf { it > 0 })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(if (entry.isVideo) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
        if (withArtwork) entry.thumbnailFile?.let { metadata.setArtworkUri(Uri.fromFile(File(it))) }
        if (mixtapeId != null) metadata.setExtras(Bundle().apply { putString(EXTRA_MIXTAPE, mixtapeId) })
        return MediaItem.Builder()
            .setMediaId(browseId ?: entry.id)
            .setUri(entry.mediaFile?.let { Uri.fromFile(File(it)) })
            .setMediaMetadata(metadata.build())
            .build()
    }

    /** The entry id behind a media id: a plain id, or one inside a mixtape folder. */
    fun entryId(mediaId: String): String = if (mediaId.startsWith(IN_MIXTAPE_PREFIX)) mediaId.substringAfter('/') else mediaId

    /** The mixtape an item is played from: its extra (from the app) or its browse id (from the car). */
    fun mixtapeOf(item: MediaItem): String? =
        item.mediaMetadata.extras?.getString(EXTRA_MIXTAPE)
            ?: item.mediaId.takeIf { it.startsWith(IN_MIXTAPE_PREFIX) }?.removePrefix(IN_MIXTAPE_PREFIX)?.substringBefore('/')

    fun root(context: Context): MediaItem = folder(ROOT, context.getString(R.string.app_name))

    fun children(context: Context, parentId: String, entries: List<Entry>, mixtapes: List<Mixtape>, settings: AppSettings): List<MediaItem>? {
        val done = sorted(entries.filter { it.isDone }, settings.librarySort)
        return when {
            parentId == ROOT -> buildList {
                if (done.any(::isInProgress)) add(folder(CONTINUE, context.getString(R.string.browse_continue)))
                if (mixtapes.isNotEmpty()) add(folder(MIXTAPES, context.getString(R.string.mixtapes)))
                add(folder(NEW, context.getString(R.string.browse_new)))
                add(folder(ALL, context.getString(R.string.browse_all)))
            }
            parentId == CONTINUE -> done.filter(::isInProgress).sortedByDescending { it.lastPlayedAt ?: 0 }.map { from(it, withArtwork = false) }
            parentId == NEW -> done.filter { it.isNew }.map { from(it, withArtwork = false) }
            parentId == ALL -> done.map { from(it, withArtwork = false) }
            parentId == MIXTAPES -> mixtapes.map { folder(MIXTAPE_PREFIX + it.id, it.name) }
            parentId.startsWith(MIXTAPE_PREFIX) -> {
                val tape = mixtapes.firstOrNull { it.id == parentId.removePrefix(MIXTAPE_PREFIX) } ?: return null
                mixtapeQueue(tape, entries).map { from(it, withArtwork = false, browseId = "$IN_MIXTAPE_PREFIX${tape.id}/${it.id}") }
            }
            else -> null
        }
    }

    /** A mixtape's downloads in its order — those that are there to play. */
    fun mixtapeQueue(tape: Mixtape, entries: List<Entry>): List<Entry> {
        val byId = entries.associateBy { it.id }
        return tape.entryIds.mapNotNull { byId[it]?.takeIf { entry -> entry.isDone } }
    }

    /** The entry and everything after it in the library's order — the queue for "keep playing". */
    fun queueFrom(entry: Entry, entries: List<Entry>, settings: AppSettings): List<Entry> {
        if (!settings.autoPlayNext) return listOf(entry)
        val ordered = sorted(entries.filter { it.isDone }, settings.librarySort)
        val index = ordered.indexOfFirst { it.id == entry.id }
        return if (index < 0) listOf(entry) else ordered.drop(index)
    }

    fun sorted(entries: List<Entry>, sort: LibrarySort): List<Entry> = when (sort) {
        LibrarySort.NEWEST -> entries.sortedByDescending { it.createdAt }
        LibrarySort.OLDEST -> entries.sortedBy { it.createdAt }
        LibrarySort.TITLE -> entries.sortedBy { it.title.lowercase() }
        LibrarySort.LAST_PLAYED -> entries.sortedByDescending { it.lastPlayedAt ?: 0 }
    }

    fun resumePosition(entry: Entry, rewindSec: Int): Long =
        if (entry.positionMs > 5_000) (entry.positionMs - rewindSec * 1000L).coerceAtLeast(0) else 0

    private fun isInProgress(entry: Entry) = entry.positionMs > 5_000 && !entry.finished

    private fun folder(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build(),
        )
        .build()
}
