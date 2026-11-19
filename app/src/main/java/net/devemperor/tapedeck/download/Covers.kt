package net.devemperor.tapedeck.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlin.math.max

object Covers {
    /**
     * Many videos only have YouTube's 4:3 thumbnails, which carry the 16:9 picture between black bars. The
     * list crops them away by itself, but the player's square cover showed them, so the bars are cut off the
     * file once after the download. Only rows that are black all the way across count, so a dark scene
     * stays whole, and at most a third is taken from each side.
     */
    fun trimLetterbox(file: File) {
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return
        val width = bitmap.width
        val height = bitmap.height
        val step = max(1, width / 64)
        fun black(y: Int): Boolean {
            var x = 0
            while (x < width) {
                val c = bitmap.getPixel(x, y)
                if (max(Color.red(c), max(Color.green(c), Color.blue(c))) > BLACK_LEVEL) return false
                x += step
            }
            return true
        }
        var top = 0
        while (top < height / 3 && black(top)) top++
        var bottom = height - 1
        while (bottom > height * 2 / 3 && black(bottom)) bottom--
        val cut = top + (height - 1 - bottom)
        if (cut < height * MIN_CUT) {
            bitmap.recycle()
            return
        }
        val cropped = Bitmap.createBitmap(bitmap, 0, top, width, bottom - top + 1)
        file.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        cropped.recycle()
        bitmap.recycle()
    }

    private const val BLACK_LEVEL = 24
    private const val MIN_CUT = 0.06
}
