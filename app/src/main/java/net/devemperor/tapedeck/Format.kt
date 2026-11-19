package net.devemperor.tapedeck

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import net.devemperor.tapedeck.engine.LiveProgress
import net.devemperor.tapedeck.engine.Phase
import kotlin.math.roundToInt

object Format {
    fun bytes(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    /** 4:05, 1:02:05 */
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** "vor 11 Min." / "11 min. ago", lower-case so it reads inside a sentence. */
    fun relative(time: Long): String =
        DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
            .toString().replaceFirstChar { it.lowercase() }

    fun progress(context: Context, progress: LiveProgress?): String = when (progress?.phase) {
        null, Phase.STARTING -> context.getString(R.string.progress_starting)
        Phase.PREPARING -> context.getString(R.string.progress_preparing)
        Phase.PROCESSING -> context.getString(R.string.progress_processing)
        Phase.DOWNLOADING -> buildList {
            progress.fraction?.let { add("${(it * 100).roundToInt()} %") }
            progress.speedBytesPerSec?.takeIf { it > 0 }?.let { add(context.getString(R.string.progress_speed, bytes(context, it))) }
            progress.etaSec?.takeIf { it > 0 }?.let { add(context.getString(R.string.progress_eta, clock(it * 1000))) }
        }.joinToString(" · ").ifEmpty { context.getString(R.string.progress_downloading) }
    }
}
