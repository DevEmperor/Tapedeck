package net.devemperor.tapedeck.data

import java.util.concurrent.TimeUnit

/** When the automatic clean-up removes an entry: N days after download, or after it was heard to the end. */
object AutoDelete {
    fun dueAt(entry: Entry, settings: AppSettings): Long? {
        if (settings.autoDeleteDays <= 0 || entry.keep || !entry.isDone) return null
        val reference = if (settings.autoDeleteOnlyFinished) {
            entry.finishedAt?.takeIf { entry.finished } ?: return null
        } else {
            entry.completedAt ?: entry.createdAt
        }
        return reference + TimeUnit.DAYS.toMillis(settings.autoDeleteDays.toLong())
    }

    fun victims(entries: List<Entry>, settings: AppSettings, now: Long, playingId: String?): List<Entry> =
        entries.filter { entry ->
            entry.id != playingId && dueAt(entry, settings)?.let { it <= now } == true
        }
}
