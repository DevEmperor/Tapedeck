package net.devemperor.tapedeck.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.data.AppSettings
import net.devemperor.tapedeck.data.Entry
import net.devemperor.tapedeck.data.LibrarySort
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

    fun from(entry: Entry, withArtwork: Boolean = true): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(entry.title)
            .setArtist(entry.uploader)
            .setDurationMs(entry.durationMs.takeIf { it > 0 })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(if (entry.isVideo) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
        if (withArtwork) entry.thumbnailFile?.let { metadata.setArtworkUri(Uri.fromFile(File(it))) }
        return MediaItem.Builder()
            .setMediaId(entry.id)
            .setUri(entry.mediaFile?.let { Uri.fromFile(File(it)) })
            .setMediaMetadata(metadata.build())
            .build()
    }

    fun root(context: Context): MediaItem = folder(ROOT, context.getString(R.string.app_name))

    fun children(context: Context, parentId: String, entries: List<Entry>, settings: AppSettings): List<MediaItem>? {
        val done = sorted(entries.filter { it.isDone }, settings.librarySort)
        return when (parentId) {
            ROOT -> buildList {
                if (done.any(::isInProgress)) add(folder(CONTINUE, context.getString(R.string.browse_continue)))
                add(folder(NEW, context.getString(R.string.browse_new)))
                add(folder(ALL, context.getString(R.string.browse_all)))
            }
            CONTINUE -> done.filter(::isInProgress).sortedByDescending { it.lastPlayedAt ?: 0 }.map { from(it, withArtwork = false) }
            NEW -> done.filter { it.isNew }.map { from(it, withArtwork = false) }
            ALL -> done.map { from(it, withArtwork = false) }
            else -> null
        }
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
