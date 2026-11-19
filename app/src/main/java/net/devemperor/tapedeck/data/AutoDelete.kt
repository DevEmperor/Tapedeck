package net.devemperor.tapedeck.data

import java.util.concurrent.TimeUnit

/**
 * When the automatic clean-up removes an entry: N days after download, or after it was heard to the end.
 * Kept entries and those on a mixtape ([onMixtapes]) are never removed — a song is meant to be heard again.
 */
object AutoDelete {
    fun dueAt(entry: Entry, settings: AppSettings, onMixtapes: Set<String> = emptySet()): Long? {
        if (!settings.autoDeleteEnabled || entry.keep || !entry.isDone || entry.id in onMixtapes) return null
        val reference = if (settings.autoDeleteOnlyFinished) {
            entry.finishedAt?.takeIf { entry.finished } ?: return null
        } else {
            entry.completedAt ?: entry.createdAt
        }
        return reference + TimeUnit.DAYS.toMillis(settings.autoDeleteAfterDays.toLong())
    }

    fun victims(entries: List<Entry>, settings: AppSettings, now: Long, playingId: String?, onMixtapes: Set<String> = emptySet()): List<Entry> =
        entries.filter { entry ->
            entry.id != playingId && dueAt(entry, settings, onMixtapes)?.let { it <= now } == true
        }
}
