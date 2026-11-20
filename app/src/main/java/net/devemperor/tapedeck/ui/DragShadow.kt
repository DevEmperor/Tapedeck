package net.devemperor.tapedeck.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import net.devemperor.tapedeck.data.Entry
import java.io.File

/** The theme's colours for the lifted card, as plain ARGB values for the platform canvas. */
data class ShadowColors(val surface: Int, val accent: Int, val text: Int)

/**
 * What hangs under the finger while a download is dragged: a compact card with its picture and title —
 * opaque, outlined in the accent colour and tilted a little, as if lifted off the list. The finger holds
 * it on its lower middle, so it sits right above the fingertip instead of off to the side where the
 * wide list card happened to be pressed.
 */
class EntryDragShadow(context: Context, entry: Entry, colors: ShadowColors) : View.DragShadowBuilder() {
    private val bitmap: Bitmap
    private val touch: Point

    init {
        val metrics = context.resources.displayMetrics
        fun dp(value: Float) = value * metrics.density
        val cardWidth = dp(264f)
        val cardHeight = dp(76f)
        // Room around the card for its shadow and the tilt.
        val margin = dp(18f)
        val width = (cardWidth + 2 * margin).toInt()
        val height = (cardHeight + 2 * margin).toInt()
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.rotate(TILT_DEGREES, width / 2f, height / 2f)

        val card = RectF(margin, margin, margin + cardWidth, margin + cardHeight)
        val radius = dp(16f)
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.surface
            setShadowLayer(dp(12f), 0f, dp(5f), 0x73000000)
        }
        canvas.drawRoundRect(card, radius, radius, body)

        // The picture on the left, cropped to 16:9 like in the list.
        val inset = dp(8f)
        val pictureHeight = cardHeight - 2 * inset
        val picture = RectF(card.left + inset, card.top + inset, card.left + inset + pictureHeight * 16f / 9f, card.bottom - inset)
        val pictureRadius = dp(10f)
        val thumbnail = entry.thumbnailFile?.let(::File)?.takeIf { it.exists() }?.let { decode(it, picture.width().toInt()) }
        if (thumbnail != null) {
            val crop = centerCrop(thumbnail.width, thumbnail.height, picture.width() / picture.height())
            val matrix = Matrix().apply { setRectToRect(RectF(crop), picture, Matrix.ScaleToFit.FILL) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                shader = BitmapShader(thumbnail, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
            }
            canvas.drawRoundRect(picture, pictureRadius, pictureRadius, paint)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.accent; alpha = 60 }
            canvas.drawRoundRect(picture, pictureRadius, pictureRadius, paint)
        }

        // The title beside it, in up to two lines.
        val textLeft = picture.right + dp(10f)
        val textWidth = (card.right - dp(12f) - textLeft).toInt().coerceAtLeast(1)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.text
            textSize = 14f * metrics.density * context.resources.configuration.fontScale
            typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, 500, false) else Typeface.DEFAULT_BOLD
        }
        val title = StaticLayout.Builder.obtain(entry.title, 0, entry.title.length, textPaint, textWidth)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(textLeft, card.centerY() - title.height / 2f)
        title.draw(canvas)
        canvas.restore()

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
            color = colors.accent
        }
        val half = ring.strokeWidth / 2
        canvas.drawRoundRect(RectF(card.left + half, card.top + half, card.right - half, card.bottom - half), radius - half, radius - half, ring)

        touch = Point(width / 2, (margin + cardHeight * 0.68f).toInt())
    }

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(bitmap.width, bitmap.height)
        outShadowTouchPoint.set(touch.x, touch.y)
    }

    override fun onDrawShadow(canvas: Canvas) {
        canvas.drawBitmap(bitmap, 0f, 0f, null)
    }

    private companion object {
        const val TILT_DEGREES = -2.5f

        /** Decodes the cover no larger than needed for [width] pixels. */
        fun decode(file: File, width: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= width) sample *= 2
            return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }

        /** The middle part of a [width] × [height] picture that has the [aspect] of the frame it goes in. */
        fun centerCrop(width: Int, height: Int, aspect: Float): Rect =
            if (width.toFloat() / height > aspect) {
                val cropWidth = (height * aspect).toInt()
                val left = (width - cropWidth) / 2
                Rect(left, 0, left + cropWidth, height)
            } else {
                val cropHeight = (width / aspect).toInt()
                val top = (height - cropHeight) / 2
                Rect(0, top, width, top + cropHeight)
            }
    }
}
