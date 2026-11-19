package net.devemperor.tapedeck.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.devemperor.tapedeck.data.Entry
import java.io.File

/**
 * The colour a cover is known by — its most vivid hue, not the muddy average — for the player's
 * background and the deck's cassette. Worked out from a ~48 px copy of the cover and kept per file.
 */
object CoverColors {
    // Lazy, so the pure colour maths below can run in unit tests without Android.
    private val cache by lazy { LruCache<String, Int>(64) }
    private const val NONE = 0

    fun of(file: File): Color? {
        val key = file.absolutePath + ":" + file.lastModified()
        cache.get(key)?.let { return if (it == NONE) null else Color(it) }
        val argb = compute(file)
        cache.put(key, argb ?: NONE)
        return argb?.let(::Color)
    }

    private fun compute(file: File): Int? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 48) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        return dominant(pixels)
    }

    /**
     * Pure, so it is unit-tested: hues in 24 buckets, weighted by how vivid a pixel is; near-black,
     * near-white and grey pixels hardly count. The winning bucket's average colour is the answer.
     */
    fun dominant(pixels: IntArray): Int? {
        val weight = FloatArray(24)
        val r = FloatArray(24)
        val g = FloatArray(24)
        val b = FloatArray(24)
        val hsv = FloatArray(3)
        for (p in pixels) {
            val red = (p shr 16) and 0xFF
            val green = (p shr 8) and 0xFF
            val blue = p and 0xFF
            rgbToHsv(red, green, blue, hsv)
            val saturation = hsv[1]
            val value = hsv[2]
            if (value < 0.15f || saturation < 0.18f) continue
            val w = saturation * saturation * value
            val bucket = ((hsv[0] / 15f).toInt()).coerceIn(0, 23)
            weight[bucket] += w
            r[bucket] += red * w
            g[bucket] += green * w
            b[bucket] += blue * w
        }
        val best = weight.indices.maxByOrNull { weight[it] } ?: return null
        val total = weight[best]
        if (total < pixels.size * 0.01f) return null
        // The cover's hue, lifted into a clear, friendly range: a dark, dull film still gets a colour
        // that reads as one (and a neon one is calmed down a little).
        rgbToHsv((r[best] / total).toInt(), (g[best] / total).toInt(), (b[best] / total).toInt(), hsv)
        return hsvToRgb(hsv[0], hsv[1].coerceIn(0.45f, 0.8f), hsv[2].coerceIn(0.66f, 0.9f))
    }

    private fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int {
        val c = value * saturation
        val x = c * (1 - kotlin.math.abs((hue / 60f) % 2 - 1))
        val m = value - c
        val (r, g, b) = when ((hue / 60f).toInt().coerceIn(0, 5)) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun channel(v: Float) = ((v + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    private fun rgbToHsv(red: Int, green: Int, blue: Int, out: FloatArray) {
        val rf = red / 255f
        val gf = green / 255f
        val bf = blue / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min
        val hue = when {
            delta == 0f -> 0f
            max == rf -> 60f * (((gf - bf) / delta) % 6f)
            max == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }
        out[0] = if (hue < 0) hue + 360f else hue
        out[1] = if (max == 0f) 0f else delta / max
        out[2] = max
    }
}

/** The cover colour of [entry], once it is worked out (null without a cover or without any colour in it). */
@Composable
fun rememberCoverColor(entry: Entry): State<Color?> =
    produceState<Color?>(null, entry.thumbnailFile) {
        value = withContext(Dispatchers.IO) { entry.thumbnailFile?.let(::File)?.takeIf { it.exists() }?.let(CoverColors::of) }
    }
