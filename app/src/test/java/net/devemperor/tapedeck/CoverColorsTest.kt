package net.devemperor.tapedeck

import net.devemperor.tapedeck.ui.CoverColors
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverColorsTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun channels(argb: Int) = Triple((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    @Test
    fun `finds the vivid colour, not the grey average`() {
        // Mostly grey and black, with a patch of a dull dark red: the answer is red, lifted to be clearly visible.
        val pixels = IntArray(1000) { i ->
            when {
                i < 150 -> rgb(110, 30, 25)
                i < 600 -> rgb(128, 128, 128)
                else -> rgb(5, 5, 5)
            }
        }
        val (r, g, b) = channels(CoverColors.dominant(pixels)!!)
        assertTrue("red leads: $r $g $b", r > g + 60 && r > b + 60)
        assertTrue("bright enough: $r", r >= 160)
    }

    @Test
    fun `gives up on a cover without colour`() {
        assertNull(CoverColors.dominant(IntArray(500) { rgb(it % 255, it % 255, it % 255) }))
    }
}
