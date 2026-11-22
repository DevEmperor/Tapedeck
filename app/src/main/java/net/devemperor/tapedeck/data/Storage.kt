package net.devemperor.tapedeck.data

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * Where downloads live ([StorageLocation]). Each one works in a folder of its own with fixed file names —
 * in the app's folder on the phone (Android/data/…/files/media/<entry>, no permission needed, visible over
 * USB) or the app's folder on an SD card. With "Download folder" the finished file then moves to
 * Download/Tapedeck under its title, where every file manager shows it.
 */
object Storage {
    /** The app's own folder on the phone. */
    fun appRoot(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "media").apply { mkdirs() }
    }

    /** The app's folder on an SD card, while one is in and mounted. */
    fun sdCardRoot(context: Context): File? =
        context.getExternalFilesDirs(null).drop(1).firstOrNull { dir ->
            dir != null && runCatching {
                Environment.isExternalStorageRemovable(dir) && Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED
            }.getOrDefault(false)
        }?.let { File(it, "media").apply { mkdirs() } }

    /** Download/Tapedeck: Android 11 and later let an app put its own files there by path, no permission needed. */
    val downloadsAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun downloadsRoot(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Tapedeck")

    /** Where a new download works: on the SD card when that is chosen (and in), else in the app folder. */
    fun workRoot(context: Context, location: StorageLocation): File =
        if (location == StorageLocation.SD_CARD) sdCardRoot(context) ?: appRoot(context) else appRoot(context)

    /** The folder an entry works in; entries from before there was a choice have theirs in the app folder. */
    fun entryDir(context: Context, entry: Entry): File = entry.folder?.let(::File) ?: File(appRoot(context), entry.id)

    fun deleteFiles(context: Context, entry: Entry) {
        entryDir(context, entry).deleteRecursively()
        entry.mediaFile?.let(::File)?.let { media ->
            // A file in Download/Tapedeck lies outside the entry's folder.
            if (media.exists()) media.delete()
            media.parentFile?.takeIf { it.name == entry.id }?.deleteRecursively()
        }
    }

    fun freeBytes(dir: File): Long = runCatching { StatFs(dir.apply { mkdirs() }.path).availableBytes }.getOrDefault(Long.MAX_VALUE)

    /** Whether the entry's file already lies where [location] puts downloads. */
    fun isIn(context: Context, entry: Entry, location: StorageLocation): Boolean {
        val media = entry.mediaFile?.let(::File) ?: return true
        return when (location) {
            StorageLocation.DOWNLOADS -> media.parentFile == downloadsRoot()
            StorageLocation.APP -> media.parentFile?.parentFile == appRoot(context)
            StorageLocation.SD_CARD -> sdCardRoot(context)?.let { media.parentFile?.parentFile == it } ?: true
        }
    }

    /**
     * Puts a finished file into Download/Tapedeck under its title ("Big Buck Bunny.m4a", and "… (2).m4a" when
     * that name is taken); null if it could not.
     */
    fun publish(media: File, title: String): File? {
        val dir = downloadsRoot().apply { mkdirs() }
        val target = freeName(dir, title, media.extension)
        return target.takeIf { move(media, it) }
    }

    /** Where an entry's files went when moving it to [location] (null: left as it was). */
    data class Moved(val mediaFile: String, val thumbnailFile: String?, val folder: String?)

    /**
     * Moves a finished download to [location]: its file to Download/Tapedeck, or its file and cover into its
     * own folder in the app folder or on the SD card. The cover of a file in Download/Tapedeck stays with
     * the app (file managers have no use for it).
     */
    fun relocate(context: Context, entry: Entry, location: StorageLocation): Moved? {
        if (isIn(context, entry, location)) return null
        val media = entry.mediaFile?.let(::File)?.takeIf { it.exists() } ?: return null
        val cover = entry.thumbnailFile?.let(::File)?.takeIf { it.exists() }
        if (location == StorageLocation.DOWNLOADS) {
            val moved = publish(media, entry.title) ?: return null
            return Moved(moved.path, cover?.path, entry.folder)
        }
        val root = (if (location == StorageLocation.SD_CARD) sdCardRoot(context) else appRoot(context)) ?: return null
        val target = File(root, entry.id).apply { mkdirs() }
        val newMedia = File(target, media.name)
        if (!move(media, newMedia)) return null
        val newCover = cover?.let { old -> File(target, old.name).takeIf { old == it || move(old, it) } ?: old }
        // What is left of the old folder (it only held this entry's files).
        val old = entryDir(context, entry)
        if (old != target && old.name == entry.id) old.deleteRecursively()
        return Moved(newMedia.path, newCover?.path, target.path)
    }

    private fun freeName(dir: File, title: String, extension: String): File {
        val first = File(dir, fileNameFor(title, extension))
        if (!first.exists()) return first
        val base = first.nameWithoutExtension
        return generateSequence(2) { it + 1 }.map { File(dir, "$base ($it).$extension") }.first { !it.exists() }
    }

    /** Renames where it can, copies (and deletes the original) across storage volumes. */
    private fun move(from: File, to: File): Boolean {
        if (from == to) return true
        if (from.renameTo(to)) return true
        return runCatching {
            from.copyTo(to, overwrite = false)
            from.delete()
            true
        }.getOrElse {
            to.delete()
            false
        }
    }

    fun mimeType(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "m4a" -> "audio/mp4"
            "opus" -> "audio/ogg"
            "webm" -> "video/webm"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }

    fun shareIntent(context: Context, entry: Entry): Intent? {
        val file = entry.mediaFile?.let(::File)?.takeIf { it.exists() } ?: return null
        // On disk every download is "media.mp4"; the receiving app gets the title as the file's name
        // ("Big Buck Bunny.mp4"), the way "Save to Downloads" names it.
        val name = fileNameFor(entry.title, file.extension)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file, name)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, entry.title)
            // The share sheet's preview reads the name from here as well.
            clipData = ClipData.newUri(context.contentResolver, name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, entry.title)
    }

    /** Copies the file into Download/Tapedeck, e.g. to put it on a USB stick for the car. */
    fun exportToDownloads(context: Context, entry: Entry): Boolean {
        val file = entry.mediaFile?.let(::File)?.takeIf { it.exists() } ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val name = fileNameFor(entry.title, file.extension)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType(file))
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Tapedeck")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("no stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    fun fileNameFor(title: String, extension: String): String {
        val clean = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().take(120)
        return (clean.ifEmpty { "Tapedeck" }) + "." + extension
    }
}
