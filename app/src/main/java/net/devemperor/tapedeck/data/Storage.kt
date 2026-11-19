package net.devemperor.tapedeck.data

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
 * Downloads live in the app's own folder on the shared storage (Android/data/…/files/media/<entry>), so
 * they need no storage permission and can still be copied off over USB.
 */
object Storage {
    fun mediaRoot(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "media").apply { mkdirs() }
    }

    fun entryDir(context: Context, id: String) = File(mediaRoot(context), id)

    fun deleteFiles(context: Context, entry: Entry) {
        entryDir(context, entry.id).deleteRecursively()
        entry.mediaFile?.let { File(it).parentFile?.takeIf { dir -> dir.name == entry.id }?.deleteRecursively() }
    }

    fun usedBytes(context: Context): Long =
        mediaRoot(context).walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun freeBytes(context: Context): Long = runCatching { StatFs(mediaRoot(context).path).availableBytes }.getOrDefault(Long.MAX_VALUE)

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
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, entry.title)
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
